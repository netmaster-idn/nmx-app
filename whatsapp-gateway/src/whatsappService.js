const path = require("path");
const fs = require("fs");
const QRCode = require("qrcode");
const { Client, LocalAuth, MessageMedia } = require("whatsapp-web.js");
const { SessionStore } = require("./sessionStore");
const { STATUS } = require("./status");

function detectBrowserExecutable(configuredPath) {
  const explicitPath = configuredPath ? String(configuredPath).trim() : "";
  if (explicitPath) {
    const resolvedPath = path.resolve(explicitPath);
    if (fs.existsSync(resolvedPath)) {
      return resolvedPath;
    }
  }

  const platform = process.platform;
  const candidates = [];

  if (platform === "win32") {
    candidates.push(
      "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
      "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
      "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
      "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
      "C:\\Program Files\\Chromium\\Application\\chrome.exe",
      "C:\\Program Files (x86)\\Chromium\\Application\\chrome.exe"
    );
  } else if (platform === "darwin") {
    candidates.push(
      "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
      "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
      "/Applications/Chromium.app/Contents/MacOS/Chromium"
    );
  } else {
    candidates.push(
      "/usr/bin/google-chrome",
      "/usr/bin/google-chrome-stable",
      "/usr/bin/chromium",
      "/usr/bin/chromium-browser",
      "/snap/bin/chromium"
    );
  }

  return candidates.find((candidate) => fs.existsSync(candidate)) || "";
}

class WhatsappService {
  constructor(options) {
    this.sessionStore = new SessionStore({
      sessionId: options.sessionId,
      stateFile: options.stateFile,
      sessionRoot: options.sessionRoot
    });
    this.chromeExecutablePath = detectBrowserExecutable(options.chromeExecutablePath || "");
    this.webClientTimeoutMs = Number(options.webClientTimeoutMs || 0);
    this.client = null;
    this.initializingPromise = null;
    this.reconnectTimer = null;
    this.allowReconnect = true;
  }

  getStatus() {
    return this.sessionStore.getState();
  }

  getQrCode() {
    return this.getStatus();
  }

  async sendMessage(payload) {
    await this.ensureClientReady();

    const message = payload && payload.message ? String(payload.message).trim() : "";
    if (!message) {
      throw new Error("Nomor tujuan dan isi pesan wajib diisi");
    }

    const chatId = this.resolveChatId(payload);
    const response = await this.client.sendMessage(chatId, message);
    return {
      chatId,
      message,
      messageId: response && response.id ? response.id._serialized : null
    };
  }

  async sendDocument(to, document) {
    await this.ensureClientReady();
    if (!to) {
      throw new Error("Nomor tujuan wajib diisi");
    }
    if (!document || !document.base64Data || !document.mimeType || !document.fileName) {
      throw new Error("Dokumen PDF wajib diisi lengkap");
    }

    const chatId = this.normalizeChatId(to);
    const media = new MessageMedia(document.mimeType, document.base64Data, document.fileName);
    const response = await this.client.sendMessage(chatId, media, {
      sendMediaAsDocument: true,
      caption: document.caption || ""
    });

    return {
      chatId,
      fileName: document.fileName,
      messageId: response && response.id ? response.id._serialized : null
    };
  }

  async getChats(options = {}) {
    await this.ensureClientReady();

    const limit = this.sanitizeLimit(options.limit, 30, 100);
    const search = options.search ? String(options.search).trim().toLowerCase() : "";
    const chats = await this.client.getChats();
    const filtered = chats
      .map((chat) => this.mapChat(chat))
      .filter((chat) => {
        if (!search) {
          return true;
        }
        const haystacks = [chat.name, chat.shortName, chat.chatId, chat.lastMessagePreview];
        return haystacks.some((value) => value && String(value).toLowerCase().includes(search));
      })
      .sort((left, right) => (right.timestamp || 0) - (left.timestamp || 0))
      .slice(0, limit);

    return {
      sessionId: this.getStatus().sessionId,
      total: filtered.length,
      chats: filtered
    };
  }

