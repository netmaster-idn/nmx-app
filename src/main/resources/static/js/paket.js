// Paket & Layanan Page JavaScript
// This file provides all functions needed for the paket.html page

// ==================== GLOBAL VARIABLES ====================
var packages = [];
var services = [];
var filteredPackages = [];
var filteredServices = [];
var currentEditPackage = null;
var currentEditService = null;
var confirmCallback = null;

// ==================== CSRF TOKEN HANDLER ====================
// Get headers with CSRF token for secure API calls
function getCsrfHeaders() {
    var headers = { 'Content-Type': 'application/json' };
    // Try to get CSRF token from window (set by Thymeleaf) or meta tag
    var csrfToken = window.csrfToken || '';
    var csrfHeader = window.csrfHeader || 'X-CSRF-TOKEN';
    
    if (csrfToken) {
        headers[csrfHeader] = csrfToken;
    }
    return headers;
}

// ==================== TAB FUNCTIONS ====================
function openTab(tabId) {
    // Update tab buttons
    document.querySelectorAll('.tab-btn').forEach(function(btn) {
        btn.classList.remove('active');
    });
    var activeBtn = document.querySelector('[data-tab="' + tabId + '"]');
    if (activeBtn) activeBtn.classList.add('active');

    // Update tab content
    document.querySelectorAll('.tab-content').forEach(function(content) {
        content.classList.remove('active');
    });
    var activeContent = document.getElementById(tabId);
    if (activeContent) activeContent.classList.add('active');

    // Show/hide stats blocks if present
    var paketStats = document.getElementById('paketStats');
    var layananStats = document.getElementById('layananStats');
    if (paketStats && layananStats) {
        if (tabId === 'paket-internet') {
            paketStats.style.display = 'grid';
            layananStats.style.display = 'none';
        } else {
            paketStats.style.display = 'none';
            layananStats.style.display = 'grid';
        }
    }
}
// ==================== PACKAGE PANEL ====================
function openPackagePanel() {
    var packagePanelOverlay = document.getElementById('packagePanelOverlay');
    var packagePanel = document.getElementById('packagePanel');
    if (!packagePanelOverlay || !packagePanel) return;
    packagePanelOverlay.classList.add('open');
    packagePanel.classList.add('open');
    loadPackages();
}

function closePackagePanel() {
    var packagePanelOverlay = document.getElementById('packagePanelOverlay');
    var packagePanel = document.getElementById('packagePanel');
    if (packagePanelOverlay) packagePanelOverlay.classList.remove('open');
    if (packagePanel) packagePanel.classList.remove('open');
}

function loadPackages() {
    if (!document.getElementById('mainPackageTableBody') &&
        !document.getElementById('mainPackageTableWrapper') &&
        !document.getElementById('mainPackageEmptyState')) {
        return;
    }
    fetch('/pelanggan/api/packages?includeInactive=true')
        .then(function(res) { return res.json(); })
        .then(function(data) {
            if (data.success) {
                packages = data.data || [];
                filteredPackages = packages;
                try {
                    renderPackages();
                    updatePackageStats();
                } catch (e) {
                    console.warn('Error rendering packages:', e);
                }
            } else {
                showToast('error', 'Error', data.message || 'Gagal memuat data paket');
            }
        })
        .catch(function(err) {
            console.error('Error loading packages:', err);
            showToast('error', 'Error', 'Gagal memuat data paket');
        });
}

