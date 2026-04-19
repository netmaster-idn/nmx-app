require("dotenv").config();

const express = require("express");
const path = require("path");
const { WhatsappService } = require("./whatsappService");

const port = Number(process.env.PORT || 3001);
const defaultSessionId = process.env.WHATSAPP_SESSION_ID || "default";
const sessionRoot = process.env.WHATSAPP_SESSION_ROOT || path.join(__dirname, "..", "storage", "sessions");
const configuredStateFile = process.env.WHATSAPP_STATE_FILE || path.join(__dirname, "..", "storage", "runtime", "whatsapp-state.json");
const chromeExecutablePath = process.env.WHATSAPP_CHROME_EXECUTABLE_PATH || "";
const webClientTimeoutMs = process.env.WHATSAPP_WEB_CLIENT_TIMEOUT_MS || 0;

const serviceRegistry = new Map();

const app = express();
app.use(express.json());

process.on("unhandledRejection", (reason) => {
  for (const service of serviceRegistry.values()) {
    service.handleRuntimeFailure(reason instanceof Error ? reason : new Error(String(reason)), "unhandledRejection");
  }
});

process.on("uncaughtException", (error) => {
  for (const service of serviceRegistry.values()) {
    service.handleRuntimeFailure(error, "uncaughtException");
  }
});

function success(res, message, data) {
  return res.json({
    success: true,
    message,
    data
  });
}

function failure(res, status, message, error, service) {
  if (error) {
    console.error("[whatsapp-api]", message, error.message);
  }
  return res.status(status).json({
    success: false,
    message,
    data: service ? service.getStatus() : null
  });
}

function sanitizeSessionId(rawValue) {
  const candidate = String(rawValue || "").trim();
  if (!candidate) {
    return defaultSessionId;
  }

  const normalized = candidate
    .replace(/[^A-Za-z0-9_-]+/g, "-")
    .replace(/-{2,}/g, "-")
    .replace(/^-+|-+$/g, "");

  return normalized || defaultSessionId;
}

function resolveSessionId(req) {
  const headerSessionId = req.get("X-Whatsapp-Session-Id");
  if (headerSessionId) {
    return sanitizeSessionId(headerSessionId);
  }

  const querySessionId = typeof req.query.sessionId === "string" ? req.query.sessionId : "";
  if (querySessionId) {
    return sanitizeSessionId(querySessionId);
  }

  const bodySessionId = req.body && typeof req.body.sessionId === "string" ? req.body.sessionId : "";
  if (bodySessionId) {
    return sanitizeSessionId(bodySessionId);
  }

  return defaultSessionId;
}

function buildStateFile(sessionId) {
  if (sessionId === defaultSessionId) {
    return configuredStateFile;
  }

  const runtimeDir = path.dirname(configuredStateFile);
  const extension = path.extname(configuredStateFile) || ".json";
  const baseName = path.basename(configuredStateFile, extension);
  return path.join(runtimeDir, `${baseName}-${sessionId}${extension}`);
}

function getWhatsappService(sessionId) {
  const normalizedSessionId = sanitizeSessionId(sessionId);
  if (!serviceRegistry.has(normalizedSessionId)) {
    serviceRegistry.set(normalizedSessionId, new WhatsappService({
      sessionId: normalizedSessionId,
      sessionRoot,
      stateFile: buildStateFile(normalizedSessionId),
      chromeExecutablePath,
      webClientTimeoutMs
    }));
  }
  return serviceRegistry.get(normalizedSessionId);
}

function serviceForRequest(req) {
  return getWhatsappService(resolveSessionId(req));
}

app.get("/health", (req, res) => {
  const sessionId = resolveSessionId(req);
  const whatsappService = getWhatsappService(sessionId);
  success(res, "WhatsApp gateway aktif", {
    service: "nmx-whatsapp-gateway",
    sessionId,
    status: whatsappService.getStatus().status,
    activeSessions: serviceRegistry.size
  });
});

app.get("/api/whatsapp/status", (req, res) => {
  const whatsappService = serviceForRequest(req);
  success(res, "Status WhatsApp berhasil diambil", whatsappService.getStatus());
});

