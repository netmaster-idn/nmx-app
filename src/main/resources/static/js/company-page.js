(function() {
    if (window.__companyPageEnhanced) {
        return;
    }
    window.__companyPageEnhanced = true;

    var provinceSelect = document.getElementById('inputProvince');
    var regencySelect = document.getElementById('inputRegency');
    var districtSelect = document.getElementById('inputDistrict');
    var villageSelect = document.getElementById('inputVillage');
    var bankNameSelect = document.getElementById('bankName');
    var companyTabs = Array.prototype.slice.call(document.querySelectorAll('[data-company-tab]'));
    var companyPanels = Array.prototype.slice.call(document.querySelectorAll('[data-company-panel]'));
    var accordionCards = Array.prototype.slice.call(document.querySelectorAll('[data-accordion-card]'));
    var indonesianBanks = [
        'Bank Central Asia (BCA)', 'Bank Rakyat Indonesia (BRI)', 'Bank Mandiri', 'Bank Negara Indonesia (BNI)',
        'Bank Syariah Indonesia (BSI)', 'Bank Tabungan Negara (BTN)', 'Bank CIMB Niaga', 'Bank OCBC NISP',
        'Bank Danamon', 'Bank Permata', 'Bank Panin', 'Bank Maybank Indonesia', 'Bank BTPN', 'Bank Mega',
        'Bank Sinarmas', 'Bank Bukopin', 'Bank Muamalat Indonesia', 'Bank Jago', 'Bank Neo Commerce',
        'SeaBank Indonesia', 'Bank DBS Indonesia', 'Citibank N.A. Indonesia', 'HSBC Indonesia', 'Bank UOB Indonesia',
        'Bank DKI', 'Bank Jabar Banten (BJB)', 'Bank Jateng', 'Bank Jatim', 'Bank Sumut', 'Bank Nagari',
        'Bank Sumsel Babel', 'Bank Lampung', 'Bank Kalbar', 'Bank Kaltimtara', 'Bank Kalsel', 'Bank Kalteng',
        'Bank Sulselbar', 'Bank SulutGo', 'Bank Sulteng', 'Bank Sultra', 'Bank NTB Syariah', 'Bank NTT',
        'Bank Maluku Malut', 'Bank Papua', 'Bank Bengkulu', 'Bank Aceh Syariah', 'Bank BPD Bali',
        'Bank BCA Syariah', 'Bank Mega Syariah', 'Bank Panin Dubai Syariah'
    ];

    if (!provinceSelect || !regencySelect || !districtSelect || !villageSelect) {
        return;
    }

    function getPermissionLevel() { return window.permissionLevel || 'READ'; }
    function isFullAccess() { return getPermissionLevel() === 'FULL'; }
    function getCsrfHeader() { return window.csrfHeader || 'X-CSRF-TOKEN'; }
    function getCsrfToken() { return window.csrfToken || ''; }
    function getCurrentCompanyId() { return window.currentCompanyId; }
    function setCurrentCompanyId(value) { window.currentCompanyId = value; }
    function getCompanyData() { return window.companyData; }
    function setCompanyData(value) { window.companyData = value; }
    function getBankAccounts() { return window.companyBankAccounts || []; }
    function setBankAccounts(value) { window.companyBankAccounts = Array.isArray(value) ? value : []; }
    function hasText(value) { return value !== null && value !== undefined && String(value).trim() !== ''; }
    function getEditingBankAccountId() { var input = document.getElementById('bankAccountId'); return input ? input.value : ''; }

    function escapeHtml(value) {
        if (value === null || value === undefined) { return ''; }
        return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function getInitials(name) {
        if (!hasText(name)) { return 'NM'; }
        return String(name).split(/\s+/).filter(Boolean).map(function(word) { return word.charAt(0); }).join('').substring(0, 2).toUpperCase();
    }

    function buildRtRw(rt, rw) {
        if (hasText(rt) && hasText(rw)) { return 'RT ' + rt + ' / RW ' + rw; }
        if (hasText(rt)) { return 'RT ' + rt; }
        if (hasText(rw)) { return 'RW ' + rw; }
        return null;
    }

    function getSelectedOptionText(selectElement) {
        if (!selectElement || !selectElement.value) { return null; }
        return selectElement.options[selectElement.selectedIndex].text;
    }

    function formatDisplayValue(value) {
        return hasText(value) ? value : 'Data Kosong';
    }

    function showCompanyToast(message, type) {
        if (window.nmxNotify) {
            window.nmxNotify({ type: type, message: message });
            return;
        }
        var toast = document.getElementById('companyToast');
        if (!toast) { return; }
        toast.textContent = message;
        toast.className = 'company-toast ' + type + ' show';
        setTimeout(function() { toast.classList.remove('show'); }, 3200);
    }

    async function confirmAction(message, title) {
        if (window.nmxConfirm) {
            return window.nmxConfirm(message, { title: title || 'Konfirmasi', confirmText: 'Lanjutkan', confirmClass: 'btn btn-primary' });
        }
        return window.confirm(message);
    }

    function syncCompanyModalViewport() {
        document.documentElement.style.setProperty('--company-modal-viewport-gap', (window.innerWidth <= 768 ? 12 : 24) + 'px');
    }

    function focusModalAtCenter(modalId) {
        var modal = document.getElementById(modalId);
        if (!modal) { return; }
        modal.scrollTop = 0;
        var modalBody = modal.querySelector('.modal-body');
        if (modalBody) { modalBody.scrollTop = 0; }
    }

    function resetSelect(selectElement, placeholder) {
        selectElement.innerHTML = '<option value="">' + placeholder + '</option>';
        selectElement.disabled = selectElement !== provinceSelect;
    }

    function setSelectLoading(selectElement, placeholder) {
        selectElement.innerHTML = '<option value="">Memuat ' + placeholder.toLowerCase() + '...</option>';
        selectElement.disabled = true;
    }

    function populateSelect(selectElement, items, placeholder, selectedCode) {
        var html = ['<option value="">' + placeholder + '</option>'];
        for (var i = 0; i < items.length; i++) {
            var item = items[i];
            var optionValue = item.id || item.code || '';
            html.push('<option value="' + escapeHtml(optionValue) + '"' + (selectedCode && optionValue === selectedCode ? ' selected' : '') + '>' + escapeHtml(item.name) + '</option>');
        }
        selectElement.innerHTML = html.join('');
        selectElement.disabled = false;
    }

    async function loadOptionsIntoSelect(url, selectElement, placeholder, selectedCode) {
        setSelectLoading(selectElement, placeholder);
        try {
            var response = await fetch(url, { headers: { 'Accept': 'application/json' } });
            var contentType = response.headers.get('content-type') || '';
            if (!response.ok) {
                if (contentType.indexOf('application/json') !== -1) {
                    var errorResult = await response.json();
                    throw new Error(errorResult.message || 'Gagal memuat data wilayah');
                }
                throw new Error('Service data wilayah tidak tersedia');
            }
            var result = await response.json();
            if (!result.success) { throw new Error(result.message || 'Gagal memuat data wilayah'); }
            populateSelect(selectElement, result.data || [], placeholder, selectedCode);
        } catch (error) {
            resetSelect(selectElement, placeholder);
            throw error;
        }
    }

    async function loadProvinces(selectedCode) {
        await loadOptionsIntoSelect('/api/company/regions/provinces', provinceSelect, 'Pilih Provinsi', selectedCode);
    }

    async function loadRegionOptions(regionType, targetSelect, parentCode, queryName, placeholder, selectedCode) {
        if (!parentCode) {
            resetSelect(targetSelect, placeholder);
            return;
        }
        await loadOptionsIntoSelect('/api/company/regions/' + regionType + '?' + queryName + '=' + encodeURIComponent(parentCode), targetSelect, placeholder, selectedCode);
    }

    function populateBankNameOptions() {
        if (!bankNameSelect) { return; }
        var html = ['<option value="">Pilih Bank</option>'];
        for (var i = 0; i < indonesianBanks.length; i++) {
            html.push('<option value="' + escapeHtml(indonesianBanks[i]) + '">' + escapeHtml(indonesianBanks[i]) + '</option>');
        }
        bankNameSelect.innerHTML = html.join('');
    }

    function buildCompanyPayload() {
        return {
            name: document.getElementById('inputName').value.trim(),
            phone: document.getElementById('inputPhone').value.trim(),
            email: document.getElementById('inputEmail').value.trim(),
            website: document.getElementById('inputWebsite').value.trim(),
            tagline: document.getElementById('inputTagline').value.trim(),
            address: document.getElementById('inputAddressNote').value.trim(),
            provinceCode: provinceSelect.value || null,
            provinceName: getSelectedOptionText(provinceSelect),
            regencyCode: regencySelect.value || null,
            regencyName: getSelectedOptionText(regencySelect),
            districtCode: districtSelect.value || null,
            districtName: getSelectedOptionText(districtSelect),
            villageCode: villageSelect.value || null,
            villageName: getSelectedOptionText(villageSelect),
            rt: document.getElementById('inputRt').value.trim(),
            rw: document.getElementById('inputRw').value.trim(),
            buildingNumber: document.getElementById('inputBuildingNumber').value.trim(),
            streetName: document.getElementById('inputStreet').value.trim(),
            googleMapsCoordinates: document.getElementById('inputCoordinates').value.trim(),
            npwp: document.getElementById('inputNpwp').value.trim(),
            pkpNumber: document.getElementById('inputPkp').value.trim(),
            businessType: document.getElementById('inputBusiness').value.trim(),
            facebook: document.getElementById('inputFacebook').value.trim(),
            instagram: document.getElementById('inputInstagram').value.trim(),
            whatsapp: document.getElementById('inputWhatsapp').value.trim(),
            supportEmail: document.getElementById('inputSupport').value.trim()
        };
    }

    function buildBankAccountPayload() {
        return {
            bankName: document.getElementById('bankName').value.trim(),
            accountName: document.getElementById('accountName').value.trim(),
            accountNumber: document.getElementById('accountNumber').value.trim(),
            instructions: document.getElementById('bankInstructions').value.trim(),
            primary: document.getElementById('bankIsPrimary').value === 'true'
        };
    }

    function setCompanyLogo(container, logoUrl, name) {
        if (!container) { return; }
        if (hasText(logoUrl)) {
            container.classList.remove('fallback');
            container.innerHTML = '<img src="' + logoUrl + '" alt="Logo ' + escapeHtml(name || 'Company') + '">';
            return;
        }
        container.classList.add('fallback');
        container.textContent = getInitials(name);
    }

    function setHeaderTagline(tagline) {
        var element = document.getElementById('displayTagline');
        if (element) {
            element.textContent = hasText(tagline) ? tagline : 'Tambahkan tagline perusahaan';
        }
    }

    function setViewState(state) {
        var loading = document.getElementById('companyLoadingState');
        var error = document.getElementById('companyErrorState');
        var empty = document.getElementById('companyEmptyState');
        var content = document.getElementById('companyDataContent');
        if (loading) { loading.style.display = state === 'loading' ? 'grid' : 'none'; }
        if (error) { error.style.display = state === 'error' ? 'block' : 'none'; }
        if (empty) { empty.style.display = state === 'empty' ? 'block' : 'none'; }
        if (content) { content.style.display = state === 'content' ? 'grid' : 'none'; }
    }

    function renderDataTable(targetId, items) {
        var target = document.getElementById(targetId);
        if (!target) { return; }
        target.innerHTML = items.map(function(item) {
            return '<div class="company-data-row"><div class="company-data-label">' + escapeHtml(item.label) + '</div><div class="company-data-value">' + escapeHtml(formatDisplayValue(item.value)) + '</div></div>';
        }).join('');
    }

    function displayCompanyData(data) {
        document.getElementById('displayName').textContent = hasText(data.name) ? data.name : 'Company';
        setHeaderTagline(formatDisplayValue(data.tagline));
        setCompanyLogo(document.getElementById('displayLogo'), data.logoUrl, data.name);

        renderDataTable('companyInfoTable', [
            { label: 'Nama', value: data.name },
            { label: 'Alamat Lengkap', value: data.address },
            { label: 'Telepon', value: data.phone },
            { label: 'Email', value: data.email },
            { label: 'Website', value: data.website }
        ]);

        renderDataTable('serverAddressTable', [
            { label: 'Provinsi', value: data.provinceName },
            { label: 'Kota/Kabupaten', value: data.regencyName },
            { label: 'Kecamatan', value: data.districtName },
            { label: 'Kelurahan/Desa', value: data.villageName },
            { label: 'RT / RW', value: buildRtRw(data.rt, data.rw) },
            { label: 'Jalan', value: data.streetName },
            { label: 'No. bangunan', value: data.buildingNumber },
            { label: 'Koordinat Maps', value: data.googleMapsCoordinates }
        ]);

        renderDataTable('legalInfoTable', [
            { label: 'NPWP', value: data.npwp },
            { label: 'PKP', value: data.pkpNumber },
            { label: 'Bidang usaha', value: data.businessType }
        ]);

        renderDataTable('channelInfoTable', [
            { label: 'Facebook', value: data.facebook },
            { label: 'Instagram', value: data.instagram },
            { label: 'WhatsApp', value: data.whatsapp },
            { label: 'Support email', value: data.supportEmail }
        ]);

        var uploadButton = document.getElementById('uploadLogoButton');
        if (uploadButton) {
            uploadButton.innerHTML = data.logoUrl ? '<i class="fas fa-image"></i> Ganti Logo' : '<i class="fas fa-image"></i> Tambah Logo';
        }
    }

    function renderBankAccounts(accounts, targetId) {
        var container = document.getElementById(targetId);
        if (!container) { return; }
        if (!accounts || !accounts.length) {
            container.innerHTML = '<div class="company-data-value company-muted-empty">Belum ada rekening bank tersimpan.</div>';
            return;
        }
        container.innerHTML = accounts.map(function(account) {
            var actions = '';
            if (isFullAccess()) {
                actions = '<button class="bank-account-icon-btn" type="button" title="Edit rekening" onclick="openBankAccountModal(' + Number(account.id) + ')"><i class="fas fa-pen"></i></button>' +
                    '<button class="bank-account-icon-btn danger" type="button" title="Hapus rekening" onclick="deleteBankAccount(' + Number(account.id) + ')"><i class="fas fa-trash"></i></button>';
            }
            return '' +
                '<div class="bank-account-item">' +
                    '<div class="bank-account-item-header">' +
                        '<div><h4 class="bank-account-item-title">' + escapeHtml(account.bankName || 'Bank') + '</h4>' + (account.primary ? '<span class="bank-account-badge">Utama</span>' : '') + '</div>' +
                        '<div class="bank-account-item-actions">' + actions + '</div>' +
                    '</div>' +
                    '<div class="bank-account-meta">' +
                        '<div class="company-data-row"><div class="company-data-label">Nama Pemilik</div><div class="company-data-value">' + escapeHtml(formatDisplayValue(account.accountName)) + '</div></div>' +
                        '<div class="company-data-row"><div class="company-data-label">Nomor Rekening</div><div class="company-data-value">' + escapeHtml(formatDisplayValue(account.accountNumber)) + '</div></div>' +
                        '<div class="company-data-row"><div class="company-data-label">Instruksi</div><div class="company-data-value">' + escapeHtml(formatDisplayValue(account.instructions)) + '</div></div>' +
                    '</div>' +
                '</div>';
        }).join('');
    }

    function setAccordionState(card, isOpen) {
        if (!card) { return; }
        var trigger = card.querySelector('[data-accordion-trigger]');
        card.classList.toggle('is-open', !!isOpen);
        if (trigger) {
            trigger.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        }
    }

    function openFirstAccordion(panel) {
        if (!panel) { return; }
        var cards = panel.querySelectorAll('[data-accordion-card]');
        for (var i = 0; i < cards.length; i++) {
            setAccordionState(cards[i], i === 0);
        }
    }

    function activateTab(tabName) {
        for (var i = 0; i < companyTabs.length; i++) {
            companyTabs[i].classList.toggle('active', companyTabs[i].getAttribute('data-company-tab') === tabName);
        }
        for (var j = 0; j < companyPanels.length; j++) {
            var active = companyPanels[j].getAttribute('data-company-panel') === tabName;
            companyPanels[j].classList.toggle('active', active);
            if (active) {
                openFirstAccordion(companyPanels[j]);
            }
        }
    }

    async function loadBankAccounts() {
        var companyId = getCurrentCompanyId();
        if (!companyId) {
            setBankAccounts([]);
            renderBankAccounts([], 'bankAccountList');
            return;
        }
        try {
            var response = await fetch('/api/company/' + companyId + '/bank-accounts', { headers: { 'Accept': 'application/json' } });
            var result = await response.json();
            if (!result.success) { throw new Error(result.message || 'Gagal memuat rekening bank'); }
            setBankAccounts(result.data || []);
            renderBankAccounts(getBankAccounts(), 'bankAccountList');
        } catch (error) {
            console.error('Error loading bank accounts:', error);
            renderBankAccounts([], 'bankAccountList');
        }
    }

    async function loadCompanyData() {
        setViewState('loading');
        try {
            var response = await fetch('/api/company');
            var result = await response.json();
            if (result.success && result.data) {
                setCompanyData(result.data);
                setCurrentCompanyId(result.data.id);
                displayCompanyData(result.data);
                await loadBankAccounts();
                activateTab('profile');
                setViewState('content');
            } else {
                setCompanyData(null);
                setCurrentCompanyId(null);
                setBankAccounts([]);
                setViewState('empty');
            }
        } catch (error) {
            console.error('Error loading company data:', error);
            setViewState('error');
            showCompanyToast('Gagal memuat data company', 'error');
        }
    }

    async function populateForm(data) {
        document.getElementById('companyId').value = data.id || '';
        document.getElementById('inputName').value = data.name || '';
        document.getElementById('inputPhone').value = data.phone || '';
        document.getElementById('inputEmail').value = data.email || '';
        document.getElementById('inputWebsite').value = data.website || '';
        document.getElementById('inputTagline').value = data.tagline || '';
        document.getElementById('inputStreet').value = data.streetName || '';
        document.getElementById('inputBuildingNumber').value = data.buildingNumber || '';
        document.getElementById('inputRt').value = data.rt || '';
        document.getElementById('inputRw').value = data.rw || '';
        document.getElementById('inputCoordinates').value = data.googleMapsCoordinates || '';
        document.getElementById('inputAddressNote').value = data.address || '';
        document.getElementById('inputNpwp').value = data.npwp || '';
        document.getElementById('inputPkp').value = data.pkpNumber || '';
        document.getElementById('inputBusiness').value = data.businessType || '';
        document.getElementById('inputFacebook').value = data.facebook || '';
        document.getElementById('inputInstagram').value = data.instagram || '';
        document.getElementById('inputWhatsapp').value = data.whatsapp || '';
        document.getElementById('inputSupport').value = data.supportEmail || '';

        if (data.provinceCode) {
            provinceSelect.value = data.provinceCode;
            await loadRegionOptions('regencies', regencySelect, data.provinceCode, 'provinceCode', 'Pilih Kota / Kabupaten', data.regencyCode);
        }
        if (data.regencyCode) {
            regencySelect.value = data.regencyCode;
            await loadRegionOptions('districts', districtSelect, data.regencyCode, 'regencyCode', 'Pilih Kecamatan', data.districtCode);
        }
        if (data.districtCode) {
            districtSelect.value = data.districtCode;
            await loadRegionOptions('villages', villageSelect, data.districtCode, 'districtCode', 'Pilih Kelurahan / Desa', data.villageCode);
        }
        if (data.villageCode) {
            villageSelect.value = data.villageCode;
        }
    }

    async function openCompanyModal(mode) {
        syncCompanyModalViewport();
        document.getElementById('companyForm').reset();
        document.getElementById('companyId').value = '';
        resetSelect(provinceSelect, 'Pilih Provinsi');
        resetSelect(regencySelect, 'Pilih Kota / Kabupaten');
        resetSelect(districtSelect, 'Pilih Kecamatan');
        resetSelect(villageSelect, 'Pilih Kelurahan / Desa');
        document.getElementById('modalTitle').textContent = mode === 'edit' ? 'Edit Company' : 'Tambah Company';
        try {
            await loadProvinces(getCompanyData() && mode === 'edit' ? getCompanyData().provinceCode : null);
            if (mode === 'edit' && getCompanyData()) {
                await populateForm(getCompanyData());
            }
        } catch (error) {
            console.error('Error preparing company form:', error);
            showCompanyToast(error.message || 'Gagal menyiapkan form company', 'error');
        }
        document.getElementById('companyModal').classList.add('open');
        document.getElementById('modalOverlay').style.display = 'block';
        focusModalAtCenter('companyModal');
    }

    function closeCompanyModal() {
        document.getElementById('companyModal').classList.remove('open');
        document.getElementById('modalOverlay').style.display = 'none';
    }

    function closeCompanyModalOnOverlay(event) {
        if (event.target.id === 'modalOverlay') {
            closeCompanyModal();
        }
    }

    async function saveCompanyData() {
        var id = document.getElementById('companyId').value;
        var payload = buildCompanyPayload();
        if (!hasText(payload.name)) {
            showCompanyToast('Nama perusahaan wajib diisi', 'error');
            return;
        }
        try {
            var headers = { 'Content-Type': 'application/json' };
            headers[getCsrfHeader()] = getCsrfToken();
            var response = await fetch(id ? '/api/company/' + id : '/api/company', {
                method: id ? 'PUT' : 'POST',
                headers: headers,
                body: JSON.stringify(payload)
            });
            var result = await response.json();
            if (!result.success) { throw new Error(result.message || 'Gagal menyimpan data company'); }
            showCompanyToast('Data company berhasil disimpan', 'success');
            closeCompanyModal();
            await loadCompanyData();
        } catch (error) {
            console.error('Error saving company:', error);
            showCompanyToast(error.message || 'Gagal menyimpan data company', 'error');
        }
    }

    function triggerLogoUpload() {
        if (!isFullAccess()) { return; }
        if (!getCurrentCompanyId()) {
            showCompanyToast('Simpan data company terlebih dahulu sebelum upload logo', 'error');
            return;
        }
        document.getElementById('companyLogoInput').click();
    }

    async function uploadCompanyLogo(file) {
        try {
            var button = document.getElementById('uploadLogoButton');
            if (button) {
                button.disabled = true;
                button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Uploading...';
            }
            var formData = new FormData();
            formData.append('file', file);
            var headers = {};
            headers[getCsrfHeader()] = getCsrfToken();
            var response = await fetch('/api/company/' + getCurrentCompanyId() + '/logo', { method: 'POST', headers: headers, body: formData });
            var result = await response.json();
            if (!result.success) { throw new Error(result.message || 'Gagal upload logo company'); }
            showCompanyToast('Logo company berhasil diupload', 'success');
            await loadCompanyData();
        } catch (error) {
            console.error('Error uploading logo:', error);
            showCompanyToast(error.message || 'Gagal upload logo company', 'error');
        } finally {
            var button = document.getElementById('uploadLogoButton');
            if (button) {
                button.disabled = false;
                button.innerHTML = '<i class="fas fa-image"></i> ' + (getCompanyData() && getCompanyData().logoUrl ? 'Ganti Logo' : 'Tambah Logo');
            }
        }
    }

    function openDeleteConfirmModal() {
        if (!getCompanyData()) {
            showCompanyToast('Data company tidak ditemukan', 'error');
            return;
        }
        syncCompanyModalViewport();
        document.getElementById('deleteCompanyName').textContent = getCompanyData().name || '-';
        document.getElementById('deleteConfirmModal').classList.add('open');
        document.getElementById('deleteModalOverlay').style.display = 'block';
        focusModalAtCenter('deleteConfirmModal');
    }

    function closeDeleteConfirmModal() {
        document.getElementById('deleteConfirmModal').classList.remove('open');
        document.getElementById('deleteModalOverlay').style.display = 'none';
    }

    function closeDeleteConfirmModalOnOverlay(event) {
        if (event.target.id === 'deleteModalOverlay') {
            closeDeleteConfirmModal();
        }
    }

    async function deleteCompanyData() {
        if (!getCurrentCompanyId()) {
            showCompanyToast('ID company tidak ditemukan', 'error');
            return;
        }
        try {
            var headers = { 'Content-Type': 'application/json' };
            headers[getCsrfHeader()] = getCsrfToken();
            var response = await fetch('/api/company/' + getCurrentCompanyId(), { method: 'DELETE', headers: headers });
            var result = await response.json();
            if (!result.success) { throw new Error(result.message || 'Gagal menghapus data company'); }
            showCompanyToast('Data company berhasil dihapus', 'success');
            closeDeleteConfirmModal();
            setCompanyData(null);
            setCurrentCompanyId(null);
            setBankAccounts([]);
            setViewState('empty');
        } catch (error) {
            console.error('Error deleting company:', error);
            showCompanyToast(error.message || 'Gagal menghapus data company', 'error');
        }
    }

    function resetBankAccountForm() {
        document.getElementById('bankAccountForm').reset();
        document.getElementById('bankAccountId').value = '';
        document.querySelector('#bankAccountModal .form-section-title').textContent = 'Tambah Rekening Bank';
        if (bankNameSelect) { bankNameSelect.value = ''; }
        document.getElementById('bankIsPrimary').value = 'false';
    }

    function fillBankAccountForm(account) {
        if (!account) { return; }
        document.getElementById('bankAccountId').value = account.id || '';
        document.querySelector('#bankAccountModal .form-section-title').textContent = 'Edit Rekening Bank';
        document.getElementById('bankName').value = account.bankName || '';
        document.getElementById('accountName').value = account.accountName || '';
        document.getElementById('accountNumber').value = account.accountNumber || '';
        document.getElementById('bankInstructions').value = account.instructions || '';
        document.getElementById('bankIsPrimary').value = account.primary ? 'true' : 'false';
    }

    async function openBankAccountModal() {
        if (!getCurrentCompanyId()) {
            showCompanyToast('Simpan data company terlebih dahulu sebelum menambah rekening bank', 'error');
            return;
        }
        syncCompanyModalViewport();
        resetBankAccountForm();
        document.getElementById('bankAccountModal').classList.add('open');
        document.getElementById('bankAccountModalOverlay').style.display = 'block';
        focusModalAtCenter('bankAccountModal');
    }

    async function openBankAccountModalForEdit(accountId) {
        var account = getBankAccounts().find(function(item) { return Number(item.id) === Number(accountId); });
        if (!account) {
            await loadBankAccounts();
            account = getBankAccounts().find(function(item) { return Number(item.id) === Number(accountId); });
        }
        if (!account) {
            showCompanyToast('Data rekening bank tidak ditemukan', 'error');
            return;
        }
        syncCompanyModalViewport();
        resetBankAccountForm();
        fillBankAccountForm(account);
        document.getElementById('bankAccountModal').classList.add('open');
        document.getElementById('bankAccountModalOverlay').style.display = 'block';
        focusModalAtCenter('bankAccountModal');
    }

    function closeBankAccountModal() {
        document.getElementById('bankAccountModal').classList.remove('open');
        document.getElementById('bankAccountModalOverlay').style.display = 'none';
    }

    function closeBankAccountModalOnOverlay(event) {
        if (event.target.id === 'bankAccountModalOverlay') {
            closeBankAccountModal();
        }
    }

    async function saveBankAccount() {
        if (!isFullAccess()) {
            showCompanyToast('Hanya Super Admin yang dapat menambah rekening bank', 'error');
            return;
        }
        if (!getCurrentCompanyId()) {
            showCompanyToast('Company belum tersedia', 'error');
            return;
        }
        var payload = buildBankAccountPayload();
        if (!hasText(payload.bankName) || !hasText(payload.accountName) || !hasText(payload.accountNumber)) {
            showCompanyToast('Nama bank, nama pemilik, dan nomor rekening wajib diisi', 'error');
            return;
        }
        try {
            var headers = { 'Content-Type': 'application/json' };
            headers[getCsrfHeader()] = getCsrfToken();
            var bankAccountId = getEditingBankAccountId();
            var response = await fetch('/api/company/' + getCurrentCompanyId() + '/bank-accounts' + (bankAccountId ? '/' + bankAccountId : ''), {
                method: bankAccountId ? 'PUT' : 'POST',
                headers: headers,
                body: JSON.stringify(payload)
            });
            var result = await response.json();
            if (!result.success) { throw new Error(result.message || 'Gagal menyimpan rekening bank'); }
            showCompanyToast(bankAccountId ? 'Rekening bank berhasil diperbarui' : 'Rekening bank berhasil ditambahkan', 'success');
            await loadBankAccounts();
            closeBankAccountModal();
        } catch (error) {
            console.error('Error saving bank account:', error);
            showCompanyToast(error.message || 'Gagal menyimpan rekening bank', 'error');
        }
    }

    async function deleteBankAccount(accountId) {
        if (!isFullAccess()) {
            showCompanyToast('Hanya Super Admin yang dapat menghapus rekening bank', 'error');
            return;
        }
        if (!getCurrentCompanyId() || !accountId) {
            showCompanyToast('Data rekening bank tidak ditemukan', 'error');
            return;
        }
        if (!window.confirm('Hapus rekening bank ini dari database?')) {
            return;
        }
        try {
            var headers = { 'Content-Type': 'application/json' };
            headers[getCsrfHeader()] = getCsrfToken();
            var response = await fetch('/api/company/' + getCurrentCompanyId() + '/bank-accounts/' + accountId, { method: 'DELETE', headers: headers });
            var result = await response.json();
            if (!result.success) { throw new Error(result.message || 'Gagal menghapus rekening bank'); }
            showCompanyToast('Rekening bank berhasil dihapus', 'success');
            await loadBankAccounts();
        } catch (error) {
            console.error('Error deleting bank account:', error);
            showCompanyToast(error.message || 'Gagal menghapus rekening bank', 'error');
        }
    }

    async function exportDatabaseBackup() {
        if (!isFullAccess()) {
            showCompanyToast('Hanya Super Admin yang dapat melakukan export database', 'error');
            return;
        }
        try {
            var headers = {};
            headers[getCsrfHeader()] = getCsrfToken();
            var response = await fetch('/api/company/database/export', { method: 'GET', headers: headers });
            if (!response.ok) { throw new Error('Gagal membuat backup database'); }
            var blob = await response.blob();
            var disposition = response.headers.get('content-disposition') || '';
            var fileNameMatch = disposition.match(/filename=\"?([^"]+)\"?/i);
            var fileName = fileNameMatch && fileNameMatch[1] ? fileNameMatch[1] : 'nmx-database-backup.json';
            var downloadUrl = window.URL.createObjectURL(blob);
            var link = document.createElement('a');
            link.href = downloadUrl;
            link.download = fileName;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(downloadUrl);
            showCompanyToast('Backup database berhasil diunduh', 'success');
        } catch (error) {
            console.error('Error exporting database backup:', error);
            showCompanyToast(error.message || 'Gagal mengunduh backup database', 'error');
        }
    }

    function triggerDatabaseImport() {
        if (!isFullAccess()) {
            showCompanyToast('Hanya Super Admin yang dapat melakukan import database', 'error');
            return;
        }
        var input = document.getElementById('databaseImportInput');
        if (!input) {
            showCompanyToast('Input file import tidak ditemukan', 'error');
            return;
        }
        input.click();
    }

    async function importDatabaseBackup(file) {
        if (!file) { return; }
        var confirmed = await confirmAction('Import akan menggantikan seluruh isi database dengan snapshot dari file backup. Lanjutkan proses restore penuh?', 'Import Database');
        if (!confirmed) { return; }
        try {
            var formData = new FormData();
            formData.append('file', file);
            var headers = {};
            headers[getCsrfHeader()] = getCsrfToken();
            var response = await fetch('/api/company/database/import', { method: 'POST', headers: headers, body: formData });
            var result = await response.json();
            if (!response.ok || !result.success) { throw new Error(result.message || 'Gagal mengimpor backup database'); }
            showCompanyToast('Import database selesai (' + result.data.tables + ' tabel, ' + result.data.rows + ' baris).', 'success');
            setTimeout(function() { window.location.reload(); }, 900);
        } catch (error) {
            console.error('Error importing database backup:', error);
            showCompanyToast(error.message || 'Gagal mengimpor backup database', 'error');
        }
    }

    function bindAddressSelectEvents() {
        provinceSelect.addEventListener('change', async function() {
            resetSelect(regencySelect, 'Pilih Kota / Kabupaten');
            resetSelect(districtSelect, 'Pilih Kecamatan');
            resetSelect(villageSelect, 'Pilih Kelurahan / Desa');
            if (provinceSelect.value) {
                try { await loadRegionOptions('regencies', regencySelect, provinceSelect.value, 'provinceCode', 'Pilih Kota / Kabupaten'); }
                catch (error) { showCompanyToast(error.message || 'Gagal memuat kota / kabupaten', 'error'); }
            }
        });
        regencySelect.addEventListener('change', async function() {
            resetSelect(districtSelect, 'Pilih Kecamatan');
            resetSelect(villageSelect, 'Pilih Kelurahan / Desa');
            if (regencySelect.value) {
                try { await loadRegionOptions('districts', districtSelect, regencySelect.value, 'regencyCode', 'Pilih Kecamatan'); }
                catch (error) { showCompanyToast(error.message || 'Gagal memuat kecamatan', 'error'); }
            }
        });
        districtSelect.addEventListener('change', async function() {
            resetSelect(villageSelect, 'Pilih Kelurahan / Desa');
            if (districtSelect.value) {
                try { await loadRegionOptions('villages', villageSelect, districtSelect.value, 'districtCode', 'Pilih Kelurahan / Desa'); }
                catch (error) { showCompanyToast(error.message || 'Gagal memuat kelurahan / desa', 'error'); }
            }
        });
    }

    function bindLogoUploadEvent() {
        document.getElementById('companyLogoInput').addEventListener('change', async function(event) {
            var file = event.target.files && event.target.files[0];
            if (!file) { return; }
            await uploadCompanyLogo(file);
            event.target.value = '';
        });
    }

    function bindDatabaseImportEvent() {
        var input = document.getElementById('databaseImportInput');
        if (!input) { return; }
        input.addEventListener('change', async function(event) {
            var file = event.target.files && event.target.files[0];
            try { await importDatabaseBackup(file); } finally { event.target.value = ''; }
        });
    }

    function bindTabs() {
        companyTabs.forEach(function(tab) {
            tab.addEventListener('click', function() { activateTab(tab.getAttribute('data-company-tab')); });
        });
    }

    function bindAccordions() {
        accordionCards.forEach(function(card) {
            var trigger = card.querySelector('[data-accordion-trigger]');
            if (!trigger) { return; }
            trigger.addEventListener('click', function(event) {
                if (event.target.closest('.btn') || event.target.closest('.bank-account-icon-btn')) { return; }
                setAccordionState(card, !card.classList.contains('is-open'));
            });
        });
    }

    window.syncCompanyModalViewport = syncCompanyModalViewport;
    window.loadCompanyData = loadCompanyData;
    window.openCompanyModal = openCompanyModal;
    window.closeCompanyModal = closeCompanyModal;
    window.closeCompanyModalOnOverlay = closeCompanyModalOnOverlay;
    window.saveCompanyData = saveCompanyData;
    window.showCompanyToast = showCompanyToast;
    window.openDeleteConfirmModal = openDeleteConfirmModal;
    window.closeDeleteConfirmModal = closeDeleteConfirmModal;
    window.closeDeleteConfirmModalOnOverlay = closeDeleteConfirmModalOnOverlay;
    window.deleteCompanyData = deleteCompanyData;
    window.triggerLogoUpload = triggerLogoUpload;
    window.exportDatabaseBackup = exportDatabaseBackup;
    window.triggerDatabaseImport = triggerDatabaseImport;
    window.openBankAccountModal = function(accountId) { return accountId ? openBankAccountModalForEdit(accountId) : openBankAccountModal(); };
    window.closeBankAccountModal = closeBankAccountModal;
    window.closeBankAccountModalOnOverlay = closeBankAccountModalOnOverlay;
    window.saveBankAccount = saveBankAccount;
    window.deleteBankAccount = deleteBankAccount;

    bindAddressSelectEvents();
    bindLogoUploadEvent();
    bindDatabaseImportEvent();
    bindTabs();
    bindAccordions();
    populateBankNameOptions();
    activateTab('profile');
    syncCompanyModalViewport();
    window.addEventListener('resize', syncCompanyModalViewport);
})();