function renderPackages() {
    var tbody = document.getElementById('mainPackageTableBody');
    var tableWrapper = document.getElementById('mainPackageTableWrapper');
    var emptyState = document.getElementById('mainPackageEmptyState');

    if (!tbody || !tableWrapper || !emptyState) {
        return;
    }

    if (filteredPackages.length === 0) {
        tableWrapper.style.display = 'none';
        emptyState.style.display = 'flex';
        return;
    }

    tableWrapper.style.display = 'block';
    emptyState.style.display = 'none';

    tbody.innerHTML = filteredPackages.map(function(pkg) {
        return '<tr>' +
            '<td><div class="package-info"><span class="package-name">' + escapeHtml(pkg.name || '-') + '</span>' +
            (pkg.mikrotikProfileName ? '<span class="package-meta">' + escapeHtml(pkg.mikrotikProfileName) + '</span>' : '') + '</div></td>' +
            '<td><div class="package-speed"><i class="fas fa-arrow-down"></i> ' + (pkg.speedDown || 0) + ' / <i class="fas fa-arrow-up"></i> ' + (pkg.speedUp || 0) + ' Mbps</div></td>' +
            '<td><span class="package-price">' + formatRupiah(pkg.price || 0) + '</span></td>' +
            '<td><span class="status-badge-modern ' + (pkg.isActive ? 'active' : 'inactive') + '"><span class="dot"></span>' + (pkg.isActive ? 'Aktif' : 'Nonaktif') + '</span></td>' +
            '<td><div class="action-buttons-modern">' +
            '<button class="action-btn-icon edit" title="Edit" onclick="editPackage(' + pkg.id + ')"><i class="fas fa-pen"></i></button>' +
            '<button class="action-btn-icon duplicate" title="Duplicate" onclick="duplicatePackage(' + pkg.id + ')"><i class="fas fa-copy"></i></button>' +
            '<button class="action-btn-icon delete" title="Delete" onclick="deletePackage(' + pkg.id + ')"><i class="fas fa-trash"></i></button>' +
            '</div></td></tr>';
    }).join('');
}

function updatePackageStats() {
    // Safely update stats with null checks
    try {
        var packageCountEl = document.getElementById('packageCount');
        var activePackagesEl = document.getElementById('activePackages');
        
        if (packageCountEl) packageCountEl.textContent = packages.length;
        if (activePackagesEl) activePackagesEl.textContent = packages.filter(function(p) { return p.isActive; }).length;
    } catch (e) {
        console.warn('Error updating package stats:', e);
    }
}

function filterPackages() {
    var search = document.getElementById('packageSearch').value.toLowerCase();
    filteredPackages = packages.filter(function(pkg) {
        return (pkg.name && pkg.name.toLowerCase().includes(search)) ||
               (pkg.speedDown && pkg.speedDown.toString().includes(search)) ||
               (pkg.speedUp && pkg.speedUp.toString().includes(search));
    });
    renderPackages();
}

// ==================== SERVICE PANEL ====================
function openServicePanel() {
    document.getElementById('servicePanelOverlay').classList.add('open');
    document.getElementById('servicePanel').classList.add('open');
    loadServices();
}

function closeServicePanel() {
    document.getElementById('servicePanelOverlay').classList.remove('open');
    document.getElementById('servicePanel').classList.remove('open');
}

function loadServices() {
    if (!document.getElementById('serviceTableBody') &&
        !document.getElementById('serviceTableWrapper') &&
        !document.getElementById('serviceEmptyState')) {
        return;
    }
    fetch('/pelanggan/api/service-types?includeInactive=true')
        .then(function(res) { return res.json(); })
        .then(function(data) {
            if (data.success) {
                services = data.data || [];
                filteredServices = services;
                try {
                    renderServices();
                    updateServiceStats();
                } catch (e) {
                    console.warn('Error rendering services:', e);
                }
            } else {
                showToast('error', 'Error', data.message || 'Gagal memuat data layanan');
            }
        })
        .catch(function(err) {
            console.error('Error loading services:', err);
            showToast('error', 'Error', 'Gagal memuat data layanan');
        });
}