  async getMessages(chatId, options = {}) {
    await this.ensureClientReady();

    const normalizedChatId = this.assertChatId(chatId);
    const limit = this.sanitizeLimit(options.limit, 40, 100);
    const chat = await this.client.getChatById(normalizedChatId);
    if (!chat) {
      throw new Error("Chat WhatsApp tidak ditemukan");
    }

    const messages = await this.safeFetchMessages(chat, limit);
    return {
      sessionId: this.getStatus().sessionId,
      chat: this.mapChat(chat),
      messages: messages
        .map((message) => this.mapMessage(message))
        .sort((left, right) => (left.timestamp || 0) - (right.timestamp || 0))
    };
  }

  async safeFetchMessages(chat, limit) {
    try {
      return await chat.fetchMessages({ limit });
    } catch (error) {
      const message = error && error.message ? String(error.message) : "";
      const normalized = message.toLowerCase();
      const recoverable =
        normalized.includes("waitforchatloading") ||
        normalized.includes("cannot read properties of undefined");
      if (!recoverable) {
        throw error;
      }

      const chatId = chat && chat.id && chat.id._serialized ? chat.id._serialized : "unknown";
      console.warn("[whatsapp] fetchMessages fallback", {
        chatId,
        reason: message
      });
      return [];
    }
  }

  async getMessageStatus(messageId) {
    await this.ensureClientReady();

    const normalizedMessageId = String(messageId || "").trim();
    if (!normalizedMessageId) {
      throw new Error("Message WhatsApp tidak valid");
    }

    const message = await this.client.getMessageById(normalizedMessageId);
    if (!message) {
      throw new Error("Pesan WhatsApp tidak ditemukan");
    }

    return this.mapMessage(message);
  }

  isReady() {
    return Boolean(this.client) && this.getStatus().status === STATUS.CONNECTED;
  }

  async ensureClientReady() {
    if (this.isReady()) {
      return;
    }

    await this.initClient(false);
    if (!this.isReady()) {
      throw new Error("WhatsApp belum connected");
    }
  }

  async initClient(forceRestart = false) {
    if (this.client && this.isReady() && !forceRestart) {
      return this.getStatus();
    }

    if (this.initializingPromise && !forceRestart) {
      return this.initializingPromise;
    }

    this.allowReconnect = true;
    this.clearReconnectTimer();

    if (forceRestart || this.client) {
      await this.destroyClient(false);
    }

    this.sessionStore.update({
      status: STATUS.INITIALIZING,
      isReady: false,
      lastError: null
    });

    this.client = this.buildClient();
    this.attachEventHandlers(this.client);

    this.initializingPromise = this.client.initialize()
      .then(() => this.getStatus())
      .catch((error) => {
        this.handleError("initialize", error);
        throw error;
      })
      .finally(() => {
        this.initializingPromise = null;
      });

    return this.initializingPromise;
  }

  async resetSession() {
    this.allowReconnect = false;
    this.clearReconnectTimer();
    await this.logout();
    this.sessionStore.clearPersistentSession();
    this.sessionStore.save({
      status: STATUS.INACTIVE,
      label: "Nonaktif",
      isReady: false,
      hasQr: false,
      qrCode: null,
      sessionId: this.getStatus().sessionId,
      lastConnectedAt: null,
      lastDisconnectedAt: null,
      lastError: null,
      qrUpdatedAt: null
    });
    return this.getStatus();
  }

  async logout() {
    this.allowReconnect = false;
    this.clearReconnectTimer();
    try {
      if (this.client) {
        try {
          await this.client.logout();
        } catch (error) {
          console.warn("[whatsapp] logout warning:", error.message);
        }
      }
    } finally {
      await this.destroyClient(true);
    }

    this.sessionStore.update({
      status: STATUS.DISCONNECTED,
      isReady: false,
      hasQr: false,
      qrCode: null,
      qrUpdatedAt: null,
      lastDisconnectedAt: new Date().toISOString()
    });
    return this.getStatus();
  }

