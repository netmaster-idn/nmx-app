(function() {
    if (window.__historyPaymentScriptLoaded) {
        return;
    }
    window.__historyPaymentScriptLoaded = true;

    function readInitialCustomers() {
        const dataElement = document.getElementById('historyInitialCustomersData');
        if (!dataElement) {
            return [];
        }

        const raw = String(
            typeof dataElement.value === 'string'
                ? dataElement.value
                : (dataElement.textContent || '')
        ).trim();
        if (!raw) {
            return [];
        }

        try {
            return Function('"use strict"; return (' + raw + ');')();
        } catch (error) {
            console.error('Failed to parse initial history customers payload:', error);
            return [];
        }
    }

    if (!Array.isArray(window.historyInitialCustomers) || window.historyInitialCustomers.length === 0) {
        const initialCustomers = readInitialCustomers();
        window.historyInitialCustomers = Array.isArray(initialCustomers) ? initialCustomers : [];
    }

            // FIXED: Robust handleHistoryQuickAction - direct API, no deps, works immediately
            window.handleHistoryQuickAction = async function(action, customerId) {
                const id = Number(customerId);
                if (action !== 'view-history' || !id || id <= 0) return console.warn('Invalid call');

                try {
                    const [custRes, invRes] = await Promise.all([
                        fetch(`/pelanggan/api/customers/${id}`),
                        fetch(`/api/customers/${id}/invoices`)
                    ]);
                    const cust = await custRes.json();
                    const inv = await invRes.json();

                    const nameEl = document.getElementById('historyCustomerName');
                    if (nameEl) nameEl.textContent = cust.data.fullName + ' (' + cust.data.customerCode + ')';

                    renderHistoryTable(inv.data || []);
                    openModal('historyModal');
                } catch (e) {
                    showAlert('error', `Error: ${e}`);
                }
            };

            window.__historyQuickActionHandler = window.handleHistoryQuickAction;
            window.registerHistoryQuickAction = function(handler) {
                if (typeof handler === 'function') {
                    window.__historyQuickActionHandler = handler;
                }
                return window.__historyQuickActionHandler;
            };
            window.dispatchHistoryQuickAction = function(action, customerId, serviceId = 0) {
                if (typeof window.__historyQuickActionHandler === 'function') {
                    return window.__historyQuickActionHandler(action, customerId, serviceId);
                }
                return Promise.resolve();
            };

            function renderHistoryTable(rows) {
                const tbody = document.getElementById('historyTableBody');
                const data = (rows || []).sort((a, b) => new Date(b.dueDate || 0) - new Date(a.dueDate || 0));
                if (!tbody) return;
                tbody.innerHTML = data.length ? data.map(r => {
                    const status = getPaymentStatus(r.status);
                    return `<tr><td>${r.invoiceNumber||'-'}</td><td>${r.invoiceTypeLabel||'Monthly'}</td><td>${formatDate(r.dueDate)}</td><td>${formatRupiah(r.totalAmount)}</td><td>${formatRupiah(r.amountPaid)}</td><td>${formatDate(r.paymentDate)}</td><td>${r.paymentMethod||'-'}</td><td><span class="payment-badge ${status}">${status}</span></td><td><button onclick="viewInvoiceDetail(${r.id||0})" class="btn btn-sm btn-secondary">Lihat</button></td></tr>`;
                }).join('') : '<tr><td colspan="9" class="table-empty">Belum ada history</td></tr>';  
            }

            // Fallback alert
            function showAlert(type, msg) {
                if (window.nmxNotify) return window.nmxNotify({type, message:msg});
                const c = document.getElementById('alertContainer');
                c.innerHTML += `<div class="alert-message ${type}"><i class="fas fa-info"></i> ${msg}</div>`;
                setTimeout(() => c.lastElementChild.remove(), 4000);
            }

            function getPaymentStatus(s) {
                s = s?.toLowerCase() || '';
                if (s.includes('paid') || s.includes('lunas')) return 'PAID';
                if (s.includes('overdue') || s.includes('tempo')) return 'OVERDUE';
                return 'UNPAID';
            };

            // Initialize Lucide icons
            if (window.lucide && typeof window.lucide.createIcons === 'function') {
                window.lucide.createIcons();
            }
            
            // Global data
            let customers = [];
            let filteredCustomers = [];
            let selectedCustomers = new Set();
            let currentCustomerId = null;
            let currentServiceId = null;
            let confirmCallback = null;
            let currentHistoryData = [];
            let currentHistorySourceData = [];
            let currentHistoryCustomerLabel = '-';
            let currentHistoryCustomerProfile = null;
            let globalInvoices = [];
            let searchKeyword = '';
            let selectedYearFilter = '';
            let selectedMonthFilter = '';
            let selectedPaymentStatusFilter = 'all';
            let paymentStatusDebounceHandle = null;

            function mountHistoryModalsToBody() {
                const modalIds = [
                    'historyModal',
                    'invoiceModal',
                    'payModal',
                    'invoiceDetailModal',
                    'printModal',
                    'confirmModal'
                ];

                modalIds.forEach(modalId => {
                    const modal = document.getElementById(modalId);
                    if (!modal || modal.parentElement === document.body) {
                        return;
                    }
                    document.body.appendChild(modal);
                });
            }
    
            // Initialize
            document.addEventListener('DOMContentLoaded', async function() {
                mountHistoryModalsToBody();

                if (Array.isArray(window.historyInitialCustomers) && window.historyInitialCustomers.length > 0) {
                    customers = window.historyInitialCustomers.map(customer => ({
                        ...customer,
                        invoices: [],
                        totalTagihan: asNumber(customer.totalTagihan || customer.monthlyFee),
                        paidCount: normalizePaymentStatus(customer.paymentStatus) === 'paid' ? 1 : 0,
                        pendingCount: normalizePaymentStatus(customer.paymentStatus) === 'unpaid' ? 1 : 0,
                        overdueCount: normalizePaymentStatus(customer.paymentStatus) === 'overdue' ? 1 : 0,
                        paymentStatus: normalizePaymentStatus(customer.paymentStatus || 'unpaid')
                    }));
                    filteredCustomers = [...customers];
                    applyCustomerFilters();
                    updateStats();
                }

                setupSearch();
                setupFilterControls();
                await loadCustomers();
                await applyDefaultCurrentPeriodFilter();
                if (window.nmxBilling) {
                    window.nmxBilling.onRefresh(() => loadCustomers(true));
                }

                document.addEventListener('keydown', function(event) {
                    if (event.key === 'Escape') {
                        ['historyModal', 'invoiceModal', 'payModal', 'printModal', 'confirmModal', 'invoiceDetailModal']
                            .forEach(closeModal);
                    }
                });
            });
    
            // Load customers from API
            async function loadCustomers(preserveFilters = false) {
                try {
                    setCustomerTableLoading(true);
                    const response = await fetch('/api/pembayaran?status=ALL');
                    const result = await response.json();
                    if (!response.ok || !result.success || !Array.isArray(result.data)) {
                        throw new Error(result.message || 'Gagal memuat data pembayaran');
                    }

                    customers = result.data.map(mapHistoryCustomerRow);
                    selectedCustomers.clear();
                    const selectAllCheckbox = document.getElementById('selectAll');
                    if (selectAllCheckbox) {
                        selectAllCheckbox.checked = false;
                    }
                    updateSelectedUI();
                    applyCustomerFilters();
                    updateStats();

                    await enrichCustomersWithInvoices();
                    if (preserveFilters) {
                        await syncGlobalFilters(true);
                    }
                    applyCustomerFilters();
                    updateStats();
                } catch (error) {
                    console.error('Error loading customers:', error);
                    showAlert('error', 'Gagal memuat data pelanggan');
                } finally {
                    setCustomerTableLoading(false);
                }
            }

            function mapHistoryCustomerRow(customer) {
                const normalizedPaymentStatus = normalizePaymentStatus(customer && customer.paymentStatus);
                return {
                    ...customer,
                    invoices: [],
                    totalTagihan: asNumber(customer && customer.totalTagihan),
                    paidCount: normalizedPaymentStatus === 'paid' ? 1 : 0,
                    pendingCount: normalizedPaymentStatus === 'unpaid' ? 1 : 0,
                    overdueCount: normalizedPaymentStatus === 'overdue' ? 1 : 0,
                    paymentStatus: normalizedPaymentStatus
                };
            }

            function setCustomerTableLoading(isLoading) {
                const tbody = document.getElementById('customerTableBody');
                const emptyState = document.getElementById('emptyState');
                if (!tbody || !emptyState) {
                    return;
                }

                if (isLoading) {
                    emptyState.style.display = 'none';
                    tbody.innerHTML = '<tr><td colspan="9" class="table-empty">Memuat data pembayaran...</td></tr>';
                }
            }
    
            function asNumber(value) {
                const parsed = Number(value);
                return Number.isFinite(parsed) ? parsed : 0;
            }

            function getOutstandingAmount(invoice) {
                const explicitOutstanding = asNumber(invoice && invoice.outstandingAmount);
                if (explicitOutstanding > 0) {
                    return explicitOutstanding;
                }
                return Math.max(0, asNumber(invoice && invoice.totalAmount) - asNumber(invoice && invoice.amountPaid));
            }

            function resolveCustomerPaymentStatusFromInvoices(invoices, fallbackStatus = 'unpaid') {
                const invoiceRows = Array.isArray(invoices) ? invoices : [];
                if (invoiceRows.length === 0) {
                    return normalizePaymentStatus(fallbackStatus);
                }

                const today = new Date();
                today.setHours(0, 0, 0, 0);

                const outstandingInvoices = invoiceRows.filter(invoice => {
                    const normalizedStatus = normalizePaymentStatus(invoice && invoice.status);
                    return normalizedStatus !== 'paid'
                        && normalizedStatus !== 'cancelled'
                        && normalizedStatus !== 'no-payment'
                        && getOutstandingAmount(invoice) > 0;
                });

                if (outstandingInvoices.some(invoice => {
                    const dueDate = parseFlexibleDate(invoice && invoice.dueDate);
                    return dueDate && dueDate < today;
                })) {
                    return 'overdue';
                }
                if (outstandingInvoices.some(invoice => {
                    const dueDate = parseFlexibleDate(invoice && invoice.dueDate);
                    if (!dueDate) {
                        return true;
                    }
                    const reminderDate = new Date(dueDate);
                    reminderDate.setDate(reminderDate.getDate() - 3);
                    return reminderDate <= today && dueDate >= today;
                })) {
                    return 'unpaid';
                }
                if (outstandingInvoices.length > 0) {
                    return 'unpaid';
                }
                if (invoiceRows.some(invoice => normalizePaymentStatus(invoice && invoice.status) === 'paid')) {
                    return 'paid';
                }
                return normalizePaymentStatus(fallbackStatus);
            }

            function addMonthsSameDay(baseDate, monthCount = 1) {
                if (!(baseDate instanceof Date) || Number.isNaN(baseDate.getTime())) {
                    return new Date();
                }

                const year = baseDate.getFullYear();
                const month = baseDate.getMonth() + monthCount;
                const day = baseDate.getDate();
                const lastDayOfTargetMonth = new Date(year, month + 1, 0).getDate();

                return new Date(year, month, Math.min(day, lastDayOfTargetMonth));
            }

            function formatDateInput(date) {
                if (!(date instanceof Date) || Number.isNaN(date.getTime())) {
                    return '';
                }

                const year = date.getFullYear();
                const month = String(date.getMonth() + 1).padStart(2, '0');
                const day = String(date.getDate()).padStart(2, '0');
                return `${year}-${month}-${day}`;
            }

            function requestHeaders(includeJson = false) {
                const headers = {};
                if (includeJson) {
                    headers['Content-Type'] = 'application/json';
                }
                headers[window.csrfHeader || 'X-CSRF-TOKEN'] = window.csrfToken || '';
                return headers;
            }

            // Enrich customers with invoice data
            async function enrichCustomersWithInvoices() {
                try {
                    const response = await fetch('/api/invoices');
                    const result = await response.json();
                    const invoiceRows = result.success && Array.isArray(result.data) ? result.data : [];
                    globalInvoices = invoiceRows.slice();
                    const invoicesByCustomer = new Map();

                    invoiceRows.forEach(invoice => {
                        const customerId = Number(invoice.customerId);
                        if (!Number.isFinite(customerId)) {
                            return;
                        }

                        if (!invoicesByCustomer.has(customerId)) {
                            invoicesByCustomer.set(customerId, []);
                        }

                        invoicesByCustomer.get(customerId).push(invoice);
                    });

                    customers = customers.map(customer => {
                        const invoices = (invoicesByCustomer.get(Number(customer.id)) || []).slice().sort((left, right) => {
                            const leftTime = toDateMillis(left.paymentDate || left.billingMonth || left.dueDate);
                            const rightTime = toDateMillis(right.paymentDate || right.billingMonth || right.dueDate);
                            return rightTime - leftTime;
                        });
                        const paidInvoices = invoices.filter(inv => normalizePaymentStatus(inv.status) === 'paid');
                        const unpaidInvoices = invoices.filter(inv => {
                            const status = normalizePaymentStatus(inv.status);
                            return status !== 'paid' && status !== 'cancelled' && getOutstandingAmount(inv) > 0;
                        });

                        return {
                            ...customer,
                            invoices,
                            totalTagihan: unpaidInvoices.reduce((sum, inv) => sum + getOutstandingAmount(inv), 0),
                            paidCount: paidInvoices.length,
                            pendingCount: unpaidInvoices.length,
                            overdueCount: unpaidInvoices.filter(inv => normalizePaymentStatus(inv.status) === 'overdue').length,
                            paymentStatus: resolveCustomerPaymentStatusFromInvoices(invoices, customer.paymentStatus || 'unpaid')
                        };
                    });
                } catch (error) {
                    console.error('Error loading aggregate invoices for history payment page:', error);
                }
            }

            function canDeleteCustomerAction() {
                return Boolean(document.getElementById('historyCanDeleteCustomerFlag'));
            }
    
            // Render customer table
            function renderTable() {
                const tbody = document.getElementById('customerTableBody');
                const emptyState = document.getElementById('emptyState');
                
                if (filteredCustomers.length === 0) {
                    tbody.innerHTML = '';
                    emptyState.style.display = 'block';
                    return;
                }
                
                emptyState.style.display = 'none';
                tbody.innerHTML = filteredCustomers.map(customer => {
                    const statusClass = getStatusClass(customer.status);
                    const statusText = getStatusText(customer.status);
                    const paymentBadgeClass = getPaymentBadgeClass(customer.paymentStatus);
                    const paymentText = getPaymentText(customer.paymentStatus);
                    const deleteAction = canDeleteCustomerAction() ? `
                                        <button type="button" class="danger" title="Hapus Pelanggan" data-action="delete-customer" data-customer-id="${customer.id}" onclick="event.stopPropagation(); window.dispatchHistoryQuickAction('delete-customer', ${customer.id}); return false;">
                                            <i class="fas fa-trash"></i>
                                        </button>
                    ` : '';
                    
                    return `
                        <tr>
                            <td class="checkbox-cell">
                                <input type="checkbox" value="${customer.id}" onchange="toggleSelectCustomer(${customer.id})" ${selectedCustomers.has(customer.id) ? 'checked' : ''}>
                            </td>
                            <td><span class="customer-code">${customer.customerCode || '-'}</span></td>
                            <td>${customer.fullName || '-'}</td>
                            <td>${customer.phone || '-'}</td>
                            <td>${customer.packageName || '-'}</td>
                            <td>
                                <span class="status-badge ${statusClass}">
                                    <span class="status-dot"></span>
                                    ${statusText}
                                </span>
                            </td>
                            <td>${formatRupiah(customer.totalTagihan || 0)}</td>
                            <td>
                                <span class="payment-badge ${paymentBadgeClass}">
                                    ${paymentText}
                                </span>
                            </td>
                            <td>
                                <div class="action-btn">
                                    <button type="button" title="Lihat History Pembayaran" data-action="view-history" data-customer-id="${customer.id}" onclick="event.stopPropagation(); window.dispatchHistoryQuickAction('view-history', ${customer.id}); return false;">
                                        <i class="fas fa-history"></i>
                                    </button>
                                    ${customer.status === 'active' ? `
                                        <button type="button" class="warning" title="Suspend Layanan" data-action="suspend-service" data-customer-id="${customer.id}" data-service-id="${customer.serviceId || 0}" onclick="event.stopPropagation(); window.dispatchHistoryQuickAction('suspend-service', ${customer.id}, ${customer.serviceId || 0}); return false;">
                                            <i class="fas fa-pause"></i>
                                        </button>
                                    ` : `
                                        <button type="button" class="success" title="Aktifkan Layanan" data-action="activate-service" data-customer-id="${customer.id}" data-service-id="${customer.serviceId || 0}" onclick="event.stopPropagation(); window.dispatchHistoryQuickAction('activate-service', ${customer.id}, ${customer.serviceId || 0}); return false;">
                                            <i class="fas fa-play"></i>
                                        </button>
                                    `}
                                    ${deleteAction}
                                </div>
                            </td>
                        </tr>
                    `;
                }).join('');
            }
    
            // Update statistics
            function updateStats() {
                document.getElementById('totalCustomers').textContent = filteredCustomers.length;
                document.getElementById('totalPaid').textContent = filteredCustomers.filter(c => normalizePaymentStatus(c.paymentStatus) === 'paid').length;
                document.getElementById('totalPending').textContent = filteredCustomers.filter(c => normalizePaymentStatus(c.paymentStatus) === 'unpaid').length;
                document.getElementById('totalOverdue').textContent = filteredCustomers.filter(c => normalizePaymentStatus(c.paymentStatus) === 'overdue').length;
            }

            function customerMatchesSearch(customer) {
                if (!searchKeyword) {
                    return true;
                }

                return (customer.fullName && customer.fullName.toLowerCase().includes(searchKeyword)) ||
                    (customer.customerCode && customer.customerCode.toLowerCase().includes(searchKeyword)) ||
                    (customer.phone && String(customer.phone).toLowerCase().includes(searchKeyword));
            }

            function isInvoiceInSelectedPeriod(invoice) {
                if (!selectedYearFilter) {
                    return true;
                }
                const referenceDate = getInvoiceBillingDate(invoice);
                if (!referenceDate) {
                    return false;
                }

                const year = Number(selectedYearFilter);
                const month = selectedMonthFilter ? Number(selectedMonthFilter) : null;
                if (referenceDate.getFullYear() !== year) {
                    return false;
                }
                return !month || (referenceDate.getMonth() + 1) === month;
            }

            function summarizeCustomerByPeriod(customer) {
                const periodInvoices = Array.isArray(customer.invoices)
                    ? customer.invoices.filter(invoice => isInvoiceInSelectedPeriod(invoice))
                    : [];

                if (selectedYearFilter && periodInvoices.length === 0) {
                    return null;
                }

                const paidInvoices = periodInvoices.filter(inv => normalizePaymentStatus(inv.status) === 'paid');
                const unpaidInvoices = periodInvoices.filter(inv => {
                    const status = normalizePaymentStatus(inv.status);
                    return status !== 'paid' && status !== 'cancelled' && getOutstandingAmount(inv) > 0;
                });
                const totalTagihan = unpaidInvoices.reduce((sum, inv) => sum + getOutstandingAmount(inv), 0);

                return {
                    ...customer,
                    invoices: periodInvoices,
                    totalTagihan,
                    paidCount: paidInvoices.length,
                    pendingCount: unpaidInvoices.length,
                    overdueCount: unpaidInvoices.filter(inv => normalizePaymentStatus(inv.status) === 'overdue').length,
                    paymentStatus: resolveCustomerPaymentStatusFromInvoices(periodInvoices, customer.paymentStatus || 'unpaid')
                };
            }

            function applyCustomerFilters() {
                filteredCustomers = customers
                    .map(customer => summarizeCustomerByPeriod(customer))
                    .filter(customer => customer !== null)
                    .filter(customer => customerMatchesSearch(customer))
                    .filter(customer => customerMatchesPaymentStatus(customer));
                renderTable();
                updateStats();
            }
    
            // Setup search
            function setupSearch() {
                const searchInput = document.getElementById('searchInput');
                searchInput.addEventListener('input', function(e) {
                    searchKeyword = (e.target.value || '').trim().toLowerCase();
                    applyCustomerFilters();
                });
            }

            function setupFilterControls() {
                const yearSelect = document.getElementById('yearFilter');
                const monthSelect = document.getElementById('monthFilter');
                const statusSelect = document.getElementById('paymentStatusFilter');

                if (yearSelect) {
                    yearSelect.addEventListener('change', async function(event) {
                        await filterByYear(event.target.value);
                    });
                }

                if (monthSelect) {
                    monthSelect.addEventListener('change', function(event) {
                        filterByMonth(event.target.value);
                    });
                }

                if (statusSelect) {
                    statusSelect.addEventListener('change', function(event) {
                        filterByPaymentStatus(event.target.value);
                    });
                }
            }

            window.registerHistoryQuickAction(async function(action, customerId, serviceId = 0) {
                const normalizedCustomerId = Number(customerId);
                const normalizedServiceId = Number(serviceId || 0);

                switch (action) {
                    case 'view-history':
                        if (Number.isFinite(normalizedCustomerId) && normalizedCustomerId > 0) {
                            await viewPaymentHistory(normalizedCustomerId);
                        }
                        break;
                    case 'view-invoice':
                        if (Number.isFinite(normalizedCustomerId) && normalizedCustomerId > 0) {
                            await viewLatestInvoice(normalizedCustomerId);
                        }
                        break;
                    case 'pay-invoice':
                        if (Number.isFinite(normalizedCustomerId) && normalizedCustomerId > 0) {
                            await openPayInvoice(normalizedCustomerId);
                        }
                        break;
                    case 'print-history':
                        if (Number.isFinite(normalizedCustomerId) && normalizedCustomerId > 0) {
                            await openPrintHistory(normalizedCustomerId);
                        }
                        break;
                    case 'suspend-service':
                        if (Number.isFinite(normalizedCustomerId) && normalizedCustomerId > 0) {
                            confirmSuspend(normalizedCustomerId, Number.isFinite(normalizedServiceId) ? normalizedServiceId : 0);
                        }
                        break;
                    case 'activate-service':
                        if (Number.isFinite(normalizedCustomerId) && normalizedCustomerId > 0) {
                            confirmActivate(normalizedCustomerId, Number.isFinite(normalizedServiceId) ? normalizedServiceId : 0);
                        }
                        break;
                    case 'delete-customer':
                        if (Number.isFinite(normalizedCustomerId) && normalizedCustomerId > 0) {
                            confirmDeleteCustomer(normalizedCustomerId);
                        }
                        break;
                    default:
                        break;
                }
            });

            function setTextContent(id, value) {
                const element = document.getElementById(id);
                if (element) {
                    element.textContent = value;
                }
            }

            function populateHistorySummary(records) {
                const sourceRecords = Array.isArray(records) ? records : [];
                const orderedRecords = sourceRecords.slice().sort((left, right) => {
                    const leftDate = getHistoryReferenceDate(left);
                    const rightDate = getHistoryReferenceDate(right);
                    return (leftDate ? leftDate.getTime() : 0) - (rightDate ? rightDate.getTime() : 0);
                });

                const historyStart = orderedRecords.length > 0
                    ? getHistoryReferenceDate(orderedRecords[0])
                    : null;

                const latestPaymentRecord = sourceRecords
                    .filter(record => parseFlexibleDate(record && record.paymentDate))
                    .sort((left, right) => toDateMillis(right.paymentDate) - toDateMillis(left.paymentDate))[0];

                const totalPaid = sourceRecords.reduce((sum, record) => sum + asNumber(record && record.amountPaid), 0);

                setTextContent('historyCoverageStart', historyStart ? formatDate(historyStart) : '-');
                setTextContent('historyCoverageEnd', latestPaymentRecord ? formatDate(latestPaymentRecord.paymentDate) : 'Belum ada pembayaran');
                setTextContent('historyInvoiceCount', String(sourceRecords.length));
                setTextContent('historyPaidTotal', formatRupiah(totalPaid));
            }

            // Load global years for filter
            async function loadGlobalYears() {
                const yearSelect = document.getElementById('yearFilter');
                yearSelect.innerHTML = '<option value="">Semua Tahun</option>';
                const endpointYears = await fetchGlobalInvoiceYears();
                const years = endpointYears.length > 0 ? endpointYears : getAvailableInvoiceYears();

                years.forEach(year => {
                    const option = document.createElement('option');
                    option.value = year;
                    option.textContent = year;
                    yearSelect.appendChild(option);
                });

                return years;
            }
    
            // Filter by year
            async function filterByYear(year) {
                selectedYearFilter = year || '';
                const monthFilter = document.getElementById('monthFilter');
                if (year) {
                    monthFilter.disabled = false;
                    const now = new Date();
                    const currentMonth = String(now.getMonth() + 1);
                    const availableMonths = await loadMonthsForYear(year, monthFilter);
                    const monthPreference = String(year) === String(now.getFullYear())
                        ? currentMonth
                        : selectedMonthFilter;
                    selectedMonthFilter = resolvePreferredFilterValue(availableMonths, monthPreference);
                    monthFilter.value = selectedMonthFilter;
                } else {
                    selectedMonthFilter = '';
                    monthFilter.disabled = true;
                    monthFilter.innerHTML = '<option value="">Semua Bulan</option>';
                }
                applyCustomerFilters();
            }
    
            // Load months for year
            async function loadMonthsForYear(year, monthSelect) {
                monthSelect.innerHTML = '<option value="">Semua Bulan</option>';

                const endpointMonths = await fetchGlobalInvoiceMonths(year);
                const months = endpointMonths.length > 0 ? endpointMonths : getAvailableInvoiceMonthsByYear(year);
                const monthNames = [
                    'Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni',
                    'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember'
                ];

                months.forEach(month => {
                    const option = document.createElement('option');
                    option.value = month;
                    option.textContent = monthNames[month - 1] || String(month);
                    monthSelect.appendChild(option);
                });

                return months;
            }
    
            // Filter by month
            function filterByMonth(month) {
                selectedMonthFilter = month || '';
                applyCustomerFilters();
            }

            function customerMatchesPaymentStatus(customer) {
                if (!customer) {
                    return false;
                }
                if (!selectedPaymentStatusFilter || selectedPaymentStatusFilter === 'all') {
                    return true;
                }
                const selectedBucket = normalizePaymentStatus(selectedPaymentStatusFilter);
                const customerBucket = normalizePaymentStatus(customer.paymentStatus);
                return selectedBucket === customerBucket;
            }

            function filterByPaymentStatus(status) {
                selectedPaymentStatusFilter = status || 'all';
                if (paymentStatusDebounceHandle) {
                    clearTimeout(paymentStatusDebounceHandle);
                }
                applyCustomerFilters();
            }

            async function applyDefaultCurrentPeriodFilter() {
                await syncGlobalFilters(false);
            }

            async function fetchGlobalInvoiceYears() {
                try {
                    const response = await fetch('/pelanggan/api/invoices/years');
                    const result = await response.json();
                    if (result.success && Array.isArray(result.data)) {
                        return result.data.map(value => String(value));
                    }
                } catch (error) {
                    console.error('Error loading years from endpoint:', error);
                }
                return [];
            }

            async function fetchGlobalInvoiceMonths(year) {
                if (!year) {
                    return [];
                }

                try {
                    const response = await fetch(`/pelanggan/api/invoices/months?year=${encodeURIComponent(year)}`);
                    const result = await response.json();
                    if (result.success && Array.isArray(result.data)) {
                        return result.data.map(value => String(value));
                    }
                } catch (error) {
                    console.error('Error loading months from endpoint:', error);
                }
                return [];
            }

            async function syncGlobalFilters(preserveSelection = false) {
                const now = new Date();
                const currentYear = String(now.getFullYear());
                const currentMonth = String(now.getMonth() + 1);
                const yearSelect = document.getElementById('yearFilter');
                const monthSelect = document.getElementById('monthFilter');
                const previousYear = selectedYearFilter;
                const previousMonth = selectedMonthFilter;
                const availableYears = await loadGlobalYears();
                const preferredYear = resolvePreferredFilterValue(
                    availableYears,
                    preserveSelection ? (previousYear || currentYear) : currentYear
                );

                if (!preferredYear) {
                    selectedYearFilter = '';
                    selectedMonthFilter = '';
                    if (yearSelect) {
                        yearSelect.value = '';
                    }
                    if (monthSelect) {
                        monthSelect.disabled = true;
                        monthSelect.innerHTML = '<option value="">Semua Bulan</option>';
                        monthSelect.value = '';
                    }
                    applyCustomerFilters();
                    return;
                }

                selectedYearFilter = preferredYear;
                if (yearSelect) {
                    yearSelect.value = preferredYear;
                }

                if (monthSelect) {
                    monthSelect.disabled = false;
                }

                const availableMonths = await loadMonthsForYear(preferredYear, monthSelect);
                const monthPreference = preserveSelection && previousYear === preferredYear
                    ? (previousMonth || currentMonth)
                    : currentMonth;
                selectedMonthFilter = resolvePreferredFilterValue(availableMonths, monthPreference);

                if (monthSelect) {
                    monthSelect.value = selectedMonthFilter;
                }

                applyCustomerFilters();
            }

            function resolvePreferredFilterValue(values, preferredValue) {
                const normalizedValues = Array.isArray(values) ? values.map(value => String(value)) : [];
                const preferred = String(preferredValue || '');
                if (preferred && normalizedValues.includes(preferred)) {
                    return preferred;
                }
                return normalizedValues.length > 0 ? normalizedValues[0] : '';
            }

            function getInvoiceBillingDate(invoice) {
                if (!invoice) {
                    return null;
                }

                const normalizedStatus = normalizePaymentStatus(invoice.status);
                const rawDate = normalizedStatus === 'paid'
                    ? (invoice.paymentDate || invoice.billingMonth || invoice.dueDate)
                    : (invoice.billingMonth || invoice.dueDate || invoice.paymentDate);
                if (!rawDate) {
                    return null;
                }

                return parseFlexibleDate(rawDate);
            }

            function getAvailableInvoiceYears() {
                return [...new Set(globalInvoices
                    .map(invoice => getInvoiceBillingDate(invoice))
                    .filter(billingDate => billingDate !== null)
                    .map(billingDate => String(billingDate.getFullYear())))]
                    .sort((left, right) => Number(right) - Number(left));
            }

            function getAvailableInvoiceMonthsByYear(year) {
                if (!year) {
                    return [];
                }

                const normalizedYear = Number(year);
                return [...new Set(globalInvoices
                    .map(invoice => getInvoiceBillingDate(invoice))
                    .filter(billingDate => billingDate !== null && billingDate.getFullYear() === normalizedYear)
                    .map(billingDate => String(billingDate.getMonth() + 1)))]
                    .sort((left, right) => Number(right) - Number(left));
            }

            window.filterByYear = filterByYear;
            window.filterByMonth = filterByMonth;
            window.filterByPaymentStatus = filterByPaymentStatus;
    
            // Toggle select all
            function toggleSelectAll() {
                const selectAll = document.getElementById('selectAll');
                const checkboxes = document.querySelectorAll('#customerTableBody input[type="checkbox"]');
                
                if (selectAll.checked) {
                    checkboxes.forEach(cb => {
                        cb.checked = true;
                        selectedCustomers.add(parseInt(cb.value));
                    });
                } else {
                    checkboxes.forEach(cb => {
                        cb.checked = false;
                        selectedCustomers.delete(parseInt(cb.value));
                    });
                }
                
                updateSelectedUI();
            }
    
            // Toggle select customer
            function toggleSelectCustomer(customerId) {
                if (selectedCustomers.has(customerId)) {
                    selectedCustomers.delete(customerId);
                } else {
                    selectedCustomers.add(customerId);
                }
                
                updateSelectedUI();
            }
    
            // Update selected UI
            function updateSelectedUI() {
                const printBtn = document.getElementById('printSelectedBtn');
                const selectedCount = document.getElementById('selectedCount');
                const countNum = document.getElementById('countNum');
                
                if (selectedCustomers.size > 0) {
                    printBtn.style.display = 'inline-flex';
                    selectedCount.style.display = 'inline-flex';
                    countNum.textContent = selectedCustomers.size;
                } else {
                    printBtn.style.display = 'none';
                    selectedCount.style.display = 'none';
                }
            }
    
            // Print selected history
            function printSelectedHistory() {
                showAlert('info', 'Mencetak history pembayaran untuk ' + selectedCustomers.size + ' pelanggan...');
                // Implementation would open a print modal with selected customers
                window.print();
            }
    
            // Get status class
            function getStatusClass(status) {
                switch(status) {
                    case 'active': return 'active';
                    case 'suspended': return 'suspended';
                    case 'pending': return 'pending';
                    default: return 'inactive';
                }
            }
    
            // Get status text
            function getStatusText(status) {
                switch(status) {
                    case 'active': return 'Active';
                    case 'suspended': return 'Suspend';
                    case 'pending': return 'Belum Diaktivasi';
                    default: return 'Nonaktif';
                }
            }
    
            // Get payment badge class
            function getPaymentBadgeClass(status) {
                const normalizedStatus = normalizePaymentStatus(status);
                if (normalizedStatus === 'no-payment') {
                    return 'overdue';
                }
                return window.nmxBilling ? window.nmxBilling.getStatusClass(normalizedStatus) : (normalizedStatus === 'paid' ? 'paid' : (normalizedStatus === 'overdue' ? 'overdue' : 'pending'));
            }
    
            // Get payment text
            function getPaymentText(status) {
                const normalizedStatus = normalizePaymentStatus(status);
                if (normalizedStatus === 'no-payment') {
                    return 'Tidak Bayar';
                }
                return window.nmxBilling ? window.nmxBilling.getStatusLabel(normalizedStatus) : (normalizedStatus === 'paid' ? 'Lunas' : (normalizedStatus === 'overdue' ? 'Jatuh Tempo' : 'Belum Lunas'));
            }

            function normalizePaymentStatus(status) {
                const normalized = String(status || '').toLowerCase();
                if (normalized === 'no-payment' || normalized === 'no_payment' || normalized === 'tidak_bayar' || normalized === 'tidak-bayar') {
                    return 'no-payment';
                }
                if (normalized === 'paid' || normalized === 'lunas' || normalized === 'sudah-bayar') {
                    return 'paid';
                }
                if (normalized === 'overdue' || normalized === 'jatuh-tempo' || normalized === 'jatuh_tempo') {
                    return 'overdue';
                }
                if (
                    normalized === 'partial' ||
                    normalized === 'pending' ||
                    normalized === 'unpaid' ||
                    normalized === 'belum-bayar' ||
                    normalized === 'belum-lunas' ||
                    normalized === 'belum_lunas'
                ) {
                    return 'unpaid';
                }
                return 'unpaid';
            }
    
            // Format rupiah
            function formatRupiah(amount) {
                return `Rp. ${new Intl.NumberFormat('id-ID', { minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(asNumber(amount))}`;
            }

            function formatDate(dateValue, options = {}) {
                if (!dateValue) {
                    return '-';
                }

                const date = parseFlexibleDate(dateValue);
                if (!date) {
                    return '-';
                }
                return date.toLocaleDateString('id-ID', options);
            }

            function parseFlexibleDate(value) {
                if (!value) {
                    return null;
                }

                if (value instanceof Date) {
                    return Number.isNaN(value.getTime()) ? null : value;
                }

                if (Array.isArray(value) && value.length >= 3) {
                    const year = Number(value[0]);
                    const month = Number(value[1]);
                    const day = Number(value[2]);
                    if (Number.isFinite(year) && Number.isFinite(month) && Number.isFinite(day)) {
                        const parsedArrayDate = new Date(year, month - 1, day);
                        return Number.isNaN(parsedArrayDate.getTime()) ? null : parsedArrayDate;
                    }
                }

                if (typeof value === 'object') {
                    const year = Number(value.year);
                    const month = Number(value.monthValue || value.month);
                    const day = Number(value.dayOfMonth || value.day);
                    if (Number.isFinite(year) && Number.isFinite(month) && Number.isFinite(day)) {
                        const parsedObjectDate = new Date(year, month - 1, day);
                        return Number.isNaN(parsedObjectDate.getTime()) ? null : parsedObjectDate;
                    }
                }

                const parsedDate = new Date(value);
                return Number.isNaN(parsedDate.getTime()) ? null : parsedDate;
            }

            function toDateMillis(value) {
                const parsedDate = parseFlexibleDate(value);
                return parsedDate ? parsedDate.getTime() : 0;
            }

            async function viewInvoiceDetail(invoiceId) {
                try {
                    const response = await fetch(`/api/invoices/${invoiceId}`);
                    const result = await response.json();

                    if (!result.success || !result.data) {
                        showAlert('error', result.message || 'Detail invoice tidak ditemukan');
                        return;
                    }

                    const invoice = result.data;
                    document.getElementById('detailInvoiceNumber').textContent = invoice.invoiceNumber || '-';
                    document.getElementById('detailInvoiceStatus').textContent = getPaymentText(invoice.status || 'unpaid');
                    document.getElementById('detailBillingMonth').textContent = formatDate(invoice.billingMonth, { month: 'long', year: 'numeric' });
                    document.getElementById('detailDueDate').textContent = formatDate(invoice.dueDate);
                    document.getElementById('detailTotalAmount').textContent = formatRupiah(invoice.totalAmount);
                    document.getElementById('detailAmountPaid').textContent = formatRupiah(invoice.amountPaid || 0);
                    document.getElementById('detailPaymentDate').textContent = formatDate(invoice.paymentDate);
                    document.getElementById('detailPaymentMethod').textContent = invoice.paymentMethod || '-';
                    document.getElementById('detailInvoiceNotes').textContent = invoice.notes || invoice.paymentNotes || '-';

                    openModal('invoiceDetailModal');
                } catch (error) {
                    console.error('Error loading invoice detail:', error);
                    showAlert('error', 'Gagal memuat detail invoice');
                }
            }
    
            // View payment history
            async function viewPaymentHistory(customerId) {
                currentCustomerId = customerId;
                
                try {
                    const [customerResponse, invoicesResponse] = await Promise.all([
                        fetch(`/pelanggan/api/customers/${customerId}`),
                        fetch(`/api/customers/${customerId}/invoices`)
                    ]);
                    const customerResult = await customerResponse.json();
                    const invoicesResult = await invoicesResponse.json();
                    
                    if (customerResult.success) {
                        currentHistoryCustomerProfile = customerResult.data || null;
                        currentHistoryCustomerLabel = customerResult.data.fullName + ' (' + customerResult.data.customerCode + ')';
                        document.getElementById('historyCustomerName').textContent = currentHistoryCustomerLabel;

                        currentHistorySourceData = buildCustomerHistoryRecords(
                            Array.isArray(invoicesResult.data) ? invoicesResult.data : []
                        );
                        const historyYearFilter = document.getElementById('historyYearFilter');
                        const historyMonthFilter = document.getElementById('historyMonthFilter');
                        historyYearFilter.value = '';
                        historyMonthFilter.value = '';

                        loadHistoryYears();
                        loadHistoryMonths('');
                        loadFilteredHistory();
                        openModal('historyModal');
                    }
                } catch (error) {
                    console.error('Error loading customer for history:', error);
                    showAlert('error', 'Gagal memuat riwayat pembayaran');
                }
            }
    
            // Load history years
            function loadHistoryYears() {
                const yearSelect = document.getElementById('historyYearFilter');
                const monthSelect = document.getElementById('historyMonthFilter');
                yearSelect.innerHTML = '<option value="">Semua Tahun</option>';
                monthSelect.innerHTML = '<option value="">Semua Bulan</option>';
                monthSelect.disabled = true;

                getHistoryAvailableYears().forEach(year => {
                    const option = document.createElement('option');
                    option.value = year;
                    option.textContent = year;
                    yearSelect.appendChild(option);
                });
            }
    
            // Load history months for year
            function loadHistoryMonths(year) {
                const monthSelect = document.getElementById('historyMonthFilter');
                monthSelect.innerHTML = '<option value="">Semua Bulan</option>';
                
                if (!year) {
                    monthSelect.disabled = true;
                    return;
                }
                
                monthSelect.disabled = false;

                const months = ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni', 'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember'];
                getHistoryAvailableMonthsByYear(year).forEach(month => {
                    const option = document.createElement('option');
                    option.value = month;
                    option.textContent = months[month - 1];
                    monthSelect.appendChild(option);
                });
            }
    
            // Load filtered history
            function loadFilteredHistory() {
                if (!currentCustomerId) {
                    return;
                }

                const year = document.getElementById('historyYearFilter').value;
                const month = document.getElementById('historyMonthFilter').value;
                const monthSelect = document.getElementById('historyMonthFilter');
                
                if (!year) {
                    monthSelect.value = '';
                    monthSelect.innerHTML = '<option value="">Semua Bulan</option>';
                    monthSelect.disabled = true;
                } else {
                    const previousMonth = month;
                    loadHistoryMonths(year);
                    if (previousMonth && getHistoryAvailableMonthsByYear(year).includes(String(previousMonth))) {
                        monthSelect.value = previousMonth;
                    }
                }

                const tbody = document.getElementById('historyTableBody');
                currentHistoryData = currentHistorySourceData.filter(payment => {
                    const referenceDate = getHistoryReferenceDate(payment);
                    if (!referenceDate) {
                        return !year && !month;
                    }
                    if (year && referenceDate.getFullYear() !== Number(year)) {
                        return false;
                    }
                    return !month || (referenceDate.getMonth() + 1) === Number(month);
                });

                populateHistorySummary(currentHistoryData);

                if (currentHistoryData.length > 0) {
                    tbody.innerHTML = currentHistoryData.map(payment => `
                        <tr>
                            <td><span class="customer-code">${payment.invoiceNumber || '-'}</span></td>
                            <td>${payment.invoiceTypeLabel || 'Pembayaran Langganan Bulanan'}</td>
                            <td>${formatDate(payment.dueDate)}</td>
                            <td>${formatRupiah(payment.invoiceAmount || 0)}</td>
                            <td>${formatRupiah(payment.amountPaid || 0)}</td>
                            <td>${formatDate(payment.paymentDate)}</td>
                            <td>${payment.paymentMethod || '-'}</td>
                            <td>
                                <span class="payment-badge ${getPaymentBadgeClass(payment.status)}">
                                    ${getPaymentText(payment.status)}
                                </span>
                            </td>
                            <td>
                                <button class="btn btn-sm btn-secondary" onclick="viewInvoiceDetail(${payment.invoiceId})">
                                    <i class="fas fa-eye"></i> Lihat
                                </button>
                            </td>
                        </tr>
                    `).join('');
                } else {
                    tbody.innerHTML = `
                        <tr>
                            <td colspan="9" class="table-empty">
                                <p>Belum ada riwayat pembayaran sejak pemasangan atau aktivasi pelanggan.</p>
                            </td>
                        </tr>
                    `;
                }
            }
    
            // Print current history
            function printCurrentHistory() {
                openPaymentPrintWindow(
                    'History Pembayaran Pelanggan',
                    currentHistoryCustomerLabel,
                    currentHistoryData
                );
            }

            function buildCustomerHistoryRecords(invoices) {
                return (Array.isArray(invoices) ? invoices : [])
                    .map(invoice => ({
                        id: invoice.id,
                        invoiceId: invoice.id,
                        invoiceNumber: invoice.invoiceNumber,
                        customerId: invoice.customerId,
                        customerCode: invoice.customerCode,
                        customerName: invoice.customerName,
                        billingMonth: invoice.billingMonth,
                        dueDate: invoice.dueDate,
                        invoiceAmount: invoice.totalAmount,
                        amountPaid: invoice.amountPaid,
                        outstandingAmount: invoice.outstandingAmount,
                        paymentDate: invoice.paymentDate,
                        paymentMethod: invoice.paymentMethod,
                        status: invoice.status,
                        invoiceType: invoice.invoiceType,
                        invoiceTypeLabel: invoice.invoiceTypeLabel,
                        notes: invoice.notes || invoice.paymentNotes || ''
                    }))
                    .sort((left, right) => {
                        const leftReference = getHistoryReferenceDate(left);
                        const rightReference = getHistoryReferenceDate(right);
                        const leftTime = leftReference ? leftReference.getTime() : 0;
                        const rightTime = rightReference ? rightReference.getTime() : 0;
                        if (leftTime !== rightTime) {
                            return leftTime - rightTime;
                        }

                        const leftPaymentTime = toDateMillis(left.paymentDate);
                        const rightPaymentTime = toDateMillis(right.paymentDate);
                        if (leftPaymentTime !== rightPaymentTime) {
                            return leftPaymentTime - rightPaymentTime;
                        }

                        return asNumber(left.invoiceId) - asNumber(right.invoiceId);
                    });
            }

            function getHistoryReferenceDate(record) {
                return parseFlexibleDate(record && (record.billingMonth || record.dueDate || record.paymentDate));
            }

            function getHistoryAvailableYears() {
                return [...new Set(currentHistorySourceData
                    .map(record => getHistoryReferenceDate(record))
                    .filter(referenceDate => referenceDate !== null)
                    .map(referenceDate => String(referenceDate.getFullYear())))]
                    .sort((left, right) => Number(left) - Number(right));
            }

            function getHistoryAvailableMonthsByYear(year) {
                if (!year) {
                    return [];
                }

                const normalizedYear = Number(year);
                return [...new Set(currentHistorySourceData
                    .map(record => getHistoryReferenceDate(record))
                    .filter(referenceDate => referenceDate !== null && referenceDate.getFullYear() === normalizedYear)
                    .map(referenceDate => String(referenceDate.getMonth() + 1)))]
                    .sort((left, right) => Number(left) - Number(right));
            }

            async function viewLatestInvoice(customerId) {
                try {
                    const response = await fetch(`/api/customers/${customerId}/invoices`);
                    const result = await response.json();

                    if (!result.success || !Array.isArray(result.data) || result.data.length === 0) {
                        showAlert('info', 'Belum ada invoice untuk pelanggan ini');
                        return;
                    }

                    const latestInvoice = [...result.data].sort((left, right) =>
                        toDateMillis(right.dueDate || right.billingMonth) - toDateMillis(left.dueDate || left.billingMonth)
                    )[0];

                    await viewInvoiceDetail(latestInvoice.id);
                } catch (error) {
                    console.error('Error loading latest invoice:', error);
                    showAlert('error', 'Gagal memuat invoice pelanggan');
                }
            }
    
            // Open create invoice
            async function openCreateInvoice(customerId) {
                try {
                    const response = await fetch(`/pelanggan/api/customers/${customerId}`);
                    const result = await response.json();
                    
                    if (result.success) {
                        document.getElementById('invoiceCustomerId').value = customerId;
                        document.getElementById('invoiceCustomerName').textContent = result.data.fullName;
                        
                        // Get latest invoice to get monthly fee
                    const invoiceResponse = await fetch(`/api/customers/${customerId}/invoices`);
                        const invoiceResult = await invoiceResponse.json();
                        
                        if (invoiceResult.success && invoiceResult.data.length > 0) {
                            const lastInvoice = [...invoiceResult.data].sort((left, right) =>
                                toDateMillis(right.dueDate || right.billingMonth) - toDateMillis(left.dueDate || left.billingMonth)
                            )[0];
                            document.getElementById('invoiceMonthlyFee').value = lastInvoice.monthlyFee || 0;
                            const anchorDate = parseFlexibleDate(lastInvoice.dueDate || lastInvoice.billingMonth) || new Date();
                            const nextDueDate = addMonthsSameDay(anchorDate, 1);
                            document.getElementById('invoiceBillingMonth').value = formatDateInput(nextDueDate).slice(0, 7);
                            document.getElementById('invoiceDueDate').value = formatDateInput(nextDueDate);
                        } else {
                            const nextDueDate = addMonthsSameDay(new Date(), 1);
                            document.getElementById('invoiceBillingMonth').value = formatDateInput(nextDueDate).slice(0, 7);
                            document.getElementById('invoiceDueDate').value = formatDateInput(nextDueDate);
                        }
                        
                        openModal('invoiceModal');
                    }
                } catch (error) {
                    console.error('Error opening create invoice:', error);
                    showAlert('error', 'Gagal memuat data pelanggan');
                }
            }
    
            // Save invoice
            async function saveInvoice() {
                const customerId = document.getElementById('invoiceCustomerId').value;
                const billingMonth = document.getElementById('invoiceBillingMonth').value;
                const dueDate = document.getElementById('invoiceDueDate').value;
                const monthlyFee = document.getElementById('invoiceMonthlyFee').value;
                const installationFee = document.getElementById('invoiceInstallationFee').value;
                const notes = document.getElementById('invoiceNotes').value;
                
                const dto = {
                    customerId: parseInt(customerId),
                    billingMonth: dueDate || (billingMonth + '-01'),
                    dueDate: dueDate,
                    monthlyFee: parseFloat(monthlyFee),
                    installationFee: parseFloat(installationFee || 0),
                    notes: notes
                };
                
                try {
                    const response = await fetch('/api/invoices', {
                        method: 'POST',
                        headers: requestHeaders(true),
                        credentials: 'same-origin',
                        body: JSON.stringify(dto)
                    });
                    
                    const result = await response.json();
                    
                    if (result.success) {
                        showAlert('success', 'Invoice berhasil dibuat!');
                        closeModal('invoiceModal');
                        if (window.nmxBilling) {
                            window.nmxBilling.emit('invoice-created');
                        }
                        loadCustomers(true);
                    } else {
                        showAlert('error', result.message);
                    }
                } catch (error) {
                    console.error('Error creating invoice:', error);
                    showAlert('error', 'Gagal membuat invoice');
                }
            }
    
            // Open pay invoice
            async function openPayInvoice(customerId) {
                try {
                    const response = await fetch(`/api/customers/${customerId}/invoices`);
                    const result = await response.json();
                    
                    if (result.success && result.data.length > 0) {
                        const unpaidInvoice = result.data
                            .filter(inv => {
                                const normalizedStatus = normalizePaymentStatus(inv && inv.status);
                                return normalizedStatus !== 'paid'
                                    && normalizedStatus !== 'cancelled'
                                    && normalizedStatus !== 'no-payment';
                            })
                            .sort((left, right) => {
                                const leftTime = toDateMillis(left.dueDate || left.billingMonth);
                                const rightTime = toDateMillis(right.dueDate || right.billingMonth);
                                return leftTime - rightTime;
                            })[0];
                        
                        if (unpaidInvoice) {
                            await openPayInvoiceFromHistory(unpaidInvoice.id);
                        } else {
                            showAlert('info', 'Semua invoice sudah lunas!');
                        }
                    } else {
                        showAlert('info', 'Belum ada invoice untuk pelanggan ini');
                    }
                } catch (error) {
                    console.error('Error opening pay invoice:', error);
                }
            }
    
            // Open pay invoice from history
            async function openPayInvoiceFromHistory(invoiceId) {
                try {
                    const response = await fetch(`/api/invoices/${invoiceId}`);
                    const result = await response.json();

                    if (!result.success || !result.data) {
                        showAlert('error', result.message || 'Invoice tidak ditemukan');
                        return;
                    }

                    const invoice = result.data;
                    const amountToPay = getOutstandingAmount(invoice) || asNumber(invoice.totalAmount);
                    if (amountToPay <= 0
                        || normalizePaymentStatus(invoice.status) === 'paid'
                        || normalizePaymentStatus(invoice.status) === 'cancelled'
                        || normalizePaymentStatus(invoice.status) === 'no-payment') {
                        showAlert('info', 'Invoice ini tidak memiliki sisa tagihan');
                        return;
                    }

                    document.getElementById('payInvoiceId').value = invoice.id;
                    document.getElementById('payInvoiceNumber').textContent = invoice.invoiceNumber || '-';
                    document.getElementById('payTotalAmount').textContent = formatRupiah(amountToPay);
                    document.getElementById('payAmount').value = amountToPay;
                    document.getElementById('payMethod').value = 'cash';
                    document.getElementById('payNotes').value = '';

                    closeModal('historyModal');
                    openModal('payModal');
                } catch (error) {
                    console.error('Error opening pay invoice from quick action:', error);
                    showAlert('error', 'Gagal memuat detail invoice');
                }
            }
    
            // Process payment
            async function processPayment() {
                const invoiceId = document.getElementById('payInvoiceId').value;
                const amount = document.getElementById('payAmount').value;
                const method = document.getElementById('payMethod').value;
                const notes = document.getElementById('payNotes').value;

                if (!invoiceId) {
                    showAlert('error', 'Invoice tidak ditemukan');
                    return;
                }

                if (!Number.isFinite(parseFloat(amount)) || parseFloat(amount) <= 0) {
                    showAlert('error', 'Nominal pembayaran harus lebih besar dari 0');
                    return;
                }
                
                try {
                    const response = await fetch(`/api/invoices/${invoiceId}/payments`, {
                        method: 'POST',
                        headers: requestHeaders(true),
                        credentials: 'same-origin',
                        body: JSON.stringify({
                            amount: parseFloat(amount),
                            paymentDate: new Date().toISOString().slice(0, 10),
                            paymentMethod: method,
                            notes: notes
                        })
                    });
                    
                    const result = await response.json();
                    
                    if (result.success) {
                        showAlert('success', 'Pembayaran berhasil!');
                        closeModal('payModal');
                        if (window.nmxBilling) {
                            window.nmxBilling.emit('payment');
                        }
                        loadCustomers(true);
                    } else {
                        showAlert('error', result.message);
                    }
                } catch (error) {
                    console.error('Error processing payment:', error);
                    showAlert('error', 'Gagal memproses pembayaran');
                }
            }
    
            // Open print history
            async function openPrintHistory(customerId) {
                try {
                    const response = await fetch(`/pelanggan/api/customers/${customerId}`);
                    const result = await response.json();
                    
                    if (result.success) {
                        currentCustomerId = customerId;
                        currentHistoryCustomerLabel = result.data.fullName + ' (' + result.data.customerCode + ')';
                        document.getElementById('printCustomerName').textContent = currentHistoryCustomerLabel;
                        
                        // Load years
                        const yearSelect = document.getElementById('printYear');
                        yearSelect.innerHTML = '<option value="">Pilih Tahun</option>';
                        
                        const yearsResponse = await fetch(`/api/customers/${customerId}/payments/years`);
                        const yearsResult = await yearsResponse.json();
                        
                        if (yearsResult.success && yearsResult.data) {
                            yearsResult.data.forEach(year => {
                                const option = document.createElement('option');
                                option.value = year;
                                option.textContent = year;
                                yearSelect.appendChild(option);
                            });
                        }
                        
                        updatePrintOptions();
                        openModal('printModal');
                    }
                } catch (error) {
                    console.error('Error opening print history:', error);
                    showAlert('error', 'Gagal memuat data');
                }
            }
    
            // Update print options
            function updatePrintOptions() {
                const filter = document.querySelector('input[name="printFilter"]:checked').value;
                const yearSelect = document.getElementById('printYear');
                const monthSelect = document.getElementById('printMonth');
                
                if (filter === 'year') {
                    yearSelect.disabled = false;
                    monthSelect.disabled = true;
                } else if (filter === 'month') {
                    yearSelect.disabled = false;
                    monthSelect.disabled = false;
                    updatePrintMonthOptions();
                } else {
                    yearSelect.disabled = true;
                    monthSelect.disabled = true;
                }
            }
    
            // Update print month options
            async function updatePrintMonthOptions() {
                const year = document.getElementById('printYear').value;
                const monthSelect = document.getElementById('printMonth');
                
                monthSelect.innerHTML = '<option value="">Pilih Bulan</option>';
                
                if (!year) return;
                
                try {
                    const response = await fetch(`/api/customers/${currentCustomerId}/payments/months?year=${year}`);
                    const result = await response.json();
                    
                    if (result.success && result.data) {
                        const months = ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni', 'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember'];
                        result.data.forEach(month => {
                            const option = document.createElement('option');
                            option.value = month;
                            option.textContent = months[month - 1];
                            monthSelect.appendChild(option);
                        });
                    }
                } catch (error) {
                    console.error('Error loading months:', error);
                }
            }
    
            // Execute print
            function executePrint() {
                if (!currentCustomerId) {
                    showAlert('error', 'Pelanggan belum dipilih');
                    return;
                }

                const filter = document.querySelector('input[name="printFilter"]:checked').value;
                const year = document.getElementById('printYear').value;
                const month = document.getElementById('printMonth').value;
                const params = [];

                if (filter === 'year' || filter === 'month') {
                    if (!year) {
                        showAlert('error', 'Tahun harus dipilih');
                        return;
                    }
                    params.push(`year=${encodeURIComponent(year)}`);
                }

                if (filter === 'month') {
                    if (!month) {
                        showAlert('error', 'Bulan harus dipilih');
                        return;
                    }
                    params.push(`month=${encodeURIComponent(month)}`);
                }

                let url = `/api/customers/${currentCustomerId}/payments`;
                if (params.length > 0) {
                    url += `?${params.join('&')}`;
                }

                fetch(url)
                    .then(response => response.json())
                    .then(result => {
                        if (!result.success) {
                            throw new Error(result.message || 'Gagal memuat data cetak');
                        }

                        openPaymentPrintWindow(
                            'History Pembayaran Pelanggan',
                            currentHistoryCustomerLabel,
                            Array.isArray(result.data) ? result.data : []
                        );
                        closeModal('printModal');
                    })
                    .catch(error => {
                        console.error('Error printing payment history:', error);
                        showAlert('error', error.message || 'Gagal menyiapkan data cetak');
                    });
            }
    
            // Confirm suspend
            function confirmSuspend(customerId, serviceId) {
                currentCustomerId = customerId;
                currentServiceId = serviceId;
                
                document.getElementById('confirmTitle').textContent = 'Suspend Layanan';
                document.getElementById('confirmMessage').textContent = 'Apakah Anda yakin ingin menangguhkan layanan pelanggan ini?';
                document.getElementById('confirmIcon').className = 'confirm-icon warning';
                document.getElementById('confirmIcon').innerHTML = '<i class="fas fa-pause"></i>';
                document.getElementById('confirmBtn').className = 'btn btn-primary';
                
                confirmCallback = executeSuspend;
                openModal('confirmModal');
            }
    
            // Execute suspend
            async function executeSuspend() {
                if (!currentServiceId || currentServiceId === 0) {
                    showAlert('error', 'Layanan tidak ditemukan');
                    closeModal('confirmModal');
                    return;
                }
                
                try {
                    const response = await fetch(`/pelanggan/api/services/${currentServiceId}/suspend`, {
                        method: 'POST',
                        headers: requestHeaders(),
                        credentials: 'same-origin'
                    });
                    
                    const result = await response.json();
                    
                    if (result.success) {
                        showAlert('success', 'Layanan berhasil ditangguhkan!');
                        loadCustomers(true);
                    } else {
                        showAlert('error', result.message);
                    }
                } catch (error) {
                    console.error('Error suspending service:', error);
                    showAlert('error', 'Gagal menangguhkan layanan');
                }
                
                closeModal('confirmModal');
            }
    
            // Confirm activate
            function confirmActivate(customerId, serviceId) {
                currentCustomerId = customerId;
                currentServiceId = serviceId;
                
                document.getElementById('confirmTitle').textContent = 'Aktifkan Layanan';
                document.getElementById('confirmMessage').textContent = 'Apakah Anda yakin ingin mengaktifkan kembali layanan pelanggan ini?';
                document.getElementById('confirmIcon').className = 'confirm-icon success';
                document.getElementById('confirmIcon').innerHTML = '<i class="fas fa-play"></i>';
                document.getElementById('confirmBtn').className = 'btn btn-primary';
                
                confirmCallback = executeActivate;
                openModal('confirmModal');
            }
    
            // Execute activate
            async function executeActivate() {
                if (!currentServiceId || currentServiceId === 0) {
                    showAlert('error', 'Layanan tidak ditemukan');
                    closeModal('confirmModal');
                    return;
                }
                
                try {
                    const response = await fetch(`/pelanggan/api/services/${currentServiceId}/activate`, {
                        method: 'POST',
                        headers: requestHeaders(),
                        credentials: 'same-origin'
                    });
                    
                    const result = await response.json();
                    
                    if (result.success) {
                        showAlert('success', 'Layanan berhasil diaktifkan!');
                        if (window.nmxBilling) {
                            window.nmxBilling.emit('activation');
                        }
                        loadCustomers(true);
                    } else {
                        showAlert('error', result.message);
                    }
                } catch (error) {
                    console.error('Error activating service:', error);
                    showAlert('error', 'Gagal mengaktifkan layanan');
                }
                
                closeModal('confirmModal');
            }
    
            // Confirm delete customer
            function confirmDeleteCustomer(customerId) {
                currentCustomerId = customerId;
                
                document.getElementById('confirmTitle').textContent = 'Hapus Pelanggan';
                document.getElementById('confirmMessage').textContent = 'PERINGATAN: Tindakan ini tidak dapat dibatalkan! Semua data pelanggan akan dihapus permanen.';
                document.getElementById('confirmIcon').className = 'confirm-icon danger';
                document.getElementById('confirmIcon').innerHTML = '<i class="fas fa-exclamation-triangle"></i>';
                document.getElementById('confirmBtn').className = 'btn btn-danger';
                
                confirmCallback = executeDeleteCustomer;
                openModal('confirmModal');
            }
    
            // Execute delete customer
            async function executeDeleteCustomer() {
                try {
                    const response = await fetch(`/pelanggan/api/customers/${currentCustomerId}`, {
                        method: 'DELETE',
                        headers: requestHeaders(),
                        credentials: 'same-origin'
                    });
                    
                    const result = await response.json();
                    
                    if (result.success) {
                        showAlert('success', 'Pelanggan berhasil dihapus!');
                        loadCustomers(true);
                    } else {
                        showAlert('error', result.message);
                    }
                } catch (error) {
                    console.error('Error deleting customer:', error);
                    showAlert('error', 'Gagal menghapus pelanggan');
                }
                
                closeModal('confirmModal');
                document.getElementById('confirmBtn').className = 'btn btn-primary';
            }
    
            // Execute confirm callback
            function executeConfirm() {
                if (confirmCallback) {
                    confirmCallback();
                }
            }
    
            // Modal functions
            function openModal(modalId) {
                const modal = document.getElementById(modalId);
                if (!modal) return;
                modal.classList.add('open');
                document.body.style.overflow = 'hidden';
            }
    
            function closeModal(modalId) {
                const modal = document.getElementById(modalId);
                if (!modal) return;
                modal.classList.remove('open');
                const anyModalOpen = document.querySelector('.modal.open');
                document.body.style.overflow = anyModalOpen ? 'hidden' : '';
            }
    
            // Show alert
            function showAlert(type, message) {
                if (window.nmxNotify) {
                    window.nmxNotify({
                        type: type,
                        message: message
                    });
                    return;
                }

                const alertContainer = document.getElementById('alertContainer');
                if (!alertContainer) return;
                
                const alertDiv = document.createElement('div');
                alertDiv.className = `alert-message ${type}`;
                
                let icon = 'info-circle';
                if (type === 'success') icon = 'check-circle';
                if (type === 'error') icon = 'exclamation-circle';
                
                alertDiv.innerHTML = `
                    <i class="fas fa-${icon}"></i>
                    <span>${message}</span>
                `;
                
                alertContainer.appendChild(alertDiv);
                
                setTimeout(() => {
                    alertDiv.remove();
                }, 5000);
            }

            function openPaymentPrintWindow(title, customerLabel, rows) {
                const printWindow = window.open('', '_blank', 'width=960,height=720');
                if (!printWindow) {
                    showAlert('error', 'Popup diblokir browser. Izinkan popup untuk mencetak.');
                    return;
                }

                const paymentRows = Array.isArray(rows) ? rows : [];
                const tableRows = paymentRows.length > 0
                    ? paymentRows.map(payment => `
                        <tr>
                            <td>${escapeHtml(payment.invoiceNumber || '-')}</td>
                            <td>${escapeHtml(payment.invoiceTypeLabel || 'Pembayaran Langganan Bulanan')}</td>
                            <td>${formatDate(payment.dueDate)}</td>
                            <td>${formatRupiah(payment.invoiceAmount || 0)}</td>
                            <td>${formatRupiah(payment.amountPaid || 0)}</td>
                            <td>${formatDate(payment.paymentDate)}</td>
                            <td>${escapeHtml(payment.paymentMethod || '-')}</td>
                            <td>${escapeHtml(getPaymentText(payment.status || 'unpaid'))}</td>
                        </tr>
                    `).join('')
                    : `
                        <tr>
                            <td colspan="8" style="text-align:center;padding:24px;">Belum ada riwayat pembayaran.</td>
                        </tr>
                    `;

                printWindow.document.write(`
                    <!DOCTYPE html>
                    <html lang="id">
                    <head>
                        <meta charset="UTF-8">
                        <title>${escapeHtml(title)}</title>
                        <style>
                            body { font-family: Arial, sans-serif; margin: 24px; color: #111827; }
                            h1 { margin: 0 0 8px; font-size: 24px; }
                            p { margin: 0 0 4px; color: #4b5563; }
                            table { width: 100%; border-collapse: collapse; margin-top: 24px; }
                            th, td { border: 1px solid #d1d5db; padding: 10px; font-size: 12px; text-align: left; }
                            th { background: #f3f4f6; }
                            .meta { margin-top: 16px; }
                        </style>
                    </head>
                    <body>
                        <h1>${escapeHtml(title)}</h1>
                        <p>${escapeHtml(customerLabel || '-')}</p>
                        <p class="meta">Dicetak: ${new Date().toLocaleString('id-ID')}</p>
                        <table>
                            <thead>
                                <tr>
                                    <th>No Invoice</th>
                                    <th>Jenis Pembayaran</th>
                                    <th>Jatuh Tempo</th>
                                    <th>Total Invoice</th>
                                    <th>Nominal Bayar</th>
                                    <th>Tanggal Bayar</th>
                                    <th>Metode</th>
                                    <th>Status Invoice</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${tableRows}
                            </tbody>
                        </table>
                    </body>
                    </html>
                `);
                printWindow.document.close();
                printWindow.focus();
                setTimeout(() => {
                    printWindow.print();
                }, 250);
            }

            Object.assign(window, {
                closeModal,
                executeConfirm,
                executePrint,
                handleHistoryQuickAction: window.handleHistoryQuickAction,
                loadFilteredHistory,
                openPayInvoice,
                openPrintHistory,
                printCurrentHistory,
                printSelectedHistory,
                processPayment,
                saveInvoice,
                toggleSelectAll,
                toggleSelectCustomer,
                updatePrintMonthOptions,
                updatePrintOptions,
                viewInvoiceDetail,
                viewLatestInvoice,
                viewPaymentHistory,
                confirmActivate,
                confirmDeleteCustomer,
                confirmSuspend
            });
    
            // Clock function
    
            // Toggle Menu}
        

            (function() {
                const historyState = {
                    customerId: null,
                    customerLabel: '-',
                    allRows: [],
                    filteredRows: []
                };

                function updateHistorySummary(rows) {
                    const normalizedRows = Array.isArray(rows) ? rows : [];
                    const orderedRows = normalizedRows.slice().sort((left, right) => {
                        const leftRef = getHistoryReferenceDate(left);
                        const rightRef = getHistoryReferenceDate(right);
                        return (leftRef ? leftRef.getTime() : 0) - (rightRef ? rightRef.getTime() : 0);
                    });

                    const historyStart = orderedRows.length > 0 ? getHistoryReferenceDate(orderedRows[0]) : null;
                    const latestPaymentRow = normalizedRows
                        .filter(row => parseDate(row && row.paymentDate))
                        .sort((left, right) => toDateMillis(right.paymentDate) - toDateMillis(left.paymentDate))[0];
                    const totalPaid = normalizedRows.reduce((sum, row) => sum + asNumber(row && row.amountPaid), 0);

                    const assignText = (id, value) => {
                        const element = document.getElementById(id);
                        if (element) {
                            element.textContent = value;
                        }
                    };

                    assignText('historyCoverageStart', historyStart ? formatDate(historyStart) : '-');
                    assignText('historyCoverageEnd', latestPaymentRow ? formatDate(latestPaymentRow.paymentDate) : 'Belum ada pembayaran');
                    assignText('historyInvoiceCount', String(normalizedRows.length));
                    assignText('historyPaidTotal', formatRupiah(totalPaid));
                }

                const monthNames = ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni', 'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember'];

                function asNumber(value) {
                    const parsed = Number(value);
                    return Number.isFinite(parsed) ? parsed : 0;
                }

                function parseDate(value) {
                    if (!value) {
                        return null;
                    }

                    if (value instanceof Date) {
                        return Number.isNaN(value.getTime()) ? null : value;
                    }

                    if (Array.isArray(value) && value.length >= 3) {
                        const parsedArray = new Date(Number(value[0]), Number(value[1]) - 1, Number(value[2]));
                        return Number.isNaN(parsedArray.getTime()) ? null : parsedArray;
                    }

                    if (typeof value === 'object') {
                        const year = Number(value.year);
                        const month = Number(value.monthValue || value.month);
                        const day = Number(value.dayOfMonth || value.day || 1);
                        if (Number.isFinite(year) && Number.isFinite(month)) {
                            const parsedObject = new Date(year, month - 1, day);
                            return Number.isNaN(parsedObject.getTime()) ? null : parsedObject;
                        }
                    }

                    const parsed = new Date(value);
                    return Number.isNaN(parsed.getTime()) ? null : parsed;
                }

                function formatDate(value, options = {}) {
                    const parsed = parseDate(value);
                    return parsed ? parsed.toLocaleDateString('id-ID', options) : '-';
                }

                function toIsoDateString(value) {
                    const parsed = parseDate(value);
                    if (!parsed) {
                        return '';
                    }
                    const year = parsed.getFullYear();
                    const month = String(parsed.getMonth() + 1).padStart(2, '0');
                    const day = String(parsed.getDate()).padStart(2, '0');
                    return `${year}-${month}-${day}`;
                }

                function formatRupiah(value) {
                    return `Rp. ${new Intl.NumberFormat('id-ID', {
                        minimumFractionDigits: 0,
                        maximumFractionDigits: 0
                    }).format(asNumber(value))}`;
                }

                function normalizeStatus(status) {
                    const normalized = String(status || '').toLowerCase();
                    if (normalized === 'no-payment' || normalized === 'no_payment' || normalized === 'tidak_bayar' || normalized === 'tidak-bayar') {
                        return 'no-payment';
                    }
                    if (normalized === 'paid' || normalized === 'lunas') {
                        return 'paid';
                    }
                    if (normalized === 'overdue' || normalized === 'jatuh-tempo' || normalized === 'jatuh_tempo') {
                        return 'overdue';
                    }
                    return 'unpaid';
                }

                function getStatusClass(status) {
                    const normalized = normalizeStatus(status);
                    if (normalized === 'no-payment') {
                        return 'overdue';
                    }
                    if (window.nmxBilling && typeof window.nmxBilling.getStatusClass === 'function') {
                        return window.nmxBilling.getStatusClass(normalized);
                    }
                    return normalized === 'paid' ? 'paid' : (normalized === 'overdue' ? 'overdue' : 'pending');
                }

                function getStatusLabel(status) {
                    const normalized = normalizeStatus(status);
                    if (normalized === 'no-payment') {
                        return 'Tidak Bayar';
                    }
                    if (window.nmxBilling && typeof window.nmxBilling.getStatusLabel === 'function') {
                        return window.nmxBilling.getStatusLabel(normalized);
                    }
                    return normalized === 'paid' ? 'Lunas' : (normalized === 'overdue' ? 'Jatuh Tempo' : 'Belum Lunas');
                }

                function canDeleteHistoryPayment() {
                    return Boolean(document.getElementById('historyCanDeleteCustomerFlag'));
                }

                function escapeHtml(value) {
                    return String(value ?? '')
                        .replace(/&/g, '&amp;')
                        .replace(/</g, '&lt;')
                        .replace(/>/g, '&gt;')
                        .replace(/"/g, '&quot;')
                        .replace(/'/g, '&#39;');
                }

                function openModalById(modalId) {
                    const modal = document.getElementById(modalId);
                    if (!modal) {
                        return;
                    }
                    modal.classList.add('open');
                    document.body.style.overflow = 'hidden';
                }

                function getHistoryReferenceDate(record) {
                    return parseDate(record && (record.billingMonth || record.dueDate || record.paymentDate));
                }

                function normalizeHistoryDescription(description) {
                    return String(description || '').trim().toUpperCase();
                }

                function isNoPaymentHistory(record) {
                    return normalizeHistoryDescription(record && record.description) === 'TIDAK_BAYAR';
                }

                function resolveHistoryInvoiceTypeLabel(invoice, paymentHistory) {
                    if (isNoPaymentHistory(paymentHistory)) {
                        return 'Catatan Sistem: Tidak Bayar';
                    }
                    return invoice && invoice.invoiceTypeLabel
                        ? invoice.invoiceTypeLabel
                        : 'Pembayaran Langganan Bulanan';
                }

                function resolveHistoryStatus(invoice, paymentHistory) {
                    if (isNoPaymentHistory(paymentHistory)) {
                        return 'no-payment';
                    }
                    return (invoice && invoice.status) || 'unpaid';
                }

                function resolveHistoryPaymentMethod(invoice, paymentHistory) {
                    if (paymentHistory && paymentHistory.method) {
                        return paymentHistory.method;
                    }
                    return invoice && invoice.paymentMethod ? invoice.paymentMethod : '-';
                }

                function resolveHistoryAmountPaid(invoice, paymentHistory) {
                    if (isNoPaymentHistory(paymentHistory)) {
                        return 0;
                    }
                    if (paymentHistory && paymentHistory.amount != null) {
                        return paymentHistory.amount;
                    }
                    return invoice && invoice.amountPaid != null ? invoice.amountPaid : 0;
                }

                function resolveHistoryNotes(invoice, paymentHistory) {
                    if (isNoPaymentHistory(paymentHistory)) {
                        return 'Pelanggan tidak melakukan pembayaran sampai akhir bulan jatuh tempo.';
                    }
                    return (invoice && (invoice.notes || invoice.paymentNotes)) || '';
                }

                function buildHistoryRows(invoices, paymentHistoryEntries) {
                    const invoiceRows = Array.isArray(invoices) ? invoices : [];
                    const paymentHistoryRows = Array.isArray(paymentHistoryEntries) ? paymentHistoryEntries : [];
                    const invoiceById = new Map();

                    invoiceRows.forEach(invoice => {
                        if (invoice && invoice.id != null) {
                            invoiceById.set(Number(invoice.id), invoice);
                        }
                    });

                    const mergedRows = paymentHistoryRows
                        .map(paymentHistory => {
                            const invoice = invoiceById.get(Number(paymentHistory && paymentHistory.invoiceId));
                            if (!invoice) {
                                return null;
                            }

                            return {
                                invoiceId: invoice.id,
                                invoiceNumber: paymentHistory.invoiceNumber || invoice.invoiceNumber || '-',
                                customerId: paymentHistory.customerId || invoice.customerId || null,
                                billingMonth: invoice.billingMonth,
                                dueDate: invoice.dueDate,
                                invoiceAmount: invoice.totalAmount || 0,
                                amountPaid: resolveHistoryAmountPaid(invoice, paymentHistory),
                                outstandingAmount: invoice.outstandingAmount || 0,
                                paymentDate: paymentHistory.paymentDate || invoice.paymentDate,
                                paymentDateKey: isNoPaymentHistory(paymentHistory) ? null : toIsoDateString(paymentHistory.paymentDate || invoice.paymentDate),
                                paymentMethod: resolveHistoryPaymentMethod(invoice, paymentHistory),
                                status: resolveHistoryStatus(invoice, paymentHistory),
                                invoiceTypeLabel: resolveHistoryInvoiceTypeLabel(invoice, paymentHistory),
                                notes: resolveHistoryNotes(invoice, paymentHistory),
                                description: paymentHistory.description || '',
                                isSystemEntry: isNoPaymentHistory(paymentHistory)
                            };
                        })
                        .filter(Boolean);

                    const invoiceIdsWithHistory = new Set(mergedRows.map(row => Number(row.invoiceId)));
                    const invoiceOnlyRows = invoiceRows
                        .filter(invoice => !invoiceIdsWithHistory.has(Number(invoice && invoice.id)))
                        .map(invoice => ({
                            invoiceId: invoice.id,
                            invoiceNumber: invoice.invoiceNumber || '-',
                            customerId: invoice.customerId || null,
                            billingMonth: invoice.billingMonth,
                            dueDate: invoice.dueDate,
                            invoiceAmount: invoice.totalAmount || 0,
                            amountPaid: invoice.amountPaid || 0,
                            outstandingAmount: invoice.outstandingAmount || 0,
                            paymentDate: invoice.paymentDate,
                            paymentDateKey: toIsoDateString(invoice.paymentDate),
                            paymentMethod: invoice.paymentMethod || '-',
                            status: invoice.status || 'unpaid',
                            invoiceTypeLabel: invoice.invoiceTypeLabel || 'Pembayaran Langganan Bulanan',
                            notes: invoice.notes || invoice.paymentNotes || '',
                            description: '',
                            isSystemEntry: false
                        }));

                    return [...mergedRows, ...invoiceOnlyRows]
                        .sort((left, right) => {
                            const leftRef = getHistoryReferenceDate(left);
                            const rightRef = getHistoryReferenceDate(right);
                            const leftTime = leftRef ? leftRef.getTime() : 0;
                            const rightTime = rightRef ? rightRef.getTime() : 0;
                            if (leftTime !== rightTime) {
                                return leftTime - rightTime;
                            }

                            const leftPay = parseDate(left.paymentDate);
                            const rightPay = parseDate(right.paymentDate);
                            const leftPayTime = leftPay ? leftPay.getTime() : 0;
                            const rightPayTime = rightPay ? rightPay.getTime() : 0;
                            if (leftPayTime !== rightPayTime) {
                                return leftPayTime - rightPayTime;
                            }

                            return asNumber(left.invoiceId) - asNumber(right.invoiceId);
                        });
                }

                async function deleteHistoryPayment(invoiceId, paymentDate) {
                    try {
                        const normalizedInvoiceId = Number(invoiceId);
                        if (!canDeleteHistoryPayment()) {
                            throw new Error('Akses hapus pembayaran hanya untuk superadmin.');
                        }
                        if (!historyState.customerId || !Number.isFinite(normalizedInvoiceId) || normalizedInvoiceId <= 0 || !paymentDate) {
                            throw new Error('Data pembayaran yang akan dihapus tidak valid.');
                        }

                        const confirmed = typeof window.nmxConfirm === 'function'
                            ? await window.nmxConfirm(
                                'Hapus pembayaran pada tanggal ini? Hanya catatan pembayaran dengan tanggal terpilih yang akan dihapus dan invoice akan dihitung ulang.',
                                {
                                    title: 'Konfirmasi Hapus Pembayaran',
                                    confirmText: 'Lanjut Hapus',
                                    confirmClass: 'btn btn-danger'
                                }
                            )
                            : window.confirm('Hapus pembayaran pada tanggal ini? Hanya catatan pembayaran dengan tanggal terpilih yang akan dihapus.');

                        if (!confirmed) {
                            return;
                        }

                        const selectedYear = document.getElementById('historyYearFilter')?.value || '';
                        const selectedMonth = document.getElementById('historyMonthFilter')?.value || '';
                        const response = await fetch(`/api/customers/${historyState.customerId}/payments/${normalizedInvoiceId}?paymentDate=${encodeURIComponent(paymentDate)}`, {
                            method: 'DELETE',
                            credentials: 'same-origin'
                        });
                        const result = await response.json();
                        if (!response.ok || !result.success) {
                            if (result && result.message === 'Penghapusan dibatalkan.') {
                                return;
                            }
                            throw new Error(result.message || 'Gagal menghapus pembayaran.');
                        }

                        await showPaymentHistory(historyState.customerId);

                        const yearSelect = document.getElementById('historyYearFilter');
                        const monthSelect = document.getElementById('historyMonthFilter');
                        if (yearSelect) {
                            yearSelect.value = selectedYear;
                        }
                        renderHistoryRows();
                        if (monthSelect && selectedYear) {
                            monthSelect.value = selectedMonth;
                            renderHistoryRows();
                        }

                        if (typeof window.showAlert === 'function') {
                            window.showAlert('success', result.message || 'Pembayaran berhasil dihapus.');
                        }
                    } catch (error) {
                        if (typeof window.showAlert === 'function') {
                            window.showAlert('error', error.message || 'Gagal menghapus pembayaran.');
                        } else {
                            console.error(error);
                        }
                    }
                }

                function populateHistoryFilters() {
                    const yearSelect = document.getElementById('historyYearFilter');
                    const monthSelect = document.getElementById('historyMonthFilter');
                    if (!yearSelect || !monthSelect) {
                        return;
                    }

                    const years = [...new Set(historyState.allRows
                        .map(row => getHistoryReferenceDate(row))
                        .filter(Boolean)
                        .map(date => String(date.getFullYear())))]
                        .sort((left, right) => Number(left) - Number(right));

                    yearSelect.innerHTML = '<option value="">Semua Tahun</option>';
                    years.forEach(year => {
                        const option = document.createElement('option');
                        option.value = year;
                        option.textContent = year;
                        yearSelect.appendChild(option);
                    });

                    monthSelect.innerHTML = '<option value="">Semua Bulan</option>';
                    monthSelect.disabled = true;
                    yearSelect.value = '';
                    monthSelect.value = '';
                }

                function refreshMonthOptions() {
                    const yearSelect = document.getElementById('historyYearFilter');
                    const monthSelect = document.getElementById('historyMonthFilter');
                    if (!yearSelect || !monthSelect) {
                        return;
                    }

                    const selectedYear = yearSelect.value;
                    monthSelect.innerHTML = '<option value="">Semua Bulan</option>';

                    if (!selectedYear) {
                        monthSelect.disabled = true;
                        monthSelect.value = '';
                        return;
                    }

                    const months = [...new Set(historyState.allRows
                        .map(row => getHistoryReferenceDate(row))
                        .filter(date => date && String(date.getFullYear()) === String(selectedYear))
                        .map(date => String(date.getMonth() + 1)))]
                        .sort((left, right) => Number(left) - Number(right));

                    months.forEach(month => {
                        const option = document.createElement('option');
                        option.value = month;
                        option.textContent = monthNames[Number(month) - 1] || month;
                        monthSelect.appendChild(option);
                    });

                    monthSelect.disabled = false;
                }

                function renderHistoryRows() {
                    const tbody = document.getElementById('historyTableBody');
                    const yearSelect = document.getElementById('historyYearFilter');
                    const monthSelect = document.getElementById('historyMonthFilter');
                    if (!tbody || !yearSelect || !monthSelect) {
                        return;
                    }

                    const selectedYear = yearSelect.value;
                    refreshMonthOptions();
                    const selectedMonth = monthSelect.value;

                    historyState.filteredRows = historyState.allRows.filter(row => {
                        const referenceDate = getHistoryReferenceDate(row);
                        if (!referenceDate) {
                            return !selectedYear && !selectedMonth;
                        }
                        if (selectedYear && String(referenceDate.getFullYear()) !== String(selectedYear)) {
                            return false;
                        }
                        return !selectedMonth || String(referenceDate.getMonth() + 1) === String(selectedMonth);
                    });

                    updateHistorySummary(historyState.filteredRows);

                    if (historyState.filteredRows.length === 0) {
                        tbody.innerHTML = '<tr><td colspan="9" class="table-empty"><p>Belum ada riwayat pembayaran sejak pemasangan atau aktivasi pelanggan.</p></td></tr>';
                        return;
                    }

                    tbody.innerHTML = historyState.filteredRows.map(row => {
                        const deleteAction = canDeleteHistoryPayment() && row.paymentDateKey && !row.isSystemEntry
                            ? `
                                <button class="btn btn-sm btn-danger" type="button" onclick='window.deleteHistoryPayment(${row.invoiceId}, ${JSON.stringify(row.paymentDateKey)})'>
                                    <i class="fas fa-trash"></i> Hapus
                                </button>
                            `
                            : '';
                        return `
                        <tr>
                            <td><span class="customer-code">${escapeHtml(row.invoiceNumber)}</span></td>
                            <td>${escapeHtml(row.invoiceTypeLabel)}</td>
                            <td>${formatDate(row.dueDate)}</td>
                            <td>${formatRupiah(row.invoiceAmount)}</td>
                            <td>${formatRupiah(row.amountPaid)}</td>
                            <td>${formatDate(row.paymentDate)}</td>
                            <td>${escapeHtml(row.paymentMethod)}</td>
                            <td>
                                <span class="payment-badge ${getStatusClass(row.status)}">
                                    ${escapeHtml(getStatusLabel(row.status))}
                                </span>
                            </td>
                            <td>
                                <button class="btn btn-sm btn-secondary" type="button" onclick="window.viewInvoiceDetail(${row.invoiceId})">
                                    <i class="fas fa-eye"></i> Lihat
                                </button>
                                ${deleteAction}
                            </td>
                        </tr>
                    `;
                    }).join('');
                }

                async function showInvoiceDetail(invoiceId) {
                    try {
                        const response = await fetch(`/api/invoices/${invoiceId}`);
                        const result = await response.json();
                        if (!result.success || !result.data) {
                            throw new Error(result.message || 'Detail invoice tidak ditemukan');
                        }

                        const invoice = result.data;
                        const assignText = (id, value) => {
                            const element = document.getElementById(id);
                            if (element) {
                                element.textContent = value;
                            }
                        };

                        assignText('detailInvoiceNumber', invoice.invoiceNumber || '-');
                        assignText('detailInvoiceStatus', getStatusLabel(invoice.status || 'unpaid'));
                        assignText('detailBillingMonth', formatDate(invoice.billingMonth, { month: 'long', year: 'numeric' }));
                        assignText('detailDueDate', formatDate(invoice.dueDate));
                        assignText('detailTotalAmount', formatRupiah(invoice.totalAmount || 0));
                        assignText('detailAmountPaid', formatRupiah(invoice.amountPaid || 0));
                        assignText('detailPaymentDate', formatDate(invoice.paymentDate));
                        assignText('detailPaymentMethod', invoice.paymentMethod || '-');
                        assignText('detailInvoiceNotes', invoice.notes || invoice.paymentNotes || '-');
                        openModalById('invoiceDetailModal');
                    } catch (error) {
                        if (typeof window.showAlert === 'function') {
                            window.showAlert('error', error.message || 'Gagal memuat detail invoice');
                        } else {
                            console.error(error);
                        }
                    }
                }

                function fallbackPrintWindow(title, customerLabel, rows) {
                    const printWindow = window.open('', '_blank', 'width=960,height=720');
                    if (!printWindow) {
                        return;
                    }

                    const tableRows = (Array.isArray(rows) ? rows : []).map(row => `
                        <tr>
                            <td>${escapeHtml(row.invoiceNumber)}</td>
                            <td>${escapeHtml(row.invoiceTypeLabel)}</td>
                            <td>${formatDate(row.dueDate)}</td>
                            <td>${formatRupiah(row.invoiceAmount)}</td>
                            <td>${formatRupiah(row.amountPaid)}</td>
                            <td>${formatDate(row.paymentDate)}</td>
                            <td>${escapeHtml(row.paymentMethod)}</td>
                            <td>${escapeHtml(getStatusLabel(row.status))}</td>
                        </tr>
                    `).join('') || '<tr><td colspan="8" style="text-align:center;padding:24px;">Belum ada riwayat pembayaran.</td></tr>';

                    printWindow.document.write(`
                        <!DOCTYPE html>
                        <html lang="id">
                        <head>
                            <meta charset="UTF-8">
                            <title>${escapeHtml(title)}</title>
                            <style>
                                body { font-family: Arial, sans-serif; margin: 24px; color: #111827; }
                                table { width: 100%; border-collapse: collapse; margin-top: 24px; }
                                th, td { border: 1px solid #d1d5db; padding: 10px; font-size: 12px; text-align: left; }
                                th { background: #f3f4f6; }
                            </style>
                        </head>
                        <body>
                            <h1>${escapeHtml(title)}</h1>
                            <p>${escapeHtml(customerLabel || '-')}</p>
                            <table>
                                <thead>
                                    <tr>
                                        <th>No Invoice</th>
                                        <th>Jenis Pembayaran</th>
                                        <th>Jatuh Tempo</th>
                                        <th>Total</th>
                                        <th>Terbayar</th>
                                        <th>Tanggal Bayar</th>
                                        <th>Metode</th>
                                        <th>Status</th>
                                    </tr>
                                </thead>
                                <tbody>${tableRows}</tbody>
                            </table>
                        </body>
                        </html>
                    `);
                    printWindow.document.close();
                    printWindow.focus();
                    setTimeout(() => printWindow.print(), 250);
                }

                async function showPaymentHistory(customerId) {
                    const normalizedCustomerId = Number(customerId);
                    if (!Number.isFinite(normalizedCustomerId) || normalizedCustomerId <= 0) {
                        return;
                    }

                    try {
                        const [customerResponse, invoicesResponse, quickPaymentsResponse] = await Promise.all([
                            fetch(`/pelanggan/api/customers/${normalizedCustomerId}`),
                            fetch(`/api/customers/${normalizedCustomerId}/invoices`),
                            fetch(`/api/customers/${normalizedCustomerId}/quick-payments`)
                        ]);

                        const customerResult = await customerResponse.json();
                        const invoicesResult = await invoicesResponse.json();
                        const quickPaymentsResult = await quickPaymentsResponse.json();

                        if (!customerResult.success) {
                            throw new Error(customerResult.message || 'Data pelanggan tidak ditemukan');
                        }
                        if (!invoicesResult.success) {
                            throw new Error(invoicesResult.message || 'Data invoice pelanggan tidak ditemukan');
                        }
                        if (!quickPaymentsResult.success) {
                            throw new Error(quickPaymentsResult.message || 'Data history pembayaran pelanggan tidak ditemukan');
                        }

                        historyState.customerId = normalizedCustomerId;
                        historyState.customerLabel = `${customerResult.data.fullName || '-'} (${customerResult.data.customerCode || '-'})`;
                        historyState.allRows = buildHistoryRows(invoicesResult.data, quickPaymentsResult.data);

                        const customerName = document.getElementById('historyCustomerName');
                        if (customerName) {
                            customerName.textContent = historyState.customerLabel;
                        }

                        populateHistoryFilters();
                        renderHistoryRows();
                        openModalById('historyModal');
                    } catch (error) {
                        if (typeof window.showAlert === 'function') {
                            window.showAlert('error', error.message || 'Gagal memuat riwayat pembayaran');
                        } else {
                            console.error(error);
                        }
                    }
                }

                window.viewPaymentHistory = showPaymentHistory;
                window.loadFilteredHistory = renderHistoryRows;
                window.deleteHistoryPayment = deleteHistoryPayment;
                window.viewInvoiceDetail = showInvoiceDetail;
                window.printCurrentHistory = function() {
                    const printFn = typeof window.openPaymentPrintWindow === 'function' ? window.openPaymentPrintWindow : fallbackPrintWindow;
                    printFn('History Pembayaran Pelanggan', historyState.customerLabel, historyState.filteredRows);
                };

                window.registerHistoryQuickAction(async function(action, customerId, serviceId) {
                    switch (action) {
                        case 'view-history':
                            return showPaymentHistory(customerId);
                        case 'view-invoice':
                            if (typeof window.viewLatestInvoice === 'function') {
                                return window.viewLatestInvoice(Number(customerId));
                            }
                            break;
                        case 'pay-invoice':
                            if (typeof window.openPayInvoice === 'function') {
                                return window.openPayInvoice(Number(customerId));
                            }
                            break;
                        case 'print-history':
                            if (typeof window.openPrintHistory === 'function') {
                                return window.openPrintHistory(Number(customerId));
                            }
                            break;
                        case 'suspend-service':
                            if (typeof window.confirmSuspend === 'function') {
                                return window.confirmSuspend(Number(customerId), Number(serviceId || 0));
                            }
                            break;
                        case 'activate-service':
                            if (typeof window.confirmActivate === 'function') {
                                return window.confirmActivate(Number(customerId), Number(serviceId || 0));
                            }
                            break;
                        case 'delete-customer':
                            if (typeof window.confirmDeleteCustomer === 'function') {
                                return window.confirmDeleteCustomer(Number(customerId));
                            }
                            break;
                        default:
                            break;
                    }
                });
            })();
        
})();