function renderServices() {
    var tbody = document.getElementById('serviceTableBody');
    var tableWrapper = document.getElementById('serviceTableWrapper');
    var emptyState = document.getElementById('serviceEmptyState');

    if (!tbody || !tableWrapper || !emptyState) {
        // UI elements not present on this page; skip rendering to avoid errors.
        return;
    }

    if (filteredServices.length === 0) {
        tableWrapper.style.display = 'none';
        emptyState.style.display = 'flex';
        return;
    }

    tableWrapper.style.display = 'block';
    emptyState.style.display = 'none';

    tbody.innerHTML = filteredServices.map(function(svc) {
        return '<tr>' +
            '<td><div class="service-info"><span class="service-name">' + escapeHtml(svc.name || '-') + '</span></div></td>' +
            '<td><div class="service-type">' + escapeHtml(svc.type || svc.serviceType || '-') + '</div></td>' +
            '<td><span class="status-badge ' + (svc.isActive ? 'active' : 'inactive') + '"><span class="dot"></span>' + (svc.isActive ? 'Aktif' : 'Nonaktif') + '</span></td>' +
            '<td><div class="action-buttons">' +
            '<button class="action-btn-icon edit" title="Edit" onclick="editService(' + svc.id + ')"><i class="fas fa-pen"></i></button>' +
            '<button class="action-btn-icon delete" title="Delete" onclick="deleteService(' + svc.id + ')"><i class="fas fa-trash"></i></button>' +
            '</div></td></tr>';
    }).join('');

    var serviceListCount = document.getElementById('serviceListCount');
    if (serviceListCount) {
        serviceListCount.textContent = filteredServices.length + ' layanan tersedia';
    }
}

function updateServiceStats() {
    // Safely update stats with null checks
    try {
        var serviceCountEl = document.getElementById('serviceCount');
        var activeServicesEl = document.getElementById('activeServices');
        
        if (serviceCountEl) serviceCountEl.textContent = services.length;
        if (activeServicesEl) activeServicesEl.textContent = services.filter(function(s) { return s.isActive; }).length;
    } catch (e) {
        console.warn('Error updating service stats:', e);
    }
}

function filterServices() {
    var search = document.getElementById('serviceSearch').value.toLowerCase();
    filteredServices = services.filter(function(svc) {
        return (svc.name && svc.name.toLowerCase().includes(search)) ||
               (svc.type && svc.type.toLowerCase().includes(search));
    });
    renderServices();
}

// ==================== PACKAGE MODAL ====================
function openAddPackageModal() {
    currentEditPackage = null;
    document.getElementById('packageId').value = '';
    document.getElementById('packageName').value = '';
    document.getElementById('packageDownload').value = '';
    document.getElementById('packageUpload').value = '';
    document.getElementById('packageBurstDownload').value = '';
    document.getElementById('packageBurstUpload').value = '';
    document.getElementById('packagePrice').value = '';
    document.getElementById('packageProfile').value = '';
    document.getElementById('packageStatus').checked = true;
    document.getElementById('packageDescription').value = '';

    document.getElementById('packageModalTitle').textContent = 'Tambah Paket Internet';
    document.getElementById('packageModalSubtitle').textContent = 'Isi detail paket internet baru';
    document.getElementById('packageModalIcon').className = 'fas fa-box';

    document.getElementById('packageModal').classList.add('open');
}

function editPackage(id) {
    var pkg = packages.find(function(p) { return p.id === id; });
    if (!pkg) return;

    currentEditPackage = pkg;
    document.getElementById('packageId').value = pkg.id;
    document.getElementById('packageName').value = pkg.name || '';
    document.getElementById('packageDownload').value = pkg.speedDown || '';
    document.getElementById('packageUpload').value = pkg.speedUp || '';
    document.getElementById('packageBurstDownload').value = pkg.burstDownload || '';
    document.getElementById('packageBurstUpload').value = pkg.burstUpload || '';
    document.getElementById('packagePrice').value = pkg.price || '';
    document.getElementById('packageProfile').value = pkg.mikrotikProfileName || '';
    document.getElementById('packageStatus').checked = pkg.isActive !== false;
    document.getElementById('packageDescription').value = pkg.description || '';

    document.getElementById('packageModalTitle').textContent = 'Edit Paket Internet';
    document.getElementById('packageModalSubtitle').textContent = 'Perbarui detail paket internet';
    document.getElementById('packageModalIcon').className = 'fas fa-edit';

    document.getElementById('packageModal').classList.add('open');
}