app.post("/api/whatsapp/init", async (req, res) => {
  const whatsappService = serviceForRequest(req);
  try {
    await whatsappService.initClient(false);
    success(res, "Inisialisasi WhatsApp berhasil dijalankan", whatsappService.getStatus());
  } catch (error) {
    failure(res, 500, "Gagal menginisialisasi WhatsApp", error, whatsappService);
  }
});

app.get("/api/whatsapp/qr", (req, res) => {
  const whatsappService = serviceForRequest(req);
  success(res, "QR WhatsApp berhasil diambil", whatsappService.getQrCode());
});

app.post("/api/whatsapp/qr/regenerate", async (req, res) => {
  const whatsappService = serviceForRequest(req);
  try {
    await whatsappService.refreshQr();
    success(res, "QR WhatsApp berhasil diperbarui", whatsappService.getStatus());
  } catch (error) {
    failure(res, 500, "Gagal memperbarui QR WhatsApp", error, whatsappService);
  }
});

app.post("/api/whatsapp/logout", async (req, res) => {
  const whatsappService = serviceForRequest(req);
  try {
    await whatsappService.logout();
    success(res, "Session WhatsApp berhasil diputuskan", whatsappService.getStatus());
  } catch (error) {
    failure(res, 500, "Gagal memutuskan session WhatsApp", error, whatsappService);
  }
});

app.post("/api/whatsapp/reset-session", async (req, res) => {
  const whatsappService = serviceForRequest(req);
  try {
    await whatsappService.resetSession();
    success(res, "Session WhatsApp berhasil direset", whatsappService.getStatus());
  } catch (error) {
    failure(res, 500, "Gagal mereset session WhatsApp", error, whatsappService);
  }
});

app.get("/api/whatsapp/chats", async (req, res) => {
  const whatsappService = serviceForRequest(req);
  try {
    const limit = Number(req.query.limit || 30);
    const search = typeof req.query.search === "string" ? req.query.search : "";
    const data = await whatsappService.getChats({ limit, search });
    success(res, "Daftar chat WhatsApp berhasil diambil", data);
  } catch (error) {
    failure(res, 500, error.message || "Gagal mengambil daftar chat WhatsApp", error, whatsappService);
  }
});

app.get("/api/whatsapp/chats/:chatId/messages", async (req, res) => {
  const whatsappService = serviceForRequest(req);
  try {
    const limit = Number(req.query.limit || 40);
    const data = await whatsappService.getMessages(req.params.chatId, { limit });
    success(res, "Riwayat chat WhatsApp berhasil diambil", data);
  } catch (error) {
    failure(res, 500, error.message || "Gagal mengambil riwayat chat WhatsApp", error, whatsappService);
  }
});

app.get("/api/whatsapp/messages/:messageId/status", async (req, res) => {
  const whatsappService = serviceForRequest(req);
  try {
    const data = await whatsappService.getMessageStatus(req.params.messageId);
    success(res, "Status pesan WhatsApp berhasil diambil", data);
  } catch (error) {
    failure(res, 500, error.message || "Gagal mengambil status pesan WhatsApp", error, whatsappService);
  }
});

app.post("/api/whatsapp/messages/send", async (req, res) => {
  const whatsappService = serviceForRequest(req);
  try {
    const payload = req.body || {};
    const data = await whatsappService.sendMessage({
      to: payload.to,
      chatId: payload.chatId,
      message: payload.message
    });
    success(res, "Pesan WhatsApp berhasil dikirim", data);
  } catch (error) {
    failure(res, 500, error.message || "Gagal mengirim pesan WhatsApp", error, whatsappService);
  }
});

app.post("/api/whatsapp/messages/send-document", async (req, res) => {
  const whatsappService = serviceForRequest(req);
  try {
    const payload = req.body || {};
    const data = await whatsappService.sendDocument(payload.to, {
      mimeType: payload.mimeType,
      fileName: payload.fileName,
      base64Data: payload.base64Data,
      caption: payload.caption
    });
    success(res, "Dokumen WhatsApp berhasil dikirim", data);
  } catch (error) {
    failure(res, 500, error.message || "Gagal mengirim dokumen WhatsApp", error, whatsappService);
  }
});

app.listen(port, () => {
  console.info("[whatsapp-api] listening", {
    port,
    defaultSessionId,
    sessionRoot,
    configuredStateFile
  });
});