  async refreshQr() {
    this.allowReconnect = true;
    await this.initClient(true);
    return this.getStatus();
  }

  handleRuntimeFailure(error, stage = "runtime") {
    this.handleError(stage, error);
    if (this.allowReconnect) {
      this.scheduleReconnect();
    }
  }

  buildClient() {
    const puppeteer = {
      headless: true,
      args: [
        "--no-sandbox",
        "--disable-setuid-sandbox",
        "--disable-dev-shm-usage"
      ]
    };

    if (this.chromeExecutablePath) {
      puppeteer.executablePath = this.chromeExecutablePath;
    }
    if (this.webClientTimeoutMs > 0) {
      puppeteer.timeout = this.webClientTimeoutMs;
    }

    return new Client({
      authStrategy: new LocalAuth({
        clientId: this.getStatus().sessionId,
        dataPath: this.sessionStore.sessionRoot
      }),
      puppeteer
    });
  }

  attachEventHandlers(client) {
    client.on("qr", async (qr) => {
      const qrCode = await QRCode.toDataURL(qr, { margin: 1, width: 280 });
      const state = this.sessionStore.update({
        status: STATUS.QR_REQUIRED,
        isReady: false,
        hasQr: true,
        qrCode,
        qrUpdatedAt: new Date().toISOString(),
        lastError: null
      });
      console.info("[whatsapp] qr generated", {
        status: state.status,
        sessionId: state.sessionId
      });
    });

    client.on("authenticated", () => {
      const state = this.sessionStore.update({
        status: STATUS.AUTHENTICATED,
        isReady: false,
        hasQr: false,
        qrCode: null,
        qrUpdatedAt: null,
        lastError: null
      });
      console.info("[whatsapp] authenticated", {
        status: state.status,
        sessionId: state.sessionId
      });
    });

    client.on("ready", () => {
      const state = this.sessionStore.update({
        status: STATUS.CONNECTED,
        isReady: true,
        hasQr: false,
        qrCode: null,
        qrUpdatedAt: null,
        lastConnectedAt: new Date().toISOString(),
        lastError: null
      });
      console.info("[whatsapp] ready", {
        status: state.status,
        sessionId: state.sessionId
      });
    });

    client.on("auth_failure", (message) => {
      const state = this.sessionStore.update({
        status: STATUS.AUTH_FAILURE,
        isReady: false,
        hasQr: false,
        qrCode: null,
        qrUpdatedAt: null,
        lastDisconnectedAt: new Date().toISOString(),
        lastError: message || "Autentikasi WhatsApp gagal"
      });
      console.error("[whatsapp] auth_failure", {
        status: state.status,
        message: state.lastError
      });
    });

    client.on("disconnected", (reason) => {
      const state = this.sessionStore.update({
        status: STATUS.DISCONNECTED,
        isReady: false,
        hasQr: false,
        qrCode: null,
        qrUpdatedAt: null,
        lastDisconnectedAt: new Date().toISOString(),
        lastError: reason ? String(reason) : null
      });
      console.warn("[whatsapp] disconnected", {
        status: state.status,
        reason: state.lastError
      });
      if (this.allowReconnect) {
        this.scheduleReconnect();
      }
    });
  }

  scheduleReconnect() {
    this.clearReconnectTimer();
    this.reconnectTimer = setTimeout(() => {
      this.initClient(true).catch((error) => {
        this.handleError("reconnect", error);
      });
    }, 3000);
  }

  clearReconnectTimer() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  normalizeChatId(phone) {
    const digits = String(phone || "").replace(/[^0-9]/g, "");
    if (!digits) {
      throw new Error("Nomor WhatsApp tidak valid");
    }
    const normalized = digits.startsWith("62")
      ? digits
      : digits.startsWith("0")
        ? "62" + digits.slice(1)
        : "62" + digits;
    return normalized + "@c.us";
  }