function duplicatePackage(id) {
    var pkg = packages.find(function(p) { return p.id === id; });
    if (!pkg) return;

    currentEditPackage = null;
    document.getElementById('packageId').value = '';
    document.getElementById('packageName').value = (pkg.name || '') + ' (Copy)';
    document.getElementById('packageDownload').value = pkg.speedDown || '';
    document.getElementById('packageUpload').value = pkg.speedUp || '';
    document.getElementById('packageBurstDownload').value = pkg.burstDownload || '';
    document.getElementById('packageBurstUpload').value = pkg.burstUpload || '';
    document.getElementById('packagePrice').value = pkg.price || '';
    document.getElementById('packageProfile').value = pkg.mikrotikProfileName || '';
    document.getElementById('packageStatus').checked = pkg.isActive !== false;
    document.getElementById('packageDescription').value = pkg.description || '';

    document.getElementById('packageModalTitle').textContent = 'Duplikat Paket Internet';
    document.getElementById('packageModalSubtitle').textContent = 'Buat salinan paket internet';
    document.getElementById('packageModalIcon').className = 'fas fa-copy';

    document.getElementById('packageModal').classList.add('open');
}

function closePackageModal() {
    document.getElementById('packageModal').classList.remove('open');
    currentEditPackage = null;
}

function savePackage(event) {
    event.preventDefault();
    
    var packageId = document.getElementById('packageId').value;
    var packageData = {
        name: document.getElementById('packageName').value.trim(),
        speedDown: parseInt(document.getElementById('packageDownload').value) || 0,
        speedUp: parseInt(document.getElementById('packageUpload').value) || 0,
        burstDownload: parseInt(document.getElementById('packageBurstDownload').value) || null,
        burstUpload: parseInt(document.getElementById('packageBurstUpload').value) || null,
        price: parseFloat(document.getElementById('packagePrice').value) || 0,
        mikrotikProfileName: document.getElementById('packageProfile').value,
        isActive: document.getElementById('packageStatus').checked,
        description: document.getElementById('packageDescription').value.trim()
    };

    if (!packageData.name) {
        showToast('warning', 'Validasi', 'Nama paket wajib diisi');
        return;
    }

    var url = packageId ? '/pelanggan/api/packages/' + packageId : '/pelanggan/api/packages';
    var method = packageId ? 'PUT' : 'POST';

    fetch(url, {
        method: method,
        headers: getCsrfHeaders(),
        body: JSON.stringify(packageData)
    })
    .then(function(res) { return res.json(); })
    .then(function(data) {
        if (data.success) {
            showToast('success', 'Berhasil', packageId ? 'Paket diperbarui!' : 'Paket dibuat!');
            closePackageModal();
            loadPackages();
        } else {
            showToast('error', 'Error', data.message || 'Gagal menyimpan paket');
        }
    })
    .catch(function(err) {
        console.error('Error saving package:', err);
        showToast('error', 'Error', 'Gagal menyimpan paket');
    });
}

function deletePackage(id) {
    showConfirm(
        'Hapus Paket',
        'Apakah Anda yakin ingin menghapus paket ini?',
        function() {
            fetch('/pelanggan/api/packages/' + id, { 
                method: 'DELETE',
                headers: getCsrfHeaders()
            })
                .then(function(res) { return res.json(); })
                .then(function(data) {
                    if (data.success) {
                        showToast('success', 'Berhasil', 'Paket dihapus!');
                        loadPackages();
                    } else {
                        showToast('error', 'Error', data.message || 'Gagal menghapus paket');
                    }
                })
                .catch(function(err) {
                    console.error('Error deleting package:', err);
                    showToast('error', 'Error', 'Gagal menghapus paket');
                });
        }
    );
}

// ==================== SERVICE MODAL ====================
function openAddServiceModal() {
    currentEditService = null;
    var supportOltInput = document.getElementById('supportOlt');
    document.getElementById('serviceId').value = '';
    document.getElementById('serviceName').value = '';
    document.getElementById('serviceType').value = '';
    document.getElementById('serviceAuth').value = 'CHAP';
    document.getElementById('supportPppoe').checked = false;
    document.getElementById('supportHotspot').checked = false;
    document.getElementById('supportStatic').checked = false;
    document.getElementById('supportDhcp').checked = false;
    if (supportOltInput) supportOltInput.checked = false;
    document.getElementById('serviceStatus').checked = true;
    document.getElementById('serviceDescription').value = '';

    document.getElementById('serviceModalTitle').textContent = 'Tambah Tipe Layanan';
    document.getElementById('serviceModalSubtitle').textContent = 'Isi detail tipe layanan baru';
    document.getElementById('serviceModalIcon').className = 'fas fa-network-wired';

    document.getElementById('serviceModal').classList.add('open');
}

