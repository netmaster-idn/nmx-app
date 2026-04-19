(function () {
    const state = {
        routers: [],
        selectedRouterId: null,
        summaryAbortController: null,
        pppoeAbortController: null,
        logsAbortController: null,
        summaryTimer: null,
        modalTimer: null,
        modalAbortController: null,
        pppoeSearch: "",
        modalUser: null,
        latestSummary: null,
        latestPppoe: [],
        latestLogs: []
    };

    function request(url, options) {
        const headers = {};
        if (window.csrfToken) {
            headers[window.csrfHeader || "X-CSRF-TOKEN"] = window.csrfToken;
        }
        return fetch(url, { ...(options || {}), headers }).then(async response => {
            const payload = await response.json();
            if (!response.ok || payload.success === false) {
                throw new Error(payload.message || "Permintaan gagal");
            }
            return payload.data;
        });
    }

    function byId(id) {
        return document.getElementById(id);
    }

    function formatDateTime(value) {
        if (!value) {
            return "-";
        }
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? value : date.toLocaleString("id-ID");
    }

    function formatBps(value) {
        const amount = Number(value || 0);
        if (amount >= 1000000000) {
            return `${(amount / 1000000000).toFixed(2)} Gbps`;
        }
        if (amount >= 1000000) {
            return `${(amount / 1000000).toFixed(2)} Mbps`;
        }
        if (amount >= 1000) {
            return `${(amount / 1000).toFixed(2)} Kbps`;
        }
        return `${amount.toFixed(0)} bps`;
    }

    function formatBytes(value) {
        const amount = Number(value || 0);
        if (amount <= 0) {
            return "-";
        }
        const units = ["B", "KB", "MB", "GB", "TB"];
        let index = 0;
        let current = amount;
        while (current >= 1024 && index < units.length - 1) {
            current /= 1024;
            index += 1;
        }
        return `${current.toFixed(current >= 10 ? 1 : 2)} ${units[index]}`;
    }

    function formatDuration(seconds) {
        const total = Number(seconds || 0);
        if (!total) {
            return "-";
        }
        const days = Math.floor(total / 86400);
        const hours = Math.floor((total % 86400) / 3600);
        const minutes = Math.floor((total % 3600) / 60);
        if (days > 0) {
            return `${days}h ${hours}j ${minutes}m`;
        }
        if (hours > 0) {
            return `${hours}j ${minutes}m`;
        }
        return `${minutes}m`;
    }

    function statusPill(status) {
        const safe = String(status || "unknown").toLowerCase();
        return `<span class="mk-monitor-pill mk-status-${safe}">${status || "unknown"}</span>`;
    }

    function setStateBadge(stateInfo) {
        const badge = byId("mkRouterStateBadge");
        const status = stateInfo && stateInfo.status ? stateInfo.status : "empty";
        badge.className = `mk-monitor-badge ${status}`;
        badge.textContent = status;
    }

    function renderRouterFilter(payload) {
        const filter = byId("mkRouterFilter");
        state.routers = payload.items || [];
        const options = state.routers.map(router => `<option value="${router.id}">${router.name || "Router"}${router.host ? " • " + router.host : ""}</option>`).join("");
        filter.innerHTML = options || '<option value="">Belum ada router</option>';
        if (payload.showFilter === false) {
            filter.classList.add("mk-monitor-hidden");
        } else {
            filter.classList.remove("mk-monitor-hidden");
        }
        if (!state.selectedRouterId) {
            state.selectedRouterId = payload.defaultRouterId || payload.singleRouterId || (state.routers[0] && state.routers[0].id) || null;
        }
        if (state.selectedRouterId) {
            filter.value = String(state.selectedRouterId);
        }
    }

    function renderSummary(summary) {
        state.latestSummary = summary;
        const router = summary.router;
        const routerState = router && router.state ? router.state : { status: "empty" };
        setStateBadge(routerState);

        if (!router) {
            byId("mkStatRouterName").textContent = "-";
            byId("mkStatRouterMeta").textContent = "Belum ada router aktif";
            byId("mkStatTraffic").textContent = "0 bps";
            byId("mkStatTrafficMeta").textContent = "Snapshot belum tersedia";
            byId("mkStatCpu").textContent = "0%";
            byId("mkStatCpuMeta").textContent = "Resource belum tersedia";
            byId("mkStatPppoe").textContent = "0";
            byId("mkStatPppoeMeta").textContent = "Belum ada session";
            renderEther1(null, routerState);
            renderResources(null);
            state.latestPppoe = [];
            state.latestLogs = [];
            renderPppoe([]);
            renderLogs([]);
            return;
        }

        byId("mkStatRouterName").textContent = router.name || "-";
        byId("mkStatRouterMeta").textContent = `${router.host || "-"} • last success ${formatDateTime(routerState.lastSuccessAt)}`;
        byId("mkStatTraffic").textContent = `${formatBps(router.ether1 ? router.ether1.rxRateBps : 0)} / ${formatBps(router.ether1 ? router.ether1.txRateBps : 0)}`;
        byId("mkStatTrafficMeta").textContent = `${router.ether1 ? (router.ether1.interfaceName || "ether1") : "ether1"} • ${router.ether1 ? (router.ether1.status || "-") : "-"}`;
        byId("mkStatCpu").textContent = `${router.resources && router.resources.cpuLoad != null ? router.resources.cpuLoad : 0}%`;
        byId("mkStatCpuMeta").textContent = router.resources ? `${router.resources.boardName || "-"} • ${router.resources.version || "-"}` : "Resource belum tersedia";
        byId("mkStatPppoe").textContent = String((router.pppoe || []).length);
        byId("mkStatPppoeMeta").textContent = `Snapshot ${formatDateTime(routerState.lastSuccessAt)}`;

        renderEther1(router.ether1, routerState);
        renderResources(router.resources);

        const pppoeItems = Array.isArray(router.pppoe) ? router.pppoe : [];
        const logItems = Array.isArray(router.logs) ? router.logs : [];
        if (!state.latestPppoe.length && pppoeItems.length) {
            state.latestPppoe = pppoeItems;
        }
        if (!state.latestLogs.length && logItems.length) {
            state.latestLogs = logItems;
        }
        renderPppoe(state.latestPppoe);
        renderLogs(state.latestLogs);
    }

    function renderEther1(ether1, stateInfo) {
        const card = byId("mkEtherCard");
        const placeholder = byId("mkEtherState");
        if (!ether1) {
            card.classList.add("mk-monitor-hidden");
            placeholder.classList.remove("mk-monitor-hidden");
            placeholder.textContent = stateInfo && stateInfo.lastError
                ? `Snapshot ether1 belum tersedia. ${stateInfo.lastError}`
                : "Menunggu snapshot ether1.";
            return;
        }

        card.classList.remove("mk-monitor-hidden");
        placeholder.classList.add("mk-monitor-hidden");
        byId("mkEtherRxNow").textContent = formatBps(ether1.rxRateBps);
        byId("mkEtherTxNow").textContent = formatBps(ether1.txRateBps);
        byId("mkEtherPackets").textContent = `${ether1.rxPackets || 0} / ${ether1.txPackets || 0}`;
        byId("mkEtherErrors").textContent = `${ether1.rxErrors || 0} / ${ether1.txErrors || 0}`;
        byId("mkEtherChart").innerHTML = buildTrafficChart(ether1.history || [], { theme: "realtime-flow" });
    }

    function renderResources(resources) {
        const grid = byId("mkResourceGrid");
        const placeholder = byId("mkResourceState");
        if (!resources) {
            grid.classList.add("mk-monitor-hidden");
            placeholder.classList.remove("mk-monitor-hidden");
            placeholder.textContent = "Menunggu resource router.";
            return;
        }

        grid.classList.remove("mk-monitor-hidden");
        placeholder.classList.add("mk-monitor-hidden");
        byId("mkResUptime").textContent = formatDuration(resources.uptimeSeconds);
        byId("mkResVersion").textContent = resources.version || "-";
        byId("mkResBoard").textContent = resources.boardName || resources.identityName || "-";
        byId("mkResArch").textContent = resources.architecture || "-";
        byId("mkResCpu").textContent = `${resources.cpuLoad != null ? resources.cpuLoad + "%" : "-"}${resources.cpuCount ? " • " + resources.cpuCount + " core" : ""}${resources.cpuFrequencyMhz ? " • " + resources.cpuFrequencyMhz + " MHz" : ""}`;
        byId("mkResMemory").textContent = `${formatBytes(resources.freeMemoryBytes)} / ${formatBytes(resources.totalMemoryBytes)}`;
        byId("mkResDisk").textContent = `${formatBytes(resources.freeHddBytes)} / ${formatBytes(resources.totalHddBytes)}`;
        byId("mkResCollectedAt").textContent = formatDateTime(resources.collectedAt);
    }

    function filteredPppoeItems(items) {
        const keyword = state.pppoeSearch.trim().toLowerCase();
        if (!keyword) {
            return items;
        }
        return items.filter(item => {
            return [
                item.username,
                item.interfaceName,
                item.ipAddress,
                item.callerId,
                item.profile
            ].some(value => String(value || "").toLowerCase().includes(keyword));
        });
    }

    function renderPppoe(items) {
        const body = byId("mkPppoeBody");
        const sourceItems = Array.isArray(items) ? items : [];
        state.latestPppoe = sourceItems;
        byId("mkPppoeQuickCount").textContent = String(sourceItems.length);
        byId("mkPppoeQuickPeek").innerHTML = sourceItems.length
            ? sourceItems.slice(0, 2).map(item => `
                <span><strong>${escapeHtml(item.username || "-")}</strong> • ${escapeHtml(item.ipAddress || item.interfaceName || "-")} • ${formatDuration(item.uptimeSeconds)}</span>
            `).join("")
            : "<span>Belum ada PPPoE aktif.</span>";
        const filtered = filteredPppoeItems(sourceItems);
        if (!filtered.length) {
            body.innerHTML = sourceItems.length
                ? '<tr><td colspan="5" class="mk-monitor-empty">Tidak ada hasil yang cocok dengan pencarian PPPoE.</td></tr>'
                : '<tr><td colspan="5" class="mk-monitor-empty">Belum ada PPPoE aktif untuk router ini.</td></tr>';
            return;
        }
        body.innerHTML = filtered.map(item => `
            <tr>
                <td>
                    <strong>${item.username || "-"}</strong>
                    <div class="mk-monitor-mini">${item.interfaceName || "-"} • ${item.ipAddress || "-"}</div>
                </td>
                <td>
                    <strong>RX ${formatBps(item.rxRateBps)}</strong>
                    <div class="mk-monitor-mini">TX ${formatBps(item.txRateBps)}</div>
                </td>
                <td>${formatDuration(item.uptimeSeconds)}</td>
                <td>${statusPill(item.status || "active")}</td>
                <td><button class="mk-monitor-link" type="button" data-pppoe-user="${encodeURIComponent(item.username || "")}">Lihat Grafik</button></td>
            </tr>
        `).join("");
    }

    function renderLogs(items) {
        const host = byId("mkLogList");
        const sourceItems = Array.isArray(items) ? items : [];
        state.latestLogs = sourceItems;
        byId("mkLogsQuickCount").textContent = String(sourceItems.length);
        byId("mkLogsQuickPeek").innerHTML = sourceItems.length
            ? sourceItems.slice(0, 2).map(item => `
                <span><strong>${escapeHtml(item.topics || item.severity || "log")}</strong> • ${escapeHtml((item.message || "-").slice(0, 72))}</span>
            `).join("")
            : "<span>Belum ada latest logs.</span>";
        const previewItems = sourceItems.slice(0, 8);
        if (!previewItems.length) {
            host.innerHTML = '<div class="mk-monitor-state">Belum ada log monitoring untuk router ini.</div>';
            return;
        }
        host.innerHTML = previewItems.map(item => `
            <article class="mk-monitor-log-item">
                <div class="mk-monitor-log-top">
                    <div>
                        ${statusPill(item.severity || "info")}
                        <div class="mk-monitor-mini" style="margin-top:8px;">${item.topics || "-"}</div>
                    </div>
                    <div class="mk-monitor-mini">${formatDateTime(item.time)}</div>
                </div>
                <div style="margin-top:10px;color:var(--text-primary);">${escapeHtml(item.message || "-")}</div>
            </article>
        `).join("");
    }

    function buildRealtimeTrafficChart(history) {
        const width = 980;
        const height = 280;
        const padding = { top: 36, right: 16, bottom: 24, left: 38 };
        const innerWidth = width - padding.left - padding.right;
        const innerHeight = height - padding.top - padding.bottom;
        const values = history.flatMap(point => [Number(point.rxRateBps || 0), Number(point.txRateBps || 0)]);
        const maxValue = Math.max(...values, 1);
        const step = history.length > 1 ? innerWidth / (history.length - 1) : 0;

        function valueToY(value) {
            return padding.top + innerHeight - ((Number(value || 0) / maxValue) * innerHeight);
        }

        const pointsToPath = key => history.map((point, index) => {
            const x = padding.left + step * index;
            const y = valueToY(point[key]);
            return `${index === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`;
        }).join(" ");

        const areaPath = key => {
            const baseY = padding.top + innerHeight;
            const line = history.map((point, index) => {
                const x = padding.left + step * index;
                const y = valueToY(point[key]);
                return `${index === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`;
            }).join(" ");
            const lastX = padding.left + step * Math.max(history.length - 1, 0);
            return `${line} L${lastX.toFixed(1)},${baseY.toFixed(1)} L${padding.left.toFixed(1)},${baseY.toFixed(1)} Z`;
        };

        const rxPath = pointsToPath("rxRateBps");
        const txPath = pointsToPath("txRateBps");
        const rxAreaPath = areaPath("rxRateBps");
        const txAreaPath = areaPath("txRateBps");
        const lastTime = history[history.length - 1] && history[history.length - 1].time ? formatDateTime(history[history.length - 1].time) : "-";
        const gridSteps = 5;
        const gridLines = Array.from({ length: gridSteps + 1 }, (_, index) => {
            const ratio = index / gridSteps;
            const y = padding.top + innerHeight * ratio;
            const value = Math.round((maxValue * (1 - ratio)) / 1000000);
            return `
                <line x1="${padding.left}" y1="${y.toFixed(1)}" x2="${width - padding.right}" y2="${y.toFixed(1)}" stroke="rgba(147,130,111,0.18)" stroke-width="1"></line>
                <text x="${padding.left - 8}" y="${(y + 4).toFixed(1)}" text-anchor="end" fill="#aa9c8a" font-size="10">${Math.max(value, 0)}</text>
            `;
        }).join("");

        return `
            <svg viewBox="0 0 ${width} ${height}" width="100%" height="280" role="img" aria-label="Traffic chart">
                <defs>
                    <linearGradient id="mkFlowPanelGradient" x1="0" x2="1" y1="0" y2="1">
                        <stop offset="0%" stop-color="#1f242c"></stop>
                        <stop offset="100%" stop-color="#161b23"></stop>
                    </linearGradient>
                    <linearGradient id="mkFlowRxGradient" x1="0" x2="0" y1="0" y2="1">
                        <stop offset="0%" stop-color="rgba(97, 209, 228, 0.34)"></stop>
                        <stop offset="100%" stop-color="rgba(97, 209, 228, 0.04)"></stop>
                    </linearGradient>
                    <linearGradient id="mkFlowTxGradient" x1="0" x2="0" y1="0" y2="1">
                        <stop offset="0%" stop-color="rgba(209, 128, 69, 0.27)"></stop>
                        <stop offset="100%" stop-color="rgba(209, 128, 69, 0.03)"></stop>
                    </linearGradient>
                    <filter id="mkFlowRxGlow" x="-40%" y="-40%" width="180%" height="180%">
                        <feGaussianBlur stdDeviation="3" result="blur"></feGaussianBlur>
                        <feMerge>
                            <feMergeNode in="blur"></feMergeNode>
                            <feMergeNode in="SourceGraphic"></feMergeNode>
                        </feMerge>
                    </filter>
                    <filter id="mkFlowTxGlow" x="-40%" y="-40%" width="180%" height="180%">
                        <feGaussianBlur stdDeviation="2.4" result="blur"></feGaussianBlur>
                        <feMerge>
                            <feMergeNode in="blur"></feMergeNode>
                            <feMergeNode in="SourceGraphic"></feMergeNode>
                        </feMerge>
                    </filter>
                    <clipPath id="mkFlowClip">
                        <rect x="${padding.left}" y="${padding.top}" width="${innerWidth}" height="${innerHeight}" rx="18"></rect>
                    </clipPath>
                </defs>
                <rect x="1" y="1" width="${width - 2}" height="${height - 2}" rx="18" fill="url(#mkFlowPanelGradient)" stroke="rgba(94,88,80,0.82)"></rect>
                <line x1="18" y1="1.5" x2="${width - 18}" y2="1.5" stroke="#d18045" stroke-width="2" stroke-linecap="round"></line>
                <text x="18" y="24" fill="#e9dfd2" font-size="13" font-family="Orbitron, 'Segoe UI', sans-serif" letter-spacing="0.8">REAL-TIME TRAFFIC FLOW (TX/RX)</text>
                <g>
                    <rect x="${width - 138}" y="14" width="10" height="10" rx="3" fill="#65d2e5" filter="url(#mkFlowRxGlow)"></rect>
                    <text x="${width - 120}" y="23" fill="#c8b7a2" font-size="10">RX</text>
                    <rect x="${width - 84}" y="14" width="10" height="10" rx="3" fill="#d18045" filter="url(#mkFlowTxGlow)"></rect>
                    <text x="${width - 66}" y="23" fill="#c8b7a2" font-size="10">TX</text>
                </g>
                ${gridLines}
                <g clip-path="url(#mkFlowClip)">
                    <path d="${rxAreaPath}" fill="url(#mkFlowRxGradient)"></path>
                    <path d="${txAreaPath}" fill="url(#mkFlowTxGradient)"></path>
                    <path d="${rxPath}" fill="none" stroke="#65d2e5" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round" filter="url(#mkFlowRxGlow)"></path>
                    <path d="${txPath}" fill="none" stroke="#d18045" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" filter="url(#mkFlowTxGlow)"></path>
                </g>
                <text x="${width - padding.right}" y="${height - 8}" text-anchor="end" fill="#9e8f7d" font-size="10">${lastTime}</text>
            </svg>
        `;
    }

    function buildTrafficChart(history, options) {
        if (!history || !history.length) {
            return '<div class="mk-monitor-state">Histori trafik belum cukup untuk digambar.</div>';
        }
        if (options && options.theme === "realtime-flow") {
            return buildRealtimeTrafficChart(history);
        }
        const width = 640;
        const height = 220;
        const padding = 18;
        const values = history.flatMap(point => [Number(point.rxRateBps || 0), Number(point.txRateBps || 0)]);
        const maxValue = Math.max(...values, 1);
        const step = history.length > 1 ? (width - padding * 2) / (history.length - 1) : 0;

        const pointsToPath = key => history.map((point, index) => {
            const x = padding + step * index;
            const y = height - padding - ((Number(point[key] || 0) / maxValue) * (height - padding * 2));
            return `${index === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`;
        }).join(" ");

        const rxPath = pointsToPath("rxRateBps");
        const txPath = pointsToPath("txRateBps");
        const lastTime = history[history.length - 1] && history[history.length - 1].time ? formatDateTime(history[history.length - 1].time) : "-";

        return `
            <svg viewBox="0 0 ${width} ${height}" width="100%" height="220" role="img" aria-label="Traffic chart">
                <defs>
                    <linearGradient id="mkRxGradient" x1="0" x2="0" y1="0" y2="1">
                        <stop offset="0%" stop-color="rgba(96,209,228,0.28)"></stop>
                        <stop offset="100%" stop-color="rgba(96,209,228,0.02)"></stop>
                    </linearGradient>
                    <linearGradient id="mkTxGradient" x1="0" x2="0" y1="0" y2="1">
                        <stop offset="0%" stop-color="rgba(209,128,69,0.26)"></stop>
                        <stop offset="100%" stop-color="rgba(209,128,69,0.02)"></stop>
                    </linearGradient>
                </defs>
                <rect x="0" y="0" width="${width}" height="${height}" rx="18" fill="rgba(21,26,34,0.86)"></rect>
                <line x1="${padding}" y1="${height - padding}" x2="${width - padding}" y2="${height - padding}" stroke="rgba(131,118,103,0.38)" stroke-width="1"></line>
                <line x1="${padding}" y1="${padding}" x2="${padding}" y2="${height - padding}" stroke="rgba(131,118,103,0.38)" stroke-width="1"></line>
                <path d="${rxPath}" fill="none" stroke="#65d2e5" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"></path>
                <path d="${txPath}" fill="none" stroke="#d18045" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"></path>
                <text x="${width - padding}" y="${padding + 4}" text-anchor="end" fill="#9f907d" font-size="11">${formatBps(maxValue)}</text>
                <text x="${width - padding}" y="${height - 6}" text-anchor="end" fill="#9f907d" font-size="11">${lastTime}</text>
            </svg>
        `;
    }

    function escapeHtml(value) {
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;");
    }

    function scheduleSummary(ms) {
        window.clearTimeout(state.summaryTimer);
        state.summaryTimer = window.setTimeout(() => {
            loadSummary().catch(handleError);
        }, Math.max(3000, Number(ms || 8000)));
    }

    async function loadRouters() {
        const payload = await request("/api/monitoring/mikrotik/routers");
        renderRouterFilter(payload);
    }

    async function loadSummary() {
        if (state.summaryAbortController) {
            state.summaryAbortController.abort();
        }
        state.summaryAbortController = new AbortController();
        const routerIdQuery = state.selectedRouterId ? `?routerId=${encodeURIComponent(state.selectedRouterId)}` : "";
        byId("mkPppoeBody").innerHTML = '<tr><td colspan="5" class="mk-monitor-empty">Memuat PPPoE aktif...</td></tr>';
        byId("mkLogList").innerHTML = '<div class="mk-monitor-state">Memuat latest logs...</div>';
        state.latestPppoe = [];
        state.latestLogs = [];
        const summary = await request(`/api/monitoring/mikrotik/summary${routerIdQuery}`, { signal: state.summaryAbortController.signal });
        renderSummary(summary);
        await Promise.allSettled([
            loadPppoeSnapshot(),
            loadLogSnapshot()
        ]);
        scheduleSummary(summary.polling && summary.polling.summaryIntervalMs);
    }

    async function loadPppoeSnapshot() {
        if (state.pppoeAbortController) {
            state.pppoeAbortController.abort();
        }
        state.pppoeAbortController = new AbortController();
        const routerIdQuery = state.selectedRouterId ? `?routerId=${encodeURIComponent(state.selectedRouterId)}` : "";
        const payload = await request(`/api/monitoring/mikrotik/pppoe${routerIdQuery}`, { signal: state.pppoeAbortController.signal });
        renderPppoe(payload.items || []);
    }

    async function loadLogSnapshot() {
        if (state.logsAbortController) {
            state.logsAbortController.abort();
        }
        state.logsAbortController = new AbortController();
        const routerIdQuery = state.selectedRouterId ? `?routerId=${encodeURIComponent(state.selectedRouterId)}` : "";
        const payload = await request(`/api/monitoring/mikrotik/logs${routerIdQuery}`, { signal: state.logsAbortController.signal });
        renderLogs(payload.items || []);
    }

    function openModal(username) {
        state.modalUser = username;
        byId("mkPppoeModal").classList.add("open");
        byId("mkModalTitle").textContent = `Traffic PPPoE ${decodeURIComponent(username)}`;
        byId("mkModalMeta").textContent = "Polling otomatis berhenti saat modal ditutup.";
        loadModalTraffic().catch(handleError);
    }

    function openPppoeListModal() {
        byId("mkPppoeListModal").classList.add("open");
    }

    function closePppoeListModal() {
        byId("mkPppoeListModal").classList.remove("open");
    }

    function openLogsModal() {
        byId("mkLogsModal").classList.add("open");
    }

    function closeLogsModal() {
        byId("mkLogsModal").classList.remove("open");
    }

    function closeModal() {
        state.modalUser = null;
        window.clearTimeout(state.modalTimer);
        if (state.modalAbortController) {
            state.modalAbortController.abort();
        }
        byId("mkPppoeModal").classList.remove("open");
    }

    function scheduleModal(ms) {
        window.clearTimeout(state.modalTimer);
        state.modalTimer = window.setTimeout(() => {
            if (state.modalUser) {
                loadModalTraffic().catch(handleError);
            }
        }, Math.max(4000, Number(ms || 8000)));
    }

    async function loadModalTraffic() {
        if (!state.modalUser) {
            return;
        }
        if (state.modalAbortController) {
            state.modalAbortController.abort();
        }
        state.modalAbortController = new AbortController();
        const routerIdQuery = state.selectedRouterId ? `?routerId=${encodeURIComponent(state.selectedRouterId)}` : "";
        const payload = await request(`/api/monitoring/mikrotik/pppoe/${state.modalUser}/traffic${routerIdQuery}`, { signal: state.modalAbortController.signal });
        const chartShell = byId("mkModalChartShell");
        const stateBox = byId("mkModalState");
        if (!payload.history || !payload.history.length) {
            chartShell.classList.add("mk-monitor-hidden");
            stateBox.classList.remove("mk-monitor-hidden");
            stateBox.textContent = "Histori user belum cukup. Biarkan modal terbuka beberapa detik agar ring buffer terisi.";
        } else {
            chartShell.classList.remove("mk-monitor-hidden");
            stateBox.classList.add("mk-monitor-hidden");
            byId("mkModalStatus").textContent = payload.online ? "Online" : "Offline";
            byId("mkModalLastSeen").textContent = formatDateTime(payload.lastSeenAt);
            byId("mkModalInterface").textContent = payload.interfaceName || "-";
            byId("mkModalChart").innerHTML = buildTrafficChart(payload.history);
        }
        scheduleModal(state.latestSummary && state.latestSummary.polling && state.latestSummary.polling.summaryIntervalMs);
    }

    function handleError(error) {
        if (error && error.name === "AbortError") {
            return;
        }
        if (window.nmxNotify) {
            window.nmxNotify({ type: "error", message: error.message || "Monitoring gagal dimuat." });
        } else {
            console.error(error);
        }
    }

    function bindEvents() {
        byId("mkRefreshButton").addEventListener("click", () => {
            loadSummary().catch(handleError);
        });
        byId("mkPppoeCard").addEventListener("click", openPppoeListModal);
        byId("mkLogsCard").addEventListener("click", openLogsModal);
        byId("mkRouterFilter").addEventListener("change", event => {
            state.selectedRouterId = event.target.value ? Number(event.target.value) : null;
            state.pppoeSearch = "";
            byId("mkPppoeSearch").value = "";
            closeModal();
            closePppoeListModal();
            closeLogsModal();
            loadSummary().catch(handleError);
        });
        byId("mkPppoeSearch").addEventListener("input", event => {
            state.pppoeSearch = event.target.value || "";
            renderPppoe(state.latestPppoe || []);
        });
        byId("mkPppoeBody").addEventListener("click", event => {
            const button = event.target.closest("[data-pppoe-user]");
            if (!button) {
                return;
            }
            openModal(button.getAttribute("data-pppoe-user"));
        });
        byId("mkPppoeListModalClose").addEventListener("click", closePppoeListModal);
        byId("mkLogsModalClose").addEventListener("click", closeLogsModal);
        byId("mkModalClose").addEventListener("click", closeModal);
        byId("mkPppoeListModal").addEventListener("click", event => {
            if (event.target === byId("mkPppoeListModal")) {
                closePppoeListModal();
            }
        });
        byId("mkLogsModal").addEventListener("click", event => {
            if (event.target === byId("mkLogsModal")) {
                closeLogsModal();
            }
        });
        byId("mkPppoeModal").addEventListener("click", event => {
            if (event.target === byId("mkPppoeModal")) {
                closeModal();
            }
        });
        document.addEventListener("keydown", event => {
            if (event.key === "Escape") {
                closeModal();
                closePppoeListModal();
                closeLogsModal();
            }
        });
    }

    document.addEventListener("DOMContentLoaded", async () => {
        bindEvents();
        try {
            await loadRouters();
            await loadSummary();
        } catch (error) {
            handleError(error);
        }
    });
})();
