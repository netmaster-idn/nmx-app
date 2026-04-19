(function () {
    const el = (id) => document.getElementById(id);
    const val = (v, f = '-') => (v === null || v === undefined || v === '' ? f : v);
    const esc = (v) => String(v ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
    const acsPageState = {
        selectedDevice: null,
        activeTab: 'overview',
        previewClientLimit: 3
    };

    function dt(value) {
        if (!value) return '-';
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('id-ID');
    }

    function boolText(value) {
        return value === true ? 'Enabled' : value === false ? 'Disabled' : '-';
    }

    function metric(value, suffix) {
        return value === null || value === undefined || value === '' ? '-' : `${value}${suffix || ''}`;
    }

    function badge(value) {
        if (!value) return '-';
        return `<span class="acs-badge ${esc(String(value).toLowerCase())}">${esc(value)}</span>`;
    }

    function statusPill(text, tone) {
        return `<span class="acs-soft-pill ${esc(tone || 'gray')}">${esc(text)}</span>`;
    }

    function readLiveDevices() {
        return window.acsState && Array.isArray(window.acsState.liveDevices) ? window.acsState.liveDevices : [];
    }

    function getSelectedDevice() {
        return acsPageState.selectedDevice || (window.acsState ? window.acsState.selectedDevice : null);
    }

    function setSelectedDevice(device) {
        acsPageState.selectedDevice = device || null;
        if (window.acsState) {
            window.acsState.selectedDevice = device || null;
        }
    }

    function replaceLiveDevice(updated) {
        if (!updated || !updated.serialNumber || !window.acsState || !Array.isArray(window.acsState.liveDevices)) {
            return;
        }
        const index = window.acsState.liveDevices.findIndex((item) => item.serialNumber === updated.serialNumber);
        if (index >= 0) {
            window.acsState.liveDevices[index] = { ...window.acsState.liveDevices[index], ...updated };
        }
        if (typeof window.renderAcsDeviceTable === 'function') {
            window.renderAcsDeviceTable();
        }
    }

    function openOverlay(id) {
        if (el(id)) el(id).classList.add('open');
    }

    function closeOverlay(id) {
        if (el(id)) el(id).classList.remove('open');
    }

    function closeAcsDetailModal() { closeOverlay('acsDetailModal'); }
    function closeWanEditorModal() { closeOverlay('acsWanEditorModal'); }
    function closeSsidEditorModal() { closeOverlay('acsSsidEditorModal'); }
    function closeAcsPeekModal() { closeOverlay('acsPeekModal'); }

    function openAcsDetailModal() {
        if (window.nmxLoading && typeof window.nmxLoading.hide === 'function') {
            window.nmxLoading.hide(true);
        }
        openOverlay('acsDetailModal');
    }

    function bindModalBackdrop(id, closer) {
        const node = el(id);
        if (!node) return;
        node.addEventListener('click', function (event) {
            if (event.target === this) closer();
        });
    }

    function mountOverlaysToBody() {
        ['acsDetailModal', 'acsWanEditorModal', 'acsSsidEditorModal', 'acsPeekModal', 'acsSettingsModal'].forEach((id) => {
            const node = el(id);
            if (node && node.parentElement !== document.body) {
                document.body.appendChild(node);
            }
        });
    }

    function setActiveTab(tab) {
        acsPageState.activeTab = tab;
        if (!el('acsDetailTabs')) return;
        document.querySelectorAll('#acsDetailTabs .acs-tab-btn').forEach((button) => {
            button.classList.toggle('active', button.dataset.tab === tab);
        });
        document.querySelectorAll('.acs-tab-panel').forEach((panel) => {
            panel.classList.toggle('active', panel.dataset.panel === tab);
        });
    }

    function sortSsids(list) {
        return [...list].sort((left, right) => {
            const leftEnabled = left.enable === true ? 1 : 0;
            const rightEnabled = right.enable === true ? 1 : 0;
            if (leftEnabled !== rightEnabled) return rightEnabled - leftEnabled;
            return Number(left.instance || 0) - Number(right.instance || 0);
        });
    }

    function summarizeClients(list) {
        const total = list.length;
        return {
            preview: list.slice(0, acsPageState.previewClientLimit),
            hiddenCount: Math.max(0, total - acsPageState.previewClientLimit),
            total
        };
    }

    function classifyService(service, index) {
        const type = String(service.connectionType || '').toUpperCase();
        const serviceList = String(service.serviceList || '').toUpperCase();
        if (serviceList.includes('TR069')) {
            return { title: 'TR069', summary: service.connectionName || 'ACS Management' };
        }
        if (type === 'PPP') {
            return { title: 'PPPoE', summary: service.username || service.connectionName || 'PPPoE Service' };
        }
        if (type === 'IP') {
            return { title: 'IP Routed', summary: service.ipAddress || service.connectionName || 'IP Routed Service' };
        }
        return { title: `WAN ${index + 1}`, summary: service.connectionName || service.ipAddress || 'WAN Service' };
    }

    function getWanByInstance(instance) {
        const device = getSelectedDevice();
        return ((device && device.wanServices) || []).find((item) => String(item.instance) === String(instance)) || null;
    }

    function getSsidByInstance(instance) {
        const device = getSelectedDevice();
        return ((device && device.ssidList) || []).find((item) => String(item.instance) === String(instance)) || null;
    }

    function primaryOverviewRows(device, info) {
        return [
            ['Ping Response', val(info.ping, 'Belum terbaca')],
            ['Device Uptime', val(info.deviceUptime, 'Belum terbaca')],
            ['PPP Uptime', val(info.pppUptime, 'Belum terbaca')],
            ['Manufacturer', val(info.manufacturer, val(device && device.vendor))],
            ['Device Type', val(info.deviceType, val(device && device.model))],
            ['SN ONT', val(device && device.serialNumber)],
            ['MAC Address', val(info.macAddress)],
            ['Software Ver', val(info.softwareVersion)],
            ['IP Address', val(device && device.ipAddress)],
            ['PPPoE Username', val(device && device.pppoeUsername, 'Belum termapping')]
        ];
    }

    function renderOverview(device) {
        const info = device && device.deviceInfo ? device.deviceInfo : {};
        if (el('acsDetailModalTitle')) {
            el('acsDetailModalTitle').textContent = device ? `ACS Terminal Management - ${val(device.serialNumber)}` : 'ACS Terminal Management';
        }
        if (el('acsDetailNotice')) {
            el('acsDetailNotice').textContent = device
                ? 'Monitoring & Configuration for ONT Device'
                : 'Pilih device dari tabel untuk melihat detail ACS live.';
        }
        if (el('acsDetailStatus')) {
            el('acsDetailStatus').innerHTML = device && device.status ? badge(device.status) : '-';
        }
        if (el('acsDetailLastInformBadge')) {
            el('acsDetailLastInformBadge').textContent = `Last inform ${dt(device && device.lastInform)}`;
        }
        if (el('acsDetailRegionPill')) {
            el('acsDetailRegionPill').textContent = `Wilayah ${val(device && device.regionName, '-')}`;
        }
        if (el('acsDetailCustomerPill')) {
            el('acsDetailCustomerPill').textContent = `Pelanggan ${val(device && device.customerName, '-')}`;
        }
        if (el('acsDeviceInfoList')) {
            el('acsDeviceInfoList').innerHTML = primaryOverviewRows(device, info).map(([label, value]) => `
                <div class="acs-info-row">
                    <label>${esc(label)}</label>
                    <strong>${esc(value)}</strong>
                </div>
            `).join('');
        }
        if (el('acsMetricRx')) {
            el('acsMetricRx').innerHTML = `${esc(metric(info.opticRxPower, '-'))}<small> dBm</small>`;
        }
        if (el('acsMetricTemp')) {
            el('acsMetricTemp').innerHTML = `${esc(metric(info.temperature, '-'))}<small> C</small>`;
        }
    }

    function buildServiceDetailRows(service) {
        const rows = [
            ['Enable', boolText(service.enable)],
            ['Connection Name', val(service.connectionName)],
            ['Connection Mode', val(service.connectionMode)],
            ['Service List', val(service.serviceList)],
            ['IP Address', val(service.ipAddress)],
            ['NAT', boolText(service.nat)],
            ['VLAN Enable', boolText(service.vlanEnabled)],
            ['VLAN ID', val(service.vlanId)],
            ['LAN Bind', val(service.lanBind)]
        ];
        if (String(service.connectionType || '').toUpperCase() === 'PPP') {
            rows.push(['Username', val(service.username)]);
            rows.push(['Password', val(service.password, 'Tersimpan')]);
        } else {
            rows.push(['DHCP / Static', val(service.ipMode)]);
            rows.push(['Gateway', val(service.gateway)]);
            rows.push(['Mask', val(service.subnetMask)]);
            rows.push(['DNS', val(service.dnsServers)]);
        }
        return rows;
    }

    function openPeekModal(title, rows) {
        if (el('acsPeekModalTitle')) el('acsPeekModalTitle').textContent = title;
        if (el('acsPeekModalContent')) {
            el('acsPeekModalContent').innerHTML = `
                <div class="acs-detail-view-grid">
                    ${rows.map(([label, value]) => `
                        <div class="acs-secondary-item">
                            <label>${esc(label)}</label>
                            <span>${esc(val(value))}</span>
                        </div>
                    `).join('')}
                </div>
            `;
        }
        openOverlay('acsPeekModal');
    }

    function renderWanServices(list) {
        const container = el('acsWanServiceList');
        if (!container) return;
        if (!list.length) {
            container.innerHTML = '<div class="acs-empty-panel">Belum ada service network yang terbaca dari ACS.</div>';
            return;
        }

        container.innerHTML = `
            <div class="acs-wan-table-wrap">
                <table class="acs-wan-table">
                    <thead>
                        <tr>
                            <th>Mode</th>
                            <th>Connection Name</th>
                            <th>IP/User</th>
                            <th>VLAN</th>
                            <th>Status</th>
                            <th>Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${list.map((service, index) => {
                            const kind = classifyService(service, index);
                            const enabled = service.enable === true;
                            const primary = String(service.connectionType || '').toUpperCase() === 'PPP'
                                ? val(service.username)
                                : val(service.ipAddress);
                            return `
                                <tr>
                                    <td>${statusPill(kind.title, 'gray')}</td>
                                    <td>${esc(val(service.connectionName, kind.summary))}</td>
                                    <td>${esc(primary)}</td>
                                    <td>${esc(val(service.vlanId, '-'))}</td>
                                    <td>${statusPill(enabled ? 'Enabled' : 'Disabled', enabled ? 'green' : 'gray')}</td>
                                    <td>
                                        <div class="acs-wan-actions">
                                            <button class="acs-icon-btn" type="button" data-network-action="view" data-network-instance="${esc(val(service.instance, ''))}" title="View Detail">
                                                <i class="fas fa-eye"></i>
                                            </button>
                                            <button class="acs-icon-btn" type="button" data-network-action="edit" data-network-instance="${esc(val(service.instance, ''))}" title="Edit WAN">
                                                <i class="fas fa-pen"></i>
                                            </button>
                                            ${enabled ? '' : `
                                                <button class="acs-icon-btn" type="button" data-network-action="enable" data-network-instance="${esc(val(service.instance, ''))}" title="Enable WAN">
                                                    <i class="fas fa-play"></i>
                                                </button>
                                            `}
                                        </div>
                                    </td>
                                </tr>
                            `;
                        }).join('')}
                    </tbody>
                </table>
            </div>
        `;
    }

    function renderSsidList(list) {
        const container = el('acsSsidList');
        if (!container) return;
        if (!list.length) {
            container.innerHTML = '<div class="acs-empty-panel">SSID belum terbaca dari ACS.</div>';
            if (el('acsSsidSummaryTitle')) el('acsSsidSummaryTitle').textContent = 'Active SSIDs';
            return;
        }

        const sorted = sortSsids(list);
        const activeCount = sorted.filter((item) => item.enable === true).length;
        if (el('acsSsidSummaryTitle')) {
            el('acsSsidSummaryTitle').textContent = `Active SSIDs (${activeCount}/${sorted.length})`;
        }

        container.innerHTML = sorted.map((ssid) => {
            const enabled = ssid.enable === true;
            return `
                <div class="acs-ssid-card">
                    <div class="acs-ssid-line">
                        <div>
                            <strong>${esc(val(ssid.ssid, `SSID ${val(ssid.instance)}`))}</strong>
                            <div class="acs-note">${esc(val(ssid.security, 'Unknown'))}</div>
                        </div>
                        ${statusPill(enabled ? 'Enabled' : 'Disabled', enabled ? 'green' : 'gray')}
                    </div>
                    <div class="acs-inline-actions">
                        <button class="acs-text-btn ${enabled ? '' : 'success'}" type="button" data-ssid-action="toggle" data-ssid-instance="${esc(val(ssid.instance, ''))}">
                            ${enabled ? 'Disable' : 'Enable'}
                        </button>
                        <button class="acs-text-btn primary" type="button" data-ssid-action="edit" data-ssid-instance="${esc(val(ssid.instance, ''))}">
                            Edit SSID
                        </button>
                    </div>
                </div>
            `;
        }).join('');
    }

    function renderWifiClients(list) {
        const container = el('acsWifiClientList');
        if (!container) return;
        const summary = summarizeClients(list);
        if (el('acsConnectedTitle')) {
            el('acsConnectedTitle').textContent = `Connected Clients (${summary.total})`;
        }
        if (el('acsViewAllClientsBtn')) {
            el('acsViewAllClientsBtn').style.display = summary.total > summary.preview.length ? '' : 'none';
        }
        if (!summary.total) {
            container.innerHTML = '<div class="acs-empty-panel">Belum ada client WiFi yang terhubung pada SSID1.</div>';
            return;
        }

        const previewMarkup = summary.preview.map((client) => `
            <div class="acs-client-card acs-client-item-v2">
                <div>
                    <strong>${esc(val(client.hostName, 'Unknown Device'))}</strong>
                    <small>${esc(val(client.ipAddress))} | ${esc(val(client.widthFreq, '-'))}</small>
                </div>
                <div class="acs-pill-row">
                    ${client.rssi ? statusPill(client.rssi, 'green') : statusPill('N/A', 'gray')}
                </div>
            </div>
        `).join('');

        const extraMarkup = summary.hiddenCount > 0
            ? `<button class="acs-text-btn" type="button" id="acsClientsMoreInline">+${summary.hiddenCount} more</button>`
            : '';

        container.innerHTML = previewMarkup + extraMarkup;
        if (el('acsClientsMoreInline')) {
            el('acsClientsMoreInline').addEventListener('click', openAllClientsModal);
        }
    }
    function fillAcsDetailModal(device) {
        setSelectedDevice(device || null);
        renderOverview(device || null);
        renderWanServices(device && Array.isArray(device.wanServices) ? device.wanServices : []);
        renderSsidList(device && Array.isArray(device.ssidList) ? device.ssidList : []);
        renderWifiClients(device && Array.isArray(device.wifiClients) ? device.wifiClients : []);
    }

    function openWanEditorModal(service) {
        if (!el('acsWanEditorModal')) return;
        el('acsWanEditorTitle').textContent = service ? 'Edit WAN Service' : 'Tambah WAN Service';
        el('acsWanMode').value = service ? 'edit' : 'create';
        el('acsWanInstance').value = service && service.instance ? service.instance : '';
        el('acsWanConnectionType').value = service && service.connectionType ? service.connectionType : 'PPP';
        el('acsWanConnectionName').value = service && service.connectionName ? service.connectionName : '';
        el('acsWanConnectionMode').value = service && service.connectionMode ? service.connectionMode : '';
        el('acsWanServiceListField').value = service && service.serviceList ? service.serviceList : '';
        el('acsWanIpAddress').value = service && service.ipAddress ? service.ipAddress : '';
        el('acsWanUsername').value = service && service.username ? service.username : '';
        el('acsWanPassword').value = service && service.password ? service.password : '';
        el('acsWanIpMode').value = service && service.ipMode ? service.ipMode : 'DHCP';
        el('acsWanGateway').value = service && service.gateway ? service.gateway : '';
        el('acsWanSubnetMask').value = service && service.subnetMask ? service.subnetMask : '';
        el('acsWanDnsServers').value = service && service.dnsServers ? service.dnsServers : '';
        el('acsWanVlanId').value = service && service.vlanId ? service.vlanId : '';
        el('acsWanLanBind').value = service && service.lanBind ? service.lanBind : '';
        el('acsWanEnable').checked = service ? Boolean(service.enable) : true;
        el('acsWanNat').checked = service ? Boolean(service.nat) : true;
        el('acsWanVlanEnable').checked = service ? Boolean(service.vlanEnabled) : false;
        toggleWanFieldGroups();
        openOverlay('acsWanEditorModal');
    }

    function openSsidEditorModal(ssid) {
        if (!el('acsSsidEditorModal')) return;
        el('acsSsidEditorTitle').textContent = ssid ? `Edit SSID ${ssid.instance ? '#' + ssid.instance : ''}` : 'Edit SSID';
        el('acsSsidInstance').value = ssid && ssid.instance ? ssid.instance : '';
        el('acsSsidNameInput').value = ssid && ssid.ssid ? ssid.ssid : '';
        el('acsSsidSecurityInput').value = ssid && ssid.security ? ssid.security : 'WPA2-PSK';
        el('acsSsidPasswordInput').value = ssid && ssid.password ? ssid.password : '';
        el('acsSsidPowerInput').value = ssid && ssid.powerPercent ? ssid.powerPercent : '';
        el('acsSsidChannelInput').value = ssid && ssid.channel ? ssid.channel : '';
        el('acsSsidEnableInput').checked = ssid ? Boolean(ssid.enable) : true;
        el('acsSsidAutoChannelInput').checked = ssid ? Boolean(ssid.autoChannel) : true;
        openOverlay('acsSsidEditorModal');
    }

    function toggleWanFieldGroups() {
        const type = el('acsWanConnectionType') ? el('acsWanConnectionType').value : 'PPP';
        document.querySelectorAll('.wan-ppp-only').forEach((node) => {
            node.style.display = type === 'PPP' ? '' : 'none';
        });
        document.querySelectorAll('.wan-ip-only').forEach((node) => {
            node.style.display = type === 'IP' ? '' : 'none';
        });
    }

    async function openAcsDetailModalBySerial(serialNumber) {
        if (!serialNumber) {
            return window.showAcsFeedback ? window.showAcsFeedback('error', 'Serial ONT belum tersedia.') : null;
        }
        const localDevice = readLiveDevices().find((item) => item.serialNumber === serialNumber) || { serialNumber };
        fillAcsDetailModal(localDevice);
        if (el('acsDetailNotice')) el('acsDetailNotice').textContent = 'Memuat detail ACS terbaru...';
        openAcsDetailModal();
        try {
            const detail = await window.requestAcsJsonWithTimeout(`/api/network/acs/devices/${encodeURIComponent(serialNumber)}/detail`);
            replaceLiveDevice(detail);
            fillAcsDetailModal(detail);
            setActiveTab(acsPageState.activeTab || 'overview');
        } catch (error) {
            if (window.showAcsFeedback) window.showAcsFeedback('error', error.message);
            if (el('acsDetailNotice')) {
                el('acsDetailNotice').textContent = 'Detail terbaru dari ACS belum berhasil dimuat. Menampilkan data terakhir yang tersedia.';
            }
        }
    }

    async function refreshSelectedAcsDetail() {
        const device = getSelectedDevice();
        if (!device || !device.serialNumber) {
            return window.showAcsFeedback ? window.showAcsFeedback('info', 'Belum ada device detail yang aktif.') : null;
        }
        await openAcsDetailModalBySerial(device.serialNumber);
    }

    async function saveWanEditor(event) {
        event.preventDefault();
        const device = getSelectedDevice();
        if (!device || !device.serialNumber) {
            return window.showAcsFeedback ? window.showAcsFeedback('error', 'Device ACS belum dipilih.') : null;
        }
        try {
            const payload = {
                mode: el('acsWanMode').value,
                instance: el('acsWanInstance').value.trim(),
                connectionType: el('acsWanConnectionType').value,
                enable: el('acsWanEnable').checked,
                connectionName: el('acsWanConnectionName').value.trim(),
                connectionMode: el('acsWanConnectionMode').value.trim(),
                serviceList: el('acsWanServiceListField').value.trim(),
                ipAddress: el('acsWanIpAddress').value.trim(),
                username: el('acsWanUsername').value.trim(),
                password: el('acsWanPassword').value.trim(),
                nat: el('acsWanNat').checked,
                vlanEnabled: el('acsWanVlanEnable').checked,
                vlanId: el('acsWanVlanId').value.trim(),
                lanBind: el('acsWanLanBind').value.trim(),
                ipMode: el('acsWanIpMode').value,
                gateway: el('acsWanGateway').value.trim(),
                subnetMask: el('acsWanSubnetMask').value.trim(),
                dnsServers: el('acsWanDnsServers').value.trim()
            };
            const updated = await window.requestAcsJson(`/api/network/acs/devices/${encodeURIComponent(device.serialNumber)}/wan`, {
                method: 'PUT',
                headers: window.acsHeaders(true),
                body: JSON.stringify(payload)
            });
            if (typeof window.clearAcsDashboardCache === 'function') window.clearAcsDashboardCache();
            replaceLiveDevice(updated);
            fillAcsDetailModal(updated);
            closeWanEditorModal();
            if (window.showAcsFeedback) window.showAcsFeedback('success', 'Perubahan WAN service berhasil dikirim ke ACS.');
        } catch (error) {
            if (window.showAcsFeedback) window.showAcsFeedback('error', error.message);
        }
    }

    async function saveSsidEditor(event) {
        event.preventDefault();
        const device = getSelectedDevice();
        if (!device || !device.serialNumber) {
            return window.showAcsFeedback ? window.showAcsFeedback('error', 'Device ACS belum dipilih.') : null;
        }
        try {
            const payload = {
                instance: el('acsSsidInstance').value.trim(),
                enable: el('acsSsidEnableInput').checked,
                ssid: el('acsSsidNameInput').value.trim(),
                security: el('acsSsidSecurityInput').value,
                password: el('acsSsidPasswordInput').value.trim(),
                powerPercent: el('acsSsidPowerInput').value.trim(),
                autoChannel: el('acsSsidAutoChannelInput').checked,
                channel: el('acsSsidChannelInput').value.trim()
            };
            const updated = await window.requestAcsJson(`/api/network/acs/devices/${encodeURIComponent(device.serialNumber)}/ssid`, {
                method: 'PUT',
                headers: window.acsHeaders(true),
                body: JSON.stringify(payload)
            });
            if (typeof window.clearAcsDashboardCache === 'function') window.clearAcsDashboardCache();
            replaceLiveDevice(updated);
            fillAcsDetailModal(updated);
            closeSsidEditorModal();
            if (window.showAcsFeedback) window.showAcsFeedback('success', 'Perubahan SSID berhasil dikirim ke ACS.');
        } catch (error) {
            if (window.showAcsFeedback) window.showAcsFeedback('error', error.message);
        }
    }

    async function toggleSsidState(instance) {
        const device = getSelectedDevice();
        const ssid = getSsidByInstance(instance);
        if (!device || !ssid) return;
        try {
            const updated = await window.requestAcsJson(`/api/network/acs/devices/${encodeURIComponent(device.serialNumber)}/ssid`, {
                method: 'PUT',
                headers: window.acsHeaders(true),
                body: JSON.stringify({
                    instance: ssid.instance,
                    enable: !ssid.enable,
                    ssid: ssid.ssid || '',
                    security: ssid.security || 'WPA2-PSK',
                    password: ssid.password || '',
                    powerPercent: ssid.powerPercent || '',
                    autoChannel: Boolean(ssid.autoChannel),
                    channel: ssid.channel || ''
                })
            });
            if (typeof window.clearAcsDashboardCache === 'function') window.clearAcsDashboardCache();
            replaceLiveDevice(updated);
            fillAcsDetailModal(updated);
            if (window.showAcsFeedback) {
                window.showAcsFeedback('success', `SSID ${ssid.ssid || ssid.instance} berhasil ${ssid.enable ? 'dinonaktifkan' : 'diaktifkan'}.`);
            }
        } catch (error) {
            if (window.showAcsFeedback) window.showAcsFeedback('error', error.message);
        }
    }

    function openServiceDetailByInstance(instance) {
        const service = getWanByInstance(instance);
        if (!service) return;
        const kind = classifyService(service, Number(service.instance || 1) - 1);
        openPeekModal(`${kind.title} Detail`, buildServiceDetailRows(service));
    }

    function openAllClientsModal() {
        const device = getSelectedDevice();
        const clients = device && Array.isArray(device.wifiClients) ? device.wifiClients : [];
        openPeekModal('Connected Devices', clients.length
            ? clients.flatMap((client, index) => [
                [`Device ${index + 1}`, val(client.hostName, 'Unknown Device')],
                ['IP Address', val(client.ipAddress)],
                ['MAC Address', val(client.macAddress)],
                ['Width Freq', val(client.widthFreq)],
                ['RSSI', val(client.rssi)],
                ['Noise', val(client.noise)]
            ])
            : [['Status', 'Belum ada client WiFi']]
        );
    }

    async function enableWan(instance) {
        const device = getSelectedDevice();
        const service = getWanByInstance(instance);
        if (!device || !service) return;
        try {
            const updated = await window.requestAcsJson(`/api/network/acs/devices/${encodeURIComponent(device.serialNumber)}/wan`, {
                method: 'PUT',
                headers: window.acsHeaders(true),
                body: JSON.stringify({
                    mode: 'edit',
                    instance: service.instance,
                    connectionType: service.connectionType,
                    enable: true,
                    connectionName: service.connectionName || '',
                    connectionMode: service.connectionMode || '',
                    serviceList: service.serviceList || '',
                    ipAddress: service.ipAddress || '',
                    username: service.username || '',
                    password: service.password || '',
                    nat: Boolean(service.nat),
                    vlanEnabled: Boolean(service.vlanEnabled),
                    vlanId: service.vlanId || '',
                    lanBind: service.lanBind || '',
                    ipMode: service.ipMode || '',
                    gateway: service.gateway || '',
                    subnetMask: service.subnetMask || '',
                    dnsServers: service.dnsServers || ''
                })
            });
            if (typeof window.clearAcsDashboardCache === 'function') window.clearAcsDashboardCache();
            replaceLiveDevice(updated);
            fillAcsDetailModal(updated);
            if (window.showAcsFeedback) window.showAcsFeedback('success', 'WAN berhasil diaktifkan.');
        } catch (error) {
            if (window.showAcsFeedback) window.showAcsFeedback('error', error.message);
        }
    }

    function bindTabs() {
        if (!el('acsDetailTabs')) return;
        el('acsDetailTabs').addEventListener('click', function (event) {
            const button = event.target.closest('.acs-tab-btn');
            if (!button) return;
            setActiveTab(button.dataset.tab || 'overview');
        });
    }

    function bindNetworkActions() {
        if (!el('acsWanServiceList')) return;
        el('acsWanServiceList').addEventListener('click', function (event) {
            const button = event.target.closest('button[data-network-action]');
            if (!button) return;
            const instance = button.dataset.networkInstance;
            const action = button.dataset.networkAction;
            if (action === 'view') openServiceDetailByInstance(instance);
            if (action === 'edit') openWanEditorModal(getWanByInstance(instance));
            if (action === 'enable') enableWan(instance);
        });
    }

    function bindWifiActions() {
        if (el('acsSsidList')) {
            el('acsSsidList').addEventListener('click', function (event) {
                const button = event.target.closest('button[data-ssid-action]');
                if (!button) return;
                const instance = button.dataset.ssidInstance;
                if (button.dataset.ssidAction === 'edit') openSsidEditorModal(getSsidByInstance(instance));
                if (button.dataset.ssidAction === 'toggle') toggleSsidState(instance);
            });
        }
        if (el('acsViewAllClientsBtn')) {
            el('acsViewAllClientsBtn').addEventListener('click', openAllClientsModal);
        }
    }

    function init() {
        if (!el('acsDetailModal')) return;
        mountOverlaysToBody();
        fillAcsDetailModal(null);
        setActiveTab('overview');
        toggleWanFieldGroups();
        bindModalBackdrop('acsDetailModal', closeAcsDetailModal);
        bindModalBackdrop('acsWanEditorModal', closeWanEditorModal);
        bindModalBackdrop('acsSsidEditorModal', closeSsidEditorModal);
        bindModalBackdrop('acsPeekModal', closeAcsPeekModal);
        bindTabs();
        bindNetworkActions();
        bindWifiActions();
        if (el('acsWanConnectionType')) el('acsWanConnectionType').addEventListener('change', toggleWanFieldGroups);
        if (el('acsWanEditorForm')) el('acsWanEditorForm').addEventListener('submit', saveWanEditor);
        if (el('acsSsidEditorForm')) el('acsSsidEditorForm').addEventListener('submit', saveSsidEditor);
    }

    window.fillAcsDetailModal = fillAcsDetailModal;
    window.openAcsDetailModal = openAcsDetailModal;
    window.closeAcsDetailModal = closeAcsDetailModal;
    window.openAcsDetailModalBySerial = openAcsDetailModalBySerial;
    window.__acsTerminalOpenDetail = openAcsDetailModalBySerial;
    window.refreshSelectedAcsDetail = refreshSelectedAcsDetail;
    window.openWanEditorModal = openWanEditorModal;
    window.closeWanEditorModal = closeWanEditorModal;
    window.openSsidEditorModal = openSsidEditorModal;
    window.closeSsidEditorModal = closeSsidEditorModal;
    window.closeAcsPeekModal = closeAcsPeekModal;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
}());