  resolveChatId(payload) {
    if (payload && payload.chatId) {
      return this.assertChatId(payload.chatId);
    }
    if (payload && payload.to) {
      return this.normalizeChatId(payload.to);
    }
    throw new Error("Nomor tujuan dan isi pesan wajib diisi");
  }

  assertChatId(chatId) {
    const normalized = decodeURIComponent(String(chatId || "").trim());
    if (!normalized || !normalized.includes("@")) {
      throw new Error("Chat WhatsApp tidak valid");
    }
    return normalized;
  }

  sanitizeLimit(value, fallback, max) {
    const parsed = Number(value || fallback);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      return fallback;
    }
    return Math.min(Math.trunc(parsed), max);
  }

  mapChat(chat) {
    const timestamp = Number(chat.timestamp || (chat.lastMessage && chat.lastMessage._data ? chat.lastMessage._data.t : 0) || 0);
    const id = chat.id && chat.id._serialized ? chat.id._serialized : null;
    const lastBody = chat.lastMessage && typeof chat.lastMessage.body === "string"
      ? chat.lastMessage.body
      : "";
    return {
      chatId: id,
      name: chat.name || chat.formattedTitle || chat.formattedName || id,
      shortName: chat.shortName || "",
      timestamp,
      unreadCount: Number(chat.unreadCount || 0),
      isGroup: Boolean(chat.isGroup),
      isMuted: Boolean(chat.isMuted),
      isArchived: Boolean(chat.archived),
      isPinned: Boolean(chat.pinned),
      lastMessagePreview: lastBody,
      lastMessageAt: timestamp ? new Date(timestamp * 1000).toISOString() : null
    };
  }

  mapMessage(message) {
    const timestamp = Number(message.timestamp || 0);
    const body = typeof message.body === "string" ? message.body : "";
    const ack = typeof message.ack === "number" ? message.ack : null;
    return {
      messageId: message.id && message.id._serialized ? message.id._serialized : null,
      body,
      type: message.type || "chat",
      fromMe: Boolean(message.fromMe),
      ack,
      timestamp,
      sentAt: timestamp ? new Date(timestamp * 1000).toISOString() : null,
      from: message.from || null,
      to: message.to || null,
      author: message.author || null,
      hasMedia: Boolean(message.hasMedia)
    };
  }

  async destroyClient(markDisconnected) {
    this.clearReconnectTimer();
    if (!this.client) {
      return;
    }

    const currentClient = this.client;
    this.client = null;

    try {
      await currentClient.destroy();
    } catch (error) {
      console.warn("[whatsapp] destroy warning:", error.message);
    }

    if (markDisconnected) {
      this.sessionStore.update({
        status: STATUS.DISCONNECTED,
        isReady: false,
        hasQr: false,
        qrCode: null,
        qrUpdatedAt: null,
        lastDisconnectedAt: new Date().toISOString()
      });
    }
  }

  handleError(stage, error) {
    const message = this.normalizeErrorMessage(error && error.message ? error.message : "Unknown WhatsApp error");
    const state = this.sessionStore.update({
      status: STATUS.ERROR,
      isReady: false,
      hasQr: false,
      qrCode: null,
      qrUpdatedAt: null,
      lastDisconnectedAt: new Date().toISOString(),
      lastError: "[" + stage + "] " + message
    });
    console.error("[whatsapp] error", {
      stage,
      status: state.status,
      message: state.lastError
    });
  }

  normalizeErrorMessage(message) {
    const text = String(message || "").trim();
    if (!text) {
      return "Unknown WhatsApp error";
    }

    if (text.includes("Could not find Chrome")) {
      if (this.chromeExecutablePath) {
        return "Chrome/Chromium tidak bisa dipakai di path " + this.chromeExecutablePath + ". Periksa instalasi browser atau ubah WHATSAPP_CHROME_EXECUTABLE_PATH.";
      }
      return "Chrome/Chromium tidak ditemukan. Install Google Chrome atau Microsoft Edge, atau atur WHATSAPP_CHROME_EXECUTABLE_PATH.";
    }

    return text;
  }
}

module.exports = {
  WhatsappService
};

