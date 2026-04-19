(function () {
    if (window.__nmxWhatsappPanelLoaded) {
        return;
    }
    window.__nmxWhatsappPanelLoaded = true;

    const settingsModalId = "whatsappSettingsModal";
    const composerModalId = "whatsappComposerModal";
    const pollOpenMs = 4000;
    const pollIdleMs = 30000;

    const statusMeta = {
        inactive: { label: "Nonaktif", tone: "muted", description: "Gateway belum diinisialisasi." },
        initializing: { label: "Connecting", tone: "info", description: "Client WhatsApp sedang memulai koneksi." },
        qr_required: { label: "QR Required", tone: "warning", description: "Scan QR dengan WhatsApp di ponsel Anda." },
        authenticated: { label: "Authenticated", tone: "info", description: "QR berhasil discan, menunggu client siap." },
        connected: { label: "Connected", tone: "success", description: "Session aktif dan siap dipakai." },
        disconnected: { label: "Disconnected", tone: "danger", description: "Session terputus. Anda bisa hubungkan ulang." },
        auth_failure: { label: "Auth Failure", tone: "danger", description: "Autentikasi gagal. Reset session lalu scan ulang." },
        error: { label: "Error", tone: "danger", description: "Terjadi error pada gateway WhatsApp." }
    };

    let state = null;
    let bootstrapState = null;
    let reminderSettings = null;
    let pollTimer = null;
    let pendingAction = false;
    let chatSearchTimer = null;
    let composerState = {
        allChats: [],
        chats: [],
        activeFilter: "all",
        activeChatId: "",
        activeChat: null,
        messages: []
    };

    function requestHeaders(includeJson) {
        const headers = {};
        if (includeJson) {
            headers["Content-Type"] = "application/json";
        }
        headers[window.csrfHeader || "X-CSRF-TOKEN"] = window.csrfToken || "";
        return headers;
    }

    function getStatusMeta(status) {
        return statusMeta[status] || statusMeta.error;
    }

    function getModal(id) {
        return document.getElementById(id);
    }

    function showAlert(type, message) {
        if (window.nmxNotify) {
            window.nmxNotify({ type: type, message: message });
            return;
        }
        window.alert(message || "Terjadi kesalahan.");
    }

    function formatDateTime(value) {
        if (!value) {
            return "-";
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return "-";
        }
        return new Intl.DateTimeFormat("id-ID", {
            dateStyle: "medium",
            timeStyle: "short"
        }).format(date);
    }

    function formatShortTime(value) {
        if (!value) {
            return "";
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return "";
        }
        return new Intl.DateTimeFormat("id-ID", {
            hour: "2-digit",
            minute: "2-digit"
        }).format(date);
    }

    function formatRelativeLabel(value) {
        if (!value) {
            return "Now";
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return "Now";
        }
        const now = new Date();
        const sameDay = date.toDateString() === now.toDateString();
        if (sameDay) {
            return formatShortTime(date);
        }
        const yesterday = new Date(now);
        yesterday.setDate(now.getDate() - 1);
        if (date.toDateString() === yesterday.toDateString()) {
            return "Yesterday";
        }
        return new Intl.DateTimeFormat("id-ID", {
            day: "2-digit",
            month: "short"
        }).format(date);
    }

    function toInitials(name) {
        const clean = String(name || "").trim();
        if (!clean) {
            return "C";
        }
        const parts = clean.split(/\s+/).slice(0, 2);
        return parts.map(function (part) { return part.charAt(0).toUpperCase(); }).join("");
    }

    function normalizeChatId(chat) {
        return String((chat && chat.chatId) || "");
    }

    function parseDateValue(value) {
        if (!value) {
            return null;
        }
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? null : date;
    }

    function getChatPreview(chat) {
        if (!chat || typeof chat !== "object") {
            return "Belum ada pesan terbaru.";
        }
        const preview = chat.lastMessage
            || chat.lastMessageBody
            || chat.lastMessageText
            || chat.preview
            || chat.lastBody
            || "";
        if (!preview) {
            return "Belum ada pesan terbaru.";
        }
        return String(preview);
    }

    function deriveChatTone(chat) {
        const preview = getChatPreview(chat).toLowerCase();
        const name = String((chat && chat.name) || "").toLowerCase();
        if (name.includes("vip") || preview.includes("vip")) {
            return "VIP";
        }
        if (preview.includes("gangguan") || preview.includes("komplain") || preview.includes("lambat")) {
            return "COMPLAINT";
        }
        if (Number(chat && chat.unreadCount || 0) > 0) {
            return "NEW";
        }
        return "ACTIVE";
    }

    function getChatTimestamp(chat) {
        return chat && (chat.lastMessageAt || chat.updatedAt || chat.timestamp || chat.lastActivityAt || chat.lastSeenAt);
    }

    function isChatOnline(chat) {
        const stamp = parseDateValue(getChatTimestamp(chat));
        if (!stamp) {
            return false;
        }
        return (Date.now() - stamp.getTime()) <= (15 * 60 * 1000);
    }

    function applyChatFilter(chats, filterKey) {
        const key = String(filterKey || "all").toLowerCase();
        if (key === "all") {
            return chats.slice();
        }
        return chats.filter(function (chat) {
            const tone = deriveChatTone(chat).toLowerCase();
            const online = isChatOnline(chat);
            const unread = Number(chat && chat.unreadCount || 0) > 0;
            if (key === "active") {
                return online || tone === "active";
            }
            if (key === "waiting") {
                return unread || tone === "new" || tone === "complaint";
            }
            if (key === "resolved") {
                return !unread && tone !== "complaint";
            }
            if (key === "vip") {
                return tone === "vip";
            }
            return true;
        });
    }

    function syncFilterChips() {
        const chips = document.querySelectorAll("#waComposerFilterChips [data-wa-filter]");
        chips.forEach(function (chip) {
            const filterKey = String(chip.getAttribute("data-wa-filter") || "all").toLowerCase();
            chip.classList.toggle("is-active", filterKey === composerState.activeFilter);
        });
    }

    function isAnyModalOpen() {
        return Boolean(document.querySelector(".modal.open"));
    }

    function isModalOpen(id) {
        const modal = getModal(id);
        return Boolean(modal && modal.classList.contains("open"));
    }

    function openModal(id) {
        const modal = getModal(id);
        if (!modal) {
            return;
        }
        modal.classList.add("open");
        document.body.style.overflow = "hidden";
    }

    function closeModal(id) {
        const modal = getModal(id);
        if (!modal) {
            return;
        }
        modal.classList.remove("open");
        document.body.style.overflow = isAnyModalOpen() ? "hidden" : "";
    }

    function setActionLoading(nextState) {
        pendingAction = Boolean(nextState);
        document.querySelectorAll("[data-wa-action]").forEach(function (button) {
            button.disabled = pendingAction;
        });
        const toggle = document.getElementById("waBotToggle");
        if (toggle) {
            toggle.disabled = pendingAction;
        }
        const reminderToggle = document.getElementById("waReminderEnabled");
        if (reminderToggle) {
            reminderToggle.disabled = pendingAction;
        }
        document.querySelectorAll("input[name='waReminderLeadDays']").forEach(function (radio) {
            radio.disabled = pendingAction;
        });
        const sendButton = document.getElementById("waComposerSendButton");
        if (sendButton) {
            sendButton.disabled = pendingAction;
        }
    }

    function applyStatusToHeader(status) {
        const meta = getStatusMeta(status && status.status);
        const label = document.getElementById("waBotToggleLabel");
        const toggle = document.getElementById("waBotToggle");
        if (label) {
            label.textContent = "Status: " + meta.label;
        }
        if (toggle) {
            toggle.checked = Boolean(status && (status.status === "connected" || status.status === "authenticated" || status.status === "initializing"));
            toggle.dataset.status = status && status.status ? status.status : "inactive";
        }
    }

    function renderStatus(status) {
        state = status || null;
        applyStatusToHeader(state);

        const resolved = state || { status: "inactive", ready: false, hasQr: false };
        const meta = getStatusMeta(resolved.status);

        const badge = document.getElementById("waStatusBadge");
        const text = document.getElementById("waStatusText");
        const note = document.getElementById("waStatusNote");
        const qrImage = document.getElementById("waQrImage");
        const qrWrap = document.getElementById("waQrWrap");
        const qrHint = document.getElementById("waQrHint");
        const sessionValue = document.getElementById("waSessionValue");
        const connectedValue = document.getElementById("waConnectedAtValue");
        const disconnectedValue = document.getElementById("waDisconnectedAtValue");
        const qrUpdatedValue = document.getElementById("waQrUpdatedAtValue");
        const errorValue = document.getElementById("waLastErrorValue");
        const connectButton = document.getElementById("waConnectButton");
        const refreshButton = document.getElementById("waRefreshQrButton");
        const logoutButton = document.getElementById("waLogoutButton");
        const resetButton = document.getElementById("waResetSessionButton");

        if (badge) {
            badge.className = "wa-status-badge " + meta.tone;
            badge.textContent = meta.label;
        }
        if (text) {
            text.textContent = meta.label;
        }
        if (note) {
            note.textContent = resolved.lastError || meta.description;
        }
        if (qrWrap) {
            qrWrap.hidden = !resolved.hasQr;
        }
        if (qrImage) {
            qrImage.src = resolved.qrCode || "";
            qrImage.alt = resolved.hasQr ? "QR Login WhatsApp" : "";
        }
        if (qrHint) {
            qrHint.textContent = resolved.hasQr
                ? "Buka WhatsApp di ponsel, pilih Perangkat Tertaut, lalu scan QR ini."
                : "QR akan muncul saat session membutuhkan login.";
        }
        if (sessionValue) {
            sessionValue.textContent = resolved.sessionId || "default";
        }
        if (connectedValue) {
            connectedValue.textContent = formatDateTime(resolved.lastConnectedAt);
        }
        if (disconnectedValue) {
            disconnectedValue.textContent = formatDateTime(resolved.lastDisconnectedAt);
        }
        if (qrUpdatedValue) {
            qrUpdatedValue.textContent = formatDateTime(resolved.qrUpdatedAt);
        }
        if (errorValue) {
            errorValue.textContent = resolved.lastError || "-";
        }
        if (connectButton) {
            connectButton.textContent = resolved.status === "connected" ? "Connected" : (resolved.status === "initializing" ? "Connecting..." : "Hubungkan");
            connectButton.disabled = pendingAction || resolved.status === "connected" || resolved.status === "initializing";
        }
        if (refreshButton) {
            refreshButton.disabled = pendingAction || resolved.status === "connected";
        }
        if (logoutButton) {
            logoutButton.disabled = pendingAction || !(resolved.status === "connected" || resolved.status === "authenticated" || resolved.status === "initializing");
        }
        if (resetButton) {
            resetButton.disabled = pendingAction;
        }

        renderComposerStatusBanner();
    }

    function renderReminderSettings(settings) {
        reminderSettings = settings || null;
        const enabled = document.getElementById("waReminderEnabled");
        const radios = document.querySelectorAll("input[name='waReminderLeadDays']");

        if (enabled) {
            enabled.checked = Boolean(settings && settings.enabled);
        }
        radios.forEach(function (radio) {
            radio.checked = String(settings && settings.leadDays ? settings.leadDays : 3) === String(radio.value);
        });
    }

    function renderGatewayBootstrapStatus(status) {
        bootstrapState = status || null;

        const badge = document.getElementById("waGatewayInstallBadge");
        const sourceValue = document.getElementById("waGatewaySourceValue");
        const installedValue = document.getElementById("waGatewayInstalledValue");
        const runtimeValue = document.getElementById("waGatewayRuntimeValue");
        const reachableValue = document.getElementById("waGatewayReachableValue");
        const messageValue = document.getElementById("waGatewayInstallMessage");

        const resolved = status || {
            sourceAvailable: false,
            installed: false,
            runtimeAvailable: false,
            reachable: false,
            installationRunning: false,
            installState: "idle",
            installMessage: "Gateway WhatsApp belum dianalisis."
        };

        let badgeTone = "muted";
        let badgeLabel = "Belum Dicek";
        if (resolved.installationRunning || resolved.installState === "running") {
            badgeTone = "info";
            badgeLabel = "Installing";
        } else if (resolved.installState === "success") {
            badgeTone = "success";
            badgeLabel = "Siap";
        } else if (resolved.installState === "error") {
            badgeTone = "danger";
            badgeLabel = "Error";
        } else if (resolved.installed) {
            badgeTone = "info";
            badgeLabel = "Terdeteksi";
        }

        if (badge) {
            badge.className = "wa-status-badge " + badgeTone;
            badge.textContent = badgeLabel;
        }
        if (sourceValue) {
            sourceValue.textContent = resolved.sourceAvailable ? "Tersedia" : "Tidak ada";
        }
        if (installedValue) {
            installedValue.textContent = resolved.installed ? "Terpasang" : "Belum terpasang";
        }
        if (runtimeValue) {
            runtimeValue.textContent = resolved.runtimeAvailable ? "Node.js siap" : "Node.js belum ada";
        }
        if (reachableValue) {
            reachableValue.textContent = resolved.reachable ? "Aktif" : "Belum aktif";
        }
        if (messageValue) {
            messageValue.textContent = resolved.installMessage || "Gateway WhatsApp belum dianalisis.";
        }
    }

    function renderComposerStatusBanner(extraMessage) {
        const banner = document.getElementById("waComposerStatusBanner");
        if (!banner) {
            return;
        }
        const resolved = state || { status: "inactive" };
        const meta = getStatusMeta(resolved.status);
        let message = extraMessage || meta.description;
        if (resolved.status === "connected") {
            message = extraMessage || "Session WhatsApp terhubung. Pilih chat di kiri atau mulai chat baru dari nomor tujuan.";
        }
        if (resolved.status === "qr_required") {
            message = extraMessage || "Session butuh scan QR. Buka Setting BOT WA untuk menyelesaikan login.";
        }
        banner.textContent = message;
    }

    async function fetchJson(url, options) {
        const response = await fetch(url, Object.assign({
            credentials: "same-origin",
            headers: requestHeaders(options && options.method && options.method !== "GET")
        }, options || {}));
        const result = await response.json();
        if (!response.ok || !result.success) {
            throw new Error(result.message || "Permintaan WhatsApp gagal.");
        }
        return result;
    }

    async function refreshStatus() {
        try {
            const result = await fetchJson("/api/whatsapp/status");
            renderStatus(result.data);
            return result.data;
        } catch (error) {
            renderStatus({
                status: "error",
                ready: false,
                hasQr: false,
                sessionId: "default",
                lastError: error.message
            });
            return null;
        }
    }

    async function refreshReminderSettings() {
        try {
            const result = await fetchJson("/api/whatsapp/reminder-settings");
            renderReminderSettings(result.data);
            return result.data;
        } catch (error) {
            showAlert("error", error.message || "Gagal memuat setting reminder WhatsApp.");
            return null;
        }
    }

    async function refreshGatewayBootstrapStatus(silent) {
        try {
            const result = await fetchJson("/api/whatsapp/gateway/bootstrap-status");
            renderGatewayBootstrapStatus(result.data);
            return result.data;
        } catch (error) {
            renderGatewayBootstrapStatus({
                sourceAvailable: false,
                installed: false,
                runtimeAvailable: false,
                reachable: false,
                installationRunning: false,
                installState: "error",
                installMessage: error.message || "Gagal membaca status instalasi WhatsApp gateway."
            });
            if (!silent) {
                showAlert("error", error.message || "Gagal membaca status instalasi WhatsApp gateway.");
            }
            return null;
        }
    }

    function schedulePolling() {
        window.clearTimeout(pollTimer);
        pollTimer = window.setTimeout(async function () {
            await refreshStatus();
            if (isModalOpen(settingsModalId) || (bootstrapState && bootstrapState.installationRunning)) {
                await refreshGatewayBootstrapStatus(true);
            }
            schedulePolling();
        }, (isModalOpen(settingsModalId) || isModalOpen(composerModalId)) ? pollOpenMs : pollIdleMs);
    }

    async function runAction(action, successMessage) {
        setActionLoading(true);
        try {
            const result = await fetchJson(action.url, {
                method: action.method || "POST",
                body: action.body ? JSON.stringify(action.body) : undefined
            });
            renderStatus(result.data);
            const status = result && result.data && result.data.status ? String(result.data.status).toLowerCase() : "";
            if (status === "error") {
                showAlert("error", result.message || "Gateway WhatsApp belum siap.");
                return result.data;
            }
            if (successMessage) {
                showAlert("success", successMessage);
            }
            return result.data;
        } catch (error) {
            showAlert("error", error.message || "Aksi WhatsApp gagal.");
            return null;
        } finally {
            setActionLoading(false);
            await refreshStatus();
        }
    }

    async function ensureInitialized() {
        const bootstrap = await refreshGatewayBootstrapStatus(true);
        if (!bootstrap || !bootstrap.reachable) {
            return;
        }
        const latest = await refreshStatus();
        if (!latest || latest.status === "inactive" || latest.status === "disconnected" || latest.status === "auth_failure") {
            await runAction({ url: "/api/whatsapp/init" });
        }
    }

    async function openSettings() {
        openModal(settingsModalId);
        renderStatus(state || {
            status: "initializing",
            ready: false,
            hasQr: false,
            sessionId: "default"
        });
        await refreshReminderSettings();
        await refreshGatewayBootstrapStatus(true);
        await ensureInitialized();
        schedulePolling();
    }

    async function connect() {
        await runAction({ url: "/api/whatsapp/init" }, "Inisialisasi WhatsApp dimulai.");
        await refreshComposerView();
    }

    async function regenerateQr() {
        await runAction({ url: "/api/whatsapp/qr/regenerate" }, "QR WhatsApp diperbarui.");
    }

    async function logout() {
        const confirmed = await (window.nmxConfirm
            ? window.nmxConfirm("Putuskan session WhatsApp sekarang?", {
                title: "Disconnect WhatsApp",
                confirmText: "Putuskan",
                confirmClass: "btn btn-danger"
            })
            : Promise.resolve(window.confirm("Putuskan session WhatsApp sekarang?")));
        if (!confirmed) {
            return;
        }
        await runAction({ url: "/api/whatsapp/logout" }, "Session WhatsApp diputuskan.");
        resetComposerSelection();
        renderComposerChats([]);
        renderComposerMessages([]);
    }

    async function resetSession() {
        const confirmed = await (window.nmxConfirm
            ? window.nmxConfirm("Reset session akan menghapus login tersimpan. Lanjutkan?", {
                title: "Reset Session",
                confirmText: "Reset",
                confirmClass: "btn btn-danger"
            })
            : Promise.resolve(window.confirm("Reset session akan menghapus login tersimpan. Lanjutkan?")));
        if (!confirmed) {
            return;
        }
        await runAction({ url: "/api/whatsapp/reset-session" }, "Session WhatsApp berhasil direset.");
        resetComposerSelection();
        renderComposerChats([]);
        renderComposerMessages([]);
    }

    async function saveReminderSettings() {
        const enabled = document.getElementById("waReminderEnabled");
        const selected = document.querySelector("input[name='waReminderLeadDays']:checked");
        const leadDays = selected ? Number(selected.value) : 3;
        setActionLoading(true);
        try {
            const result = await fetchJson("/api/whatsapp/reminder-settings", {
                method: "POST",
                body: JSON.stringify({
                    enabled: Boolean(enabled && enabled.checked),
                    leadDays: leadDays
                })
            });
            renderReminderSettings(result.data);
            showAlert("success", "Setting reminder WhatsApp berhasil disimpan.");
        } catch (error) {
            showAlert("error", error.message || "Gagal menyimpan setting reminder WhatsApp.");
        } finally {
            setActionLoading(false);
        }
    }

    async function handleToggle(toggle) {
        const checked = Boolean(toggle && toggle.checked);
        try {
            if (checked) {
                openModal(settingsModalId);
                await refreshReminderSettings();
                const ready = await ensureGatewayReadyForToggle();
                if (!ready) {
                    if (toggle) {
                        toggle.checked = false;
                    }
                    return;
                }
                await connect();
            } else {
                await logout();
            }
        } catch (error) {
            if (toggle) {
                toggle.checked = !checked;
            }
        } finally {
            await refreshStatus();
        }
    }

    async function ensureGatewayReadyForToggle() {
        const bootstrap = await refreshGatewayBootstrapStatus();
        if (!bootstrap || !bootstrap.sourceAvailable) {
            throw new Error("Folder source WhatsApp gateway tidak ditemukan di project ini.");
        }

        if (!bootstrap.installed) {
            const approved = await (window.nmxConfirm
                ? window.nmxConfirm("Gateway WhatsApp belum terinstal. Lanjutkan instalasi sekarang?", {
                    title: "Install Gateway",
                    confirmText: "Install Sekarang",
                    confirmClass: "btn btn-primary"
                })
                : Promise.resolve(window.confirm("Gateway WhatsApp belum terinstal. Lanjutkan instalasi sekarang?")));
            if (!approved) {
                renderGatewayBootstrapStatus(Object.assign({}, bootstrap, {
                    installState: "idle",
                    installMessage: "Instalasi gateway dibatalkan oleh pengguna."
                }));
                showAlert("info", "Instalasi gateway WhatsApp dibatalkan.");
                return false;
            }
            await installGatewayInBackground();
        } else if (!bootstrap.reachable) {
            await installGatewayInBackground();
        }

        const latestBootstrap = await refreshGatewayBootstrapStatus(true);
        if (!latestBootstrap || !latestBootstrap.reachable) {
            throw new Error((latestBootstrap && latestBootstrap.installMessage) || "Gateway WhatsApp belum aktif setelah proses persiapan.");
        }
        return true;
    }

    async function installGatewayInBackground() {
        setActionLoading(true);
        try {
            const result = await fetchJson("/api/whatsapp/gateway/install", {
                method: "POST"
            });
            renderGatewayBootstrapStatus(result.data);
            const completed = await waitForGatewayInstallCompletion();
            renderGatewayBootstrapStatus(completed);
            if (!completed || completed.installState === "error") {
                throw new Error((completed && completed.installMessage) || "Instalasi gateway WhatsApp gagal.");
            }
            showAlert("success", completed.installMessage || "Gateway WhatsApp berhasil disiapkan.");
            return completed;
        } finally {
            setActionLoading(false);
        }
    }

    async function waitForGatewayInstallCompletion() {
        for (let attempt = 0; attempt < 90; attempt += 1) {
            const latest = await refreshGatewayBootstrapStatus(true);
            if (latest && !latest.installationRunning && latest.installState !== "running") {
                return latest;
            }
            await new Promise(function (resolve) {
                window.setTimeout(resolve, 2000);
            });
        }
        throw new Error("Timeout saat menunggu instalasi gateway WhatsApp.");
    }

    function renderComposerChats(chats) {
        const sourceChats = Array.isArray(chats) ? chats : [];
        composerState.allChats = sourceChats;
        composerState.chats = applyChatFilter(sourceChats, composerState.activeFilter);
        syncFilterChips();
        const list = document.getElementById("waComposerChatList");
        if (!list) {
            return;
        }
        if (!composerState.chats.length) {
            list.innerHTML = '<div class="wa-chat-empty">Belum ada chat yang bisa ditampilkan untuk session ini.</div>';
            return;
        }

        list.innerHTML = composerState.chats.map(function (chat) {
            const chatId = normalizeChatId(chat);
            const activeClass = chatId === composerState.activeChatId ? " active" : "";
            const unread = Number(chat.unreadCount || 0) > 0 ? '<span class="wa-chat-item-badge">' + Number(chat.unreadCount) + '</span>' : "";
            const displayName = String(chat.name || chat.chatId || "Tanpa nama");
            const preview = getChatPreview(chat);
            const timeLabel = formatRelativeLabel(getChatTimestamp(chat));
            const tone = deriveChatTone(chat);
            const toneClass = tone.toLowerCase();
            const online = (isChatOnline(chat) || chatId === composerState.activeChatId) ? '<span class="wa-chat-online-dot"></span>' : "";
            return '' +
                '<button type="button" class="wa-chat-item' + activeClass + '" data-chat-id="' + escapeHtml(chatId) + '">' +
                    '<div class="wa-chat-item-avatar">' + escapeHtml(toInitials(displayName)) + '</div>' +
                    '<div class="wa-chat-item-main">' +
                        '<div class="wa-chat-item-head">' +
                            '<strong>' + escapeHtml(displayName) + '</strong>' +
                            '<span class="wa-chat-item-time">' + escapeHtml(timeLabel) + '</span>' +
                        '</div>' +
                        '<p class="wa-chat-item-preview">' + escapeHtml(preview) + '</p>' +
                        '<div class="wa-chat-item-meta">' +
                            online +
                            '<span class="wa-chat-badge ' + toneClass + '">' + escapeHtml(tone) + '</span>' +
                            unread +
                        '</div>' +
                    '</div>' +
                '</button>';
        }).join("");
    }

    function extractPhoneFromChat(chat) {
        const chatId = String(chat && chat.chatId || "");
        const fromName = String(chat && chat.name || "");
        const found = chatId.match(/\d{8,16}/) || fromName.match(/\d{8,16}/);
        return found ? found[0] : "-";
    }

    function extractCustomerId(chat) {
        const fromName = String(chat && chat.name || "");
        const fromChat = String(chat && chat.chatId || "");
        const sipMatch = fromName.match(/SIP[-\s]?\d+/i) || fromChat.match(/SIP[-\s]?\d+/i);
        return sipMatch ? sipMatch[0].replace(/\s+/g, "-").toUpperCase() : "SIP-1029";
    }

    function buildAiSuggestion(chat, messages) {
        const preview = getChatPreview(chat).toLowerCase();
        const hasComplaintKeyword = preview.includes("gangguan") || preview.includes("lambat") || preview.includes("komplain");
        if (hasComplaintKeyword) {
            return "Mohon maaf atas kendala internetnya. Boleh diinformasikan ID pelanggan agar kami cek status jaringan dan ONT saat ini?";
        }
        const hasQuestion = Array.isArray(messages) && messages.some(function (item) {
            return !item.fromMe && String(item.body || "").includes("?");
        });
        if (hasQuestion) {
            return "Terima kasih informasinya. Kami bantu cek detail kebutuhan Anda, mohon tunggu sebentar ya.";
        }
        return "Terima kasih sudah menghubungi kami. Silakan kirim detail kendala agar tim support bisa membantu lebih cepat.";
    }

    function updateConversationPanels(chat, messages) {
        const title = document.getElementById("waComposerChatTitle");
        const meta = document.getElementById("waComposerChatMeta");
        const headerAvatar = document.getElementById("waComposerHeaderAvatar");
        const aiText = document.getElementById("waAiSuggestionText");
        const detailName = document.getElementById("waDetailName");
        const detailCustomerId = document.getElementById("waDetailCustomerId");
        const detailPhone = document.getElementById("waDetailPhone");
        const detailLocation = document.getElementById("waDetailLocation");
        const detailNetwork = document.getElementById("waDetailNetworkStatus");
        const detailProgress = document.getElementById("waDetailNetworkProgress");
        const detailOnt = document.getElementById("waDetailOntUptime");
        const detailSla = document.getElementById("waDetailSlaTimer");
        const detailNote = document.getElementById("waDetailInternalNote");
        if (!chat) {
            if (title) title.textContent = "Pilih Percakapan";
            if (meta) meta.textContent = "Active now • SIP-1029";
            if (headerAvatar) headerAvatar.textContent = "C";
            if (aiText) aiText.textContent = "Mohon diinformasikan ID pelanggan agar kami bisa cek status jaringan lebih lanjut.";
            if (detailName) detailName.textContent = "-";
            if (detailCustomerId) detailCustomerId.textContent = "-";
            if (detailPhone) detailPhone.textContent = "-";
            if (detailLocation) detailLocation.textContent = "Long Mesangat";
            if (detailNetwork) detailNetwork.textContent = "Stable";
            if (detailProgress) detailProgress.style.width = "84%";
            if (detailOnt) detailOnt.textContent = "12d 4h 22m";
            if (detailSla) detailSla.textContent = "00:45:12";
            if (detailNote) detailNote.textContent = "Belum ada catatan internal. Anda bisa menambahkan note penanganan customer di sini.";
            return;
        }

        const customerName = String(chat.name || chat.chatId || "Customer");
        const customerId = extractCustomerId(chat);
        const phone = extractPhoneFromChat(chat);
        const noteText = deriveChatTone(chat) === "COMPLAINT"
            ? "Customer sempat mengeluhkan koneksi melambat di jam sibuk. Prioritaskan pengecekan ONT dan signal."
            : "Pelanggan aktif, lanjutkan verifikasi kebutuhan dan dokumentasi ringkas hasil penanganan.";

        if (title) title.textContent = customerName;
        if (meta) meta.textContent = "Active now • " + customerId;
        if (headerAvatar) headerAvatar.textContent = toInitials(customerName);
        if (aiText) aiText.textContent = buildAiSuggestion(chat, messages);
        if (detailName) detailName.textContent = customerName;
        if (detailCustomerId) detailCustomerId.textContent = customerId;
        if (detailPhone) detailPhone.textContent = phone;
        if (detailLocation) detailLocation.textContent = "Surabaya";
        if (detailNetwork) detailNetwork.textContent = deriveChatTone(chat) === "COMPLAINT" ? "Needs Attention" : "Stable";
        if (detailProgress) detailProgress.style.width = deriveChatTone(chat) === "COMPLAINT" ? "58%" : "84%";
        if (detailOnt) detailOnt.textContent = deriveChatTone(chat) === "COMPLAINT" ? "3d 6h 18m" : "12d 4h 22m";
        if (detailSla) detailSla.textContent = deriveChatTone(chat) === "COMPLAINT" ? "00:19:42" : "00:45:12";
        if (detailNote) detailNote.textContent = noteText;
    }

    function renderComposerMessages(messages) {
        composerState.messages = Array.isArray(messages) ? messages : [];
        const list = document.getElementById("waComposerMessageList");
        const empty = document.getElementById("waComposerEmptyState");
        const conversation = document.getElementById("waComposerConversation");
        const activeInput = document.getElementById("waComposerActiveChatInput");
        const phoneInput = document.getElementById("waComposerPhoneInput");
        const title = document.getElementById("waComposerChatTitle");
        const meta = document.getElementById("waComposerChatMeta");

        if (!list || !empty || !conversation || !activeInput || !phoneInput || !title || !meta) {
            return;
        }

        if (!composerState.activeChat) {
            empty.hidden = false;
            conversation.hidden = true;
            activeInput.value = "";
            updateConversationPanels(null, []);
            return;
        }

        empty.hidden = true;
        conversation.hidden = false;
        activeInput.value = composerState.activeChat.chatId || "";
        phoneInput.value = "";
        title.textContent = composerState.activeChat.name || composerState.activeChat.chatId || "Chat WhatsApp";
        meta.textContent = (composerState.activeChat.isGroup ? "Grup" : "Personal") + " • " + (composerState.activeChat.chatId || "");
        updateConversationPanels(composerState.activeChat, composerState.messages);

        if (!composerState.messages.length) {
            list.innerHTML = '<div class="wa-chat-empty">Belum ada riwayat pesan yang ditampilkan.</div>';
            return;
        }

        let lastDateKey = "";
        list.innerHTML = composerState.messages.map(function (message) {
            const outgoing = message.fromMe ? " outgoing" : "";
            const body = message.body || (message.hasMedia ? "[Media]" : "Pesan kosong");
            const sentDate = parseDateValue(message.sentAt);
            const dateKey = sentDate
                ? new Intl.DateTimeFormat("id-ID", { year: "numeric", month: "2-digit", day: "2-digit" }).format(sentDate)
                : "";
            let dateSeparator = "";
            if (dateKey && dateKey !== lastDateKey) {
                const dayLabel = (new Date().toDateString() === sentDate.toDateString())
                    ? "Today"
                    : new Intl.DateTimeFormat("id-ID", { weekday: "long", day: "2-digit", month: "short" }).format(sentDate);
                dateSeparator = '<div class="wa-date-separator"><span>' + escapeHtml(dayLabel + ", " + formatShortTime(sentDate)) + '</span></div>';
                lastDateKey = dateKey;
            }
            return '' +
                dateSeparator +
                '<div class="wa-message-item' + outgoing + '">' +
                    '<div class="wa-message-bubble">' +
                        '<div class="wa-message-text">' + escapeHtml(body) + '</div>' +
                        '<div class="wa-message-time">' + escapeHtml(formatShortTime(message.sentAt) || formatDateTime(message.sentAt)) + '</div>' +
                    '</div>' +
                '</div>';
        }).join("");
        list.scrollTop = list.scrollHeight;
    }

    function resetComposerSelection() {
        composerState.activeChatId = "";
        composerState.activeChat = null;
        composerState.messages = [];
        const activeInput = document.getElementById("waComposerActiveChatInput");
        if (activeInput) {
            activeInput.value = "";
        }
        const phoneInput = document.getElementById("waComposerPhoneInput");
        if (phoneInput) {
            phoneInput.value = "";
        }
        renderComposerMessages([]);
        renderComposerChats(composerState.allChats);
    }

    async function loadChats(preserveSelection) {
        const searchInput = document.getElementById("waComposerSearchInput");
        const keyword = searchInput ? String(searchInput.value || "").trim() : "";
        renderComposerStatusBanner("Memuat daftar chat WhatsApp...");
        const result = await fetchJson("/api/whatsapp/chats?limit=40" + (keyword ? "&search=" + encodeURIComponent(keyword) : ""));
        const payload = result.data || {};
        const chats = Array.isArray(payload.chats) ? payload.chats : [];
        renderComposerChats(chats);
        renderComposerStatusBanner(chats.length ? null : "Belum ada chat yang cocok untuk ditampilkan.");

        if (!preserveSelection && chats.length) {
            await selectChat(chats[0].chatId, false);
            return;
        }

        if (preserveSelection && composerState.activeChatId) {
            const activeChat = composerState.allChats.find(function (chat) {
                return chat.chatId === composerState.activeChatId;
            });
            if (activeChat) {
                composerState.activeChat = activeChat;
                renderComposerChats(chats);
                return;
            }
        }

        if (!composerState.activeChatId) {
            renderComposerMessages([]);
        }
    }

    async function selectChat(chatId, refreshList) {
        if (!chatId) {
            return;
        }
        const normalizedChatId = String(chatId);
        composerState.activeChatId = normalizedChatId;
        composerState.activeChat = composerState.allChats.find(function (chat) {
            return chat.chatId === normalizedChatId;
        }) || composerState.activeChat;
        renderComposerChats(composerState.allChats);
        renderComposerStatusBanner("Memuat riwayat chat...");
        const result = await fetchJson("/api/whatsapp/chats/" + encodeURIComponent(normalizedChatId) + "/messages?limit=50");
        const payload = result.data || {};
        composerState.activeChat = payload.chat || composerState.activeChat;
        renderComposerMessages(payload.messages || []);
        renderComposerStatusBanner();
        if (refreshList) {
            renderComposerChats(composerState.allChats);
        }
    }

    async function refreshComposerView() {
        const latest = await refreshStatus();
        if (!latest) {
            renderComposerStatusBanner("Status WhatsApp belum bisa dibaca.");
            return;
        }
        if (latest.status !== "connected") {
            renderComposerChats([]);
            resetComposerSelection();
            return;
        }
        await loadChats(true);
        if (composerState.activeChatId) {
            await selectChat(composerState.activeChatId, true);
        }
    }

    function applyComposerDraft(prefill) {
        startNewChat();

        const phoneInput = document.getElementById("waComposerPhoneInput");
        const messageInput = document.getElementById("waComposerMessageInput");
        const normalizedPhone = prefill && prefill.phone ? String(prefill.phone).trim() : "";
        const message = prefill && prefill.message ? String(prefill.message) : "";
        const label = prefill && prefill.documentLabel ? String(prefill.documentLabel) : "pesan";
        const customerName = prefill && prefill.customerName ? String(prefill.customerName).trim() : "pelanggan";

        if (phoneInput) {
            phoneInput.value = normalizedPhone;
        }
        if (messageInput) {
            messageInput.value = message;
            messageInput.focus();
            messageInput.setSelectionRange(messageInput.value.length, messageInput.value.length);
        }

        renderComposerStatusBanner("Draft " + label + " untuk " + customerName + (normalizedPhone ? " siap dikirim ke " + normalizedPhone : " siap dikirim.") );
    }

    async function openComposer(event, prefill) {
        if (event && typeof event.preventDefault === "function") {
            event.preventDefault();
        }
        openModal(composerModalId);
        renderComposerStatusBanner("Menyiapkan WhatsApp Web...");
        await ensureInitialized();
        await refreshComposerView();
        if (prefill && typeof prefill === "object") {
            applyComposerDraft(prefill);
        }
        schedulePolling();
    }

    async function submitComposerMessage(event) {
        if (event) {
            event.preventDefault();
        }
        const messageInput = document.getElementById("waComposerMessageInput");
        const phoneInput = document.getElementById("waComposerPhoneInput");
        const message = messageInput ? String(messageInput.value || "").trim() : "";
        const to = phoneInput ? String(phoneInput.value || "").trim() : "";

        if (!message) {
            showAlert("error", "Isi pesan WhatsApp wajib diisi.");
            return;
        }
        if (!composerState.activeChatId && !to) {
            showAlert("error", "Pilih chat aktif atau isi nomor tujuan terlebih dahulu.");
            return;
        }

        setActionLoading(true);
        try {
            const result = await fetchJson("/api/whatsapp/messages/send", {
                method: "POST",
                body: JSON.stringify({
                    chatId: composerState.activeChatId || null,
                    to: composerState.activeChatId ? null : to,
                    message: message
                })
            });
            if (messageInput) {
                messageInput.value = "";
            }
            showAlert("success", result.message || "Pesan WhatsApp berhasil dikirim.");
            await loadChats(true);
            const nextChatId = composerState.activeChatId || (result.data && result.data.chatId ? result.data.chatId : "");
            if (nextChatId) {
                await selectChat(nextChatId, true);
            }
        } catch (error) {
            showAlert("error", error.message || "Gagal mengirim pesan WhatsApp.");
        } finally {
            setActionLoading(false);
        }
    }

    function startNewChat() {
        composerState.activeChatId = "";
        composerState.activeChat = null;
        composerState.messages = [];
        renderComposerChats(composerState.allChats);
        renderComposerMessages([]);
        const phoneInput = document.getElementById("waComposerPhoneInput");
        const messageInput = document.getElementById("waComposerMessageInput");
        if (phoneInput) {
            phoneInput.focus();
        }
        if (messageInput) {
            messageInput.value = "";
        }
        renderComposerStatusBanner("Siap membuat chat baru. Isi nomor tujuan lalu tulis pesan.");
    }

    function escapeHtml(value) {
        return String(value || "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/\"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function bindEvents() {
        const settingsModal = getModal(settingsModalId);
        if (settingsModal && settingsModal.dataset.bound !== "true") {
            settingsModal.dataset.bound = "true";
            settingsModal.addEventListener("click", function (event) {
                if (event.target && event.target.matches("[data-wa-close]")) {
                    closeModal(settingsModalId);
                    schedulePolling();
                }
            });
        }

        const composerModal = getModal(composerModalId);
        if (composerModal && composerModal.dataset.bound !== "true") {
            composerModal.dataset.bound = "true";
            composerModal.addEventListener("click", function (event) {
                if (event.target && event.target.matches("[data-wa-compose-close]")) {
                    closeModal(composerModalId);
                    schedulePolling();
                    return;
                }
                const item = event.target && event.target.closest ? event.target.closest("[data-chat-id]") : null;
                if (item) {
                    selectChat(item.getAttribute("data-chat-id"), true).catch(function (error) {
                        showAlert("error", error.message || "Gagal memuat chat WhatsApp.");
                    });
                }
            });
        }

        const connectButton = document.getElementById("waConnectButton");
        const refreshButton = document.getElementById("waRefreshQrButton");
        const logoutButton = document.getElementById("waLogoutButton");
        const resetButton = document.getElementById("waResetSessionButton");
        const reminderToggle = document.getElementById("waReminderEnabled");
        const reminderLeadRadios = document.querySelectorAll("input[name='waReminderLeadDays']");
        const composerForm = document.getElementById("waComposerForm");
        const composerRefreshButton = document.getElementById("waComposerRefreshButton");
        const composerSettingsButton = document.getElementById("waComposerOpenSettingsButton");
        const composerNewChatButton = document.getElementById("waComposerNewChatButton");
        const composerSearchInput = document.getElementById("waComposerSearchInput");
        const filterChipsWrap = document.getElementById("waComposerFilterChips");
        const quickReplyBar = document.getElementById("waQuickReplyBar");
        const aiUseResponseButton = document.getElementById("waAiUseResponseButton");

        if (connectButton) {
            connectButton.addEventListener("click", connect);
        }
        if (refreshButton) {
            refreshButton.addEventListener("click", regenerateQr);
        }
        if (logoutButton) {
            logoutButton.addEventListener("click", logout);
        }
        if (resetButton) {
            resetButton.addEventListener("click", resetSession);
        }
        if (reminderToggle) {
            reminderToggle.addEventListener("change", function () {
                saveReminderSettings().catch(function (error) {
                    showAlert("error", error.message || "Gagal menyimpan setting reminder WhatsApp.");
                });
            });
        }
        reminderLeadRadios.forEach(function (radio) {
            radio.addEventListener("change", function () {
                if (!radio.checked) {
                    return;
                }
                saveReminderSettings().catch(function (error) {
                    showAlert("error", error.message || "Gagal menyimpan setting reminder WhatsApp.");
                });
            });
        });
        if (composerForm) {
            composerForm.addEventListener("submit", submitComposerMessage);
        }
        if (composerRefreshButton) {
            composerRefreshButton.addEventListener("click", function () {
                refreshComposerView().catch(function (error) {
                    showAlert("error", error.message || "Gagal memuat ulang chat WhatsApp.");
                });
            });
        }
        if (composerSettingsButton) {
            composerSettingsButton.addEventListener("click", openSettings);
        }
        if (composerNewChatButton) {
            composerNewChatButton.addEventListener("click", startNewChat);
        }
        if (composerSearchInput) {
            composerSearchInput.addEventListener("input", function () {
                window.clearTimeout(chatSearchTimer);
                chatSearchTimer = window.setTimeout(function () {
                    if (state && state.status === "connected") {
                        loadChats(false).catch(function (error) {
                            showAlert("error", error.message || "Gagal memfilter chat WhatsApp.");
                        });
                    }
                }, 300);
            });
        }
        if (filterChipsWrap) {
            filterChipsWrap.addEventListener("click", function (event) {
                const chip = event.target && event.target.closest ? event.target.closest("[data-wa-filter]") : null;
                if (!chip) {
                    return;
                }
                composerState.activeFilter = String(chip.getAttribute("data-wa-filter") || "all").toLowerCase();
                renderComposerChats(composerState.allChats);
            });
        }
        if (quickReplyBar) {
            quickReplyBar.addEventListener("click", function (event) {
                const chip = event.target && event.target.closest ? event.target.closest("[data-wa-quick]") : null;
                if (!chip) {
                    return;
                }
                const messageInput = document.getElementById("waComposerMessageInput");
                if (!messageInput) {
                    return;
                }
                const quickText = String(chip.getAttribute("data-wa-quick") || "").trim();
                if (!quickText) {
                    return;
                }
                messageInput.value = quickText;
                messageInput.focus();
                messageInput.setSelectionRange(messageInput.value.length, messageInput.value.length);
            });
        }
        if (aiUseResponseButton) {
            aiUseResponseButton.addEventListener("click", function () {
                const suggestion = document.getElementById("waAiSuggestionText");
                const messageInput = document.getElementById("waComposerMessageInput");
                if (!suggestion || !messageInput) {
                    return;
                }
                const text = String(suggestion.textContent || "").trim();
                if (!text) {
                    return;
                }
                messageInput.value = text;
                messageInput.focus();
                messageInput.setSelectionRange(messageInput.value.length, messageInput.value.length);
            });
        }
    }

    document.addEventListener("DOMContentLoaded", function () {
        bindEvents();
        Promise.all([refreshStatus(), refreshGatewayBootstrapStatus(true)]).finally(schedulePolling);
    });

    window.NmxWhatsappPanel = {
        open: openSettings,
        openComposer: openComposer,
        openDraft: function (prefill) {
            return openComposer(null, prefill);
        },
        close: function () {
            closeModal(settingsModalId);
            closeModal(composerModalId);
        },
        refreshStatus: refreshStatus,
        refreshComposer: refreshComposerView,
        toggle: handleToggle
    };

    window.openWaBotSettings = function () {
        return openSettings();
    };

    window.toggleWaBot = function (toggle) {
        return handleToggle(toggle);
    };

    window.openWaComposer = function (event, prefill) {
        return openComposer(event, prefill);
    };
})();





