(function() {
    function formatDateTime(value) {
        if (!value) {
            return '-';
        }
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? value : date.toLocaleString('id-ID');
    }

    function notify(type, message) {
        if (window.nmxNotify) {
            window.nmxNotify({ type, message });
            return;
        }
        alert(message);
    }

    async function request(url, options) {
        const headers = {};
        const csrfToken = window.csrfToken || '';
        const csrfHeader = window.csrfHeader || 'X-CSRF-TOKEN';
        if (csrfToken) {
            headers[csrfHeader] = csrfToken;
        }
        if (options && options.body) {
            headers['Content-Type'] = 'application/json';
        }
        const response = await fetch(url, { ...(options || {}), headers });
        const payload = await response.json();
        if (!response.ok || payload.success === false) {
            throw new Error(payload.message || 'Permintaan gagal');
        }
        return payload.data;
    }

    const state = { devices: [], filteredDevices: [], currentId: null };
    const ids = [
        'mikrotikForm', 'mikrotikTableBody', 'formMessage', 'deviceId', 'deviceNameInput', 'siteNameInput',
        'vpnIpInput', 'monitoringIntervalInput', 'apiUsernameInput', 'apiPasswordInput',
        'resetFormButton', 'testApiButton', 'checkStatusButton', 'deviceOnlineIndicator', 'mkStatTotal', 'mkStatOnline', 'mkStatOffline', 'mkStatLastSync',
        'mikrotikEmptyState', 'devicePanel', 'closeDevicePanelButton'
    ];
    const elements = Object.fromEntries(ids.map(id => [id, document.getElementById(id)]));

    function resolveVpnEndpoint(device) {
        if (!device) {
            return '';
        }
        if (device.vpnEndpoint) {
            return device.vpnEndpoint;
        }
        if (device.vpnIpAddress && String(device.vpnIpAddress).includes(':')) {
            return device.vpnIpAddress;
        }
        if (device.vpnIpAddress) {
            return `${device.vpnIpAddress}:${device.apiPort || 8728}`;
        }
        if (device.apiIpAddress) {
            return `${device.apiIpAddress}:${device.apiPort || 8728}`;
        }
        return '';
    }

    function showDevicePanel() {
        elements.devicePanel.classList.add('is-open');
    }

    function hideDevicePanel() {
        elements.devicePanel.classList.remove('is-open');
    }

    function payload() {
        return {
            deviceName: elements.deviceNameInput.value.trim(),
            siteName: elements.siteNameInput.value.trim(),
            vpnIpAddress: elements.vpnIpInput.value.trim(),
            apiUsername: elements.apiUsernameInput.value.trim(),
            apiPassword: elements.apiPasswordInput.value,
            monitoringInterval: Number(elements.monitoringIntervalInput.value || 60),
            apiEnabled: true
        };
    }

    function updateStatusMeta(device) {
        const status = String(device.currentStatus || device.status || 'offline').toLowerCase();
        elements.deviceOnlineIndicator.textContent = status;
        elements.deviceOnlineIndicator.className = `status-chip ${status === 'online' ? 'online' : status === 'maintenance' ? 'maintenance' : 'offline'}`;
        elements.apiPasswordInput.placeholder = device && device.apiPasswordMasked ? `Tersimpan: ${device.apiPasswordMasked}` : 'Kosongkan jika tidak diubah';
    }

    function setCurrentDevice(device) {
        state.currentId = device ? device.id : null;
        elements.deviceId.value = device ? device.id : '';
        elements.deviceNameInput.value = device ? (device.deviceName || device.name || '') : '';
        elements.siteNameInput.value = device ? (device.siteName || device.location || '') : '';
        elements.vpnIpInput.value = device ? resolveVpnEndpoint(device) : '';
        elements.monitoringIntervalInput.value = device ? (device.monitoringInterval || device.pollingIntervalSnmp || device.syncIntervalApi || 60) : 60;
        elements.apiUsernameInput.value = device ? (device.apiUsername || device.username || '') : '';
        elements.apiPasswordInput.value = '';
        updateStatusMeta(device || {});
        elements.formMessage.textContent = state.currentId
            ? 'Mode edit aktif. Tombol Save Device akan mengirim PUT ke backend.'
            : 'Mode tambah aktif. Anda bisa test API atau check status langsung dari form sebelum menyimpan.';
    }

    function openCreateMode() {
        state.currentId = null;
        elements.mikrotikForm.reset();
        setCurrentDevice(null);
        showDevicePanel();
        elements.deviceNameInput.focus();
    }

    function openEditMode(device) {
        setCurrentDevice(device);
        showDevicePanel();
        elements.deviceNameInput.focus();
    }

    function renderTable() {
        const online = state.devices.filter(device => String(device.currentStatus || device.status || '').toLowerCase() === 'online').length;
        const hasKeyword = document.getElementById('mikrotikSearchInput').value.trim() !== '';
        const rows = hasKeyword ? state.filteredDevices : state.devices;
        elements.mkStatTotal.textContent = String(state.devices.length);
        elements.mkStatOnline.textContent = String(online);
        elements.mkStatOffline.textContent = String(state.devices.length - online);
        const lastSync = state.devices.map(device => device.lastSnmpSyncAt || device.lastApiSyncAt).filter(Boolean).sort().pop();
        elements.mkStatLastSync.textContent = formatDateTime(lastSync);
        if (!rows.length) {
            elements.mikrotikTableBody.innerHTML = '';
            elements.mikrotikEmptyState.style.display = 'block';
            return;
        }
        elements.mikrotikEmptyState.style.display = 'none';
        elements.mikrotikTableBody.innerHTML = rows.map(device => `
            <tr>
                <td><strong>${device.deviceName || device.name || '-'}</strong><div class="mini-note">API User: ${device.apiUsername || device.username || '-'}</div></td>
                <td>${device.siteName || device.location || '-'}</td>
                <td class="hybrid-mono">${resolveVpnEndpoint(device) || '-'}</td>
                <td><span class="status-chip ${String(device.currentStatus || device.status || 'offline').toLowerCase()}">${device.currentStatus || device.status || 'offline'}</span></td>
                <td><div class="mini-note">Interval: ${device.monitoringInterval || device.pollingIntervalSnmp || device.syncIntervalApi || 60} detik</div><div class="mini-note">Last check: ${formatDateTime(device.lastApiSyncAt || device.lastSnmpSyncAt)}</div></td>
                <td><div class="inline-actions"><button class="btn btn-secondary" data-action="edit" data-id="${device.id}" type="button">Edit</button><button class="btn btn-secondary" data-action="status" data-id="${device.id}" type="button">Check</button><button class="btn btn-danger" data-action="delete" data-id="${device.id}" type="button">Hapus</button></div></td>
            </tr>
        `).join('');
    }

    async function loadDevices() {
        state.devices = await request('/api/network/mikrotik/devices');
        state.filteredDevices = [...state.devices];
        renderTable();
        if (!state.currentId && state.devices.length) {
            setCurrentDevice(state.devices[0]);
        }
    }

    async function saveDevice(event) {
        event.preventDefault();
        const isEditMode = Boolean(state.currentId);
        const url = isEditMode ? `/api/network/mikrotik/devices/${state.currentId}` : '/api/network/mikrotik/devices';
        const method = isEditMode ? 'PUT' : 'POST';
        const saved = await request(url, { method, body: JSON.stringify(payload()) });
        notify('success', isEditMode
            ? 'Konfigurasi MikroTik berhasil diperbarui.'
            : 'Konfigurasi MikroTik berhasil disimpan. Form siap untuk device berikutnya.');
        await loadDevices();
        if (isEditMode) {
            setCurrentDevice(saved);
            showDevicePanel();
            return;
        }
        openCreateMode();
        elements.formMessage.textContent = 'Mode tambah aktif. Device sebelumnya sudah tersimpan, silakan lanjut input device berikutnya.';
    }

    async function runAction(kind) {
        const hasSavedDevice = Boolean(state.currentId);
        const endpoint = kind === 'api'
            ? (hasSavedDevice ? `/api/network/mikrotik/${state.currentId}/test-api` : '/api/network/mikrotik/test-api')
            : (hasSavedDevice ? `/api/network/mikrotik/${state.currentId}/check-status` : '/api/network/mikrotik/check-status');
        const options = hasSavedDevice
            ? { method: 'POST' }
            : { method: 'POST', body: JSON.stringify(payload()) };
        const result = await request(endpoint, options);
        if (kind === 'status' && hasSavedDevice) {
            await loadDevices();
            const updated = state.devices.find(item => item.id === state.currentId);
            if (updated) {
                setCurrentDevice(updated);
            }
        }
        if (kind === 'api') {
            notify('success', `API OK: ${result.identityName || '-'} / ROS ${result.routerOsVersion || '-'}`);
            return;
        }
        const targetEndpoint = result.targetHost && result.targetPort
            ? `${result.targetHost}:${result.targetPort}`
            : (result.vpnEndpoint || result.vpnIpAddress || elements.vpnIpInput.value || '-');
        notify('success', `Status ${result.currentStatus || (result.reachable ? 'online' : 'offline')} | target ${targetEndpoint}`);
    }

    async function handleTableClick(event) {
        const button = event.target.closest('button[data-action]');
        if (!button) {
            return;
        }
        const id = Number(button.dataset.id);
        const action = button.dataset.action;
        const device = state.devices.find(item => item.id === id);
        if (!device) {
            return;
        }
        if (action === 'edit') {
            openEditMode(device);
            return;
        }
        if (action === 'status') {
            state.currentId = id;
            await runAction('status');
            return;
        }
        if (action === 'delete') {
            if (window.nmxConfirm && !(await window.nmxConfirm('Hapus device MikroTik ini?', { title: 'Konfirmasi Hapus', confirmText: 'Hapus', confirmClass: 'btn btn-danger' }))) {
                return;
            }
            await request(`/api/network/mikrotik/${id}`, { method: 'DELETE' });
            notify('success', 'Device berhasil dihapus.');
            setCurrentDevice(null);
            hideDevicePanel();
            await loadDevices();
        }
    }

    document.addEventListener('DOMContentLoaded', async function() {
        document.getElementById('focusFormButton').addEventListener('click', function() {
            openCreateMode();
        });
        document.getElementById('mikrotikSearchInput').addEventListener('input', function(event) {
            const keyword = event.target.value.toLowerCase().trim();
            state.filteredDevices = !keyword ? [...state.devices] : state.devices.filter(device =>
                String(device.deviceName || device.name || '').toLowerCase().includes(keyword) ||
                String(device.siteName || device.location || '').toLowerCase().includes(keyword) ||
                String(resolveVpnEndpoint(device) || '').toLowerCase().includes(keyword) ||
                String(device.ipAddress || '').toLowerCase().includes(keyword) ||
                String(device.currentStatus || device.status || '').toLowerCase().includes(keyword)
            );
            renderTable();
        });
        elements.mikrotikForm.addEventListener('submit', function(event) { saveDevice(event).catch(error => notify('error', error.message)); });
        elements.resetFormButton.addEventListener('click', function() {
            elements.mikrotikForm.reset();
            setCurrentDevice(null);
            hideDevicePanel();
        });
        elements.testApiButton.addEventListener('click', function() { runAction('api').catch(error => notify('error', error.message)); });
        elements.checkStatusButton.addEventListener('click', function() { runAction('status').catch(error => notify('error', error.message)); });
        elements.closeDevicePanelButton.addEventListener('click', hideDevicePanel);
        elements.devicePanel.addEventListener('click', function(event) {
            if (event.target === elements.devicePanel) {
                hideDevicePanel();
            }
        });
        document.addEventListener('keydown', function(event) {
            if (event.key === 'Escape') {
                hideDevicePanel();
            }
        });
        elements.mikrotikTableBody.addEventListener('click', function(event) { handleTableClick(event).catch(error => notify('error', error.message)); });
        try {
            await loadDevices();
            hideDevicePanel();
        } catch (error) {
            notify('error', error.message);
        }
    });
})();