function editService(id) {
    var svc = services.find(function(s) { return s.id === id; });
    if (!svc) return;

    currentEditService = svc;
    var supportOltInput = document.getElementById('supportOlt');
    document.getElementById('serviceId').value = svc.id;
    document.getElementById('serviceName').value = svc.name || '';
    document.getElementById('serviceType').value = svc.type || svc.serviceType || '';
    document.getElementById('serviceAuth').value = svc.authenticationMethod || 'CHAP';
    document.getElementById('supportPppoe').checked = svc.supportPppoe || false;
    document.getElementById('supportHotspot').checked = svc.supportHotspot || false;
    document.getElementById('supportStatic').checked = svc.supportStaticIp || svc.supportStatic || false;
    document.getElementById('supportDhcp').checked = svc.supportDhcp || false;
    if (supportOltInput) supportOltInput.checked = svc.supportOlt || false;
    document.getElementById('serviceStatus').checked = svc.isActive !== false;
    document.getElementById('serviceDescription').value = svc.description || '';

    document.getElementById('serviceModalTitle').textContent = 'Edit Tipe Layanan';
    document.getElementById('serviceModalSubtitle').textContent = 'Perbarui detail tipe layanan';
    document.getElementById('serviceModalIcon').className = 'fas fa-edit';

    document.getElementById('serviceModal').classList.add('open');
}

function closeServiceModal() {
    document.getElementById('serviceModal').classList.remove('open');
    currentEditService = null;
}

function saveService(event) {
    event.preventDefault();
    var supportOltInput = document.getElementById('supportOlt');
    
    var serviceId = document.getElementById('serviceId').value;
    var serviceData = {
        name: document.getElementById('serviceName').value.trim(),
        type: document.getElementById('serviceType').value,
        serviceType: document.getElementById('serviceType').value,
        authenticationMethod: document.getElementById('serviceAuth').value,
        supportPppoe: document.getElementById('supportPppoe').checked,
        supportHotspot: document.getElementById('supportHotspot').checked,
        supportStaticIp: document.getElementById('supportStatic').checked,
        supportStatic: document.getElementById('supportStatic').checked,
        supportDhcp: document.getElementById('supportDhcp').checked,
        supportOlt: supportOltInput ? supportOltInput.checked : false,
        isActive: document.getElementById('serviceStatus').checked,
        description: document.getElementById('serviceDescription').value.trim()
    };

    if (!serviceData.name) {
        showToast('warning', 'Validasi', 'Nama layanan wajib diisi');
        return;
    }
    if (!serviceData.type) {
        showToast('warning', 'Validasi', 'Tipe layanan wajib dipilih');
        return;
    }

    var url = serviceId ? '/pelanggan/api/service-types/' + serviceId : '/pelanggan/api/service-types';
    var method = serviceId ? 'PUT' : 'POST';

    fetch(url, {
        method: method,
        headers: getCsrfHeaders(),
        body: JSON.stringify(serviceData)
    })
    .then(function(res) { return res.json(); })
    .then(function(data) {
        if (data.success) {
            showToast('success', 'Berhasil', serviceId ? 'Layanan diperbarui!' : 'Layanan dibuat!');
            closeServiceModal();
            loadServices();
        } else {
            showToast('error', 'Error', data.message || 'Gagal menyimpan layanan');
        }
    })
    .catch(function(err) {
        console.error('Error saving service:', err);
        showToast('error', 'Error', 'Gagal menyimpan layanan');
    });
}

function deleteService(id) {
    showConfirm(
        'Hapus Layanan',
        'Apakah Anda yakin ingin menghapus tipe layanan ini?',
        function() {
            fetch('/pelanggan/api/service-types/' + id, { 
                method: 'DELETE',
                headers: getCsrfHeaders()
            })
                .then(function(res) { return res.json(); })
                .then(function(data) {
                    if (data.success) {
                        showToast('success', 'Berhasil', 'Layanan dihapus!');
                        loadServices();
                    } else {
                        showToast('error', 'Error', data.message || 'Gagal menghapus layanan');
                    }
                })
                .catch(function(err) {
                    console.error('Error deleting service:', err);
                    showToast('error', 'Error', 'Gagal menghapus layanan');
                });
        }
    );
}

// ==================== CONFIRM MODAL ====================
function showConfirm(title, message, callback) {
    document.getElementById('confirmTitle').textContent = title;
    document.getElementById('confirmMessage').textContent = message;
    confirmCallback = callback;
    document.getElementById('confirmModal').classList.add('open');
}

function closeConfirmModal() {
    document.getElementById('confirmModal').classList.remove('open');
    confirmCallback = null;
}

function executeConfirm() {
    if (confirmCallback) {
        confirmCallback();
    }
    closeConfirmModal();
}

// ==================== TOAST NOTIFICATIONS ====================
function showToast(type, title, message) {
    if (window.nmxNotify) {
        window.nmxNotify({
            type: type,
            title: title,
            message: message
        });
        return;
    }

    var container = document.getElementById('toastContainer');
    var toast = document.createElement('div');
    toast.className = 'toast ' + type;
    
    var icons = {
        success: 'fa-check-circle',
        error: 'fa-times-circle',
        warning: 'fa-exclamation-triangle',
        info: 'fa-info-circle'
    };
    
    toast.innerHTML = 
        '<div class="toast-icon">' +
            '<i class="fas ' + icons[type] + '"></i>' +
        '</div>' +
        '<div class="toast-content">' +
            '<p class="toast-title">' + escapeHtml(title) + '</p>' +
            '<p class="toast-message">' + escapeHtml(message) + '</p>' +
        '</div>' +
        '<button class="toast-close" onclick="this.parentElement.remove()">' +
            '<i class="fas fa-times"></i>' +
        '</button>';
    
    container.appendChild(toast);
    
    setTimeout(function() {
        toast.remove();
    }, 4000);
}

// ==================== UTILITY FUNCTIONS ====================
function formatRupiah(amount) {
    return `Rp. ${new Intl.NumberFormat('id-ID', {
        minimumFractionDigits: 0,
        maximumFractionDigits: 0
    }).format(amount || 0)}`;
}

function escapeHtml(text) {
    if (!text) return '';
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Close panels with Escape key
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        if (document.getElementById('packagePanelOverlay')) closePackagePanel();
        if (document.getElementById('servicePanelOverlay')) closeServicePanel();
        if (document.getElementById('packageModal')) closePackageModal();
        if (document.getElementById('serviceModal')) closeServiceModal();
        if (document.getElementById('confirmModal')) closeConfirmModal();
    }
});

// Close modals when clicking outside
document.addEventListener('DOMContentLoaded', function() {
    var packageModal = document.getElementById('packageModal');
    if (packageModal) {
        packageModal.addEventListener('click', function(e) {
            if (e.target === this) closePackageModal();
        });
    }
    
    var serviceModal = document.getElementById('serviceModal');
    if (serviceModal) {
        serviceModal.addEventListener('click', function(e) {
            if (e.target === this) closeServiceModal();
        });
    }
    
    var confirmModal = document.getElementById('confirmModal');
    if (confirmModal) {
        confirmModal.addEventListener('click', function(e) {
            if (e.target === this) closeConfirmModal();
        });
    }
    
    if (document.getElementById('mainPackageTableBody') ||
        document.getElementById('mainPackageTableWrapper') ||
        document.getElementById('mainPackageEmptyState')) {
        loadPackages();
    }

    if (document.getElementById('serviceTableBody') ||
        document.getElementById('serviceTableWrapper') ||
        document.getElementById('serviceEmptyState')) {
        loadServices();
    }
});

console.log('Paket.js loaded successfully');

