(function () {
    if (window.__invoiceQuickActionsLoaded) {
        return;
    }
    window.__invoiceQuickActionsLoaded = true;
    const manualSendInFlight = new Set();

    function asNumber(value) {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : 0;
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
                const parsed = new Date(year, month - 1, day);
                return Number.isNaN(parsed.getTime()) ? null : parsed;
            }
        }
        const parsedDate = new Date(value);
        return Number.isNaN(parsedDate.getTime()) ? null : parsedDate;
    }

    function formatDateInput(date) {
        if (!(date instanceof Date) || Number.isNaN(date.getTime())) {
            return "";
        }
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");
        return year + "-" + month + "-" + day;
    }

    function addMonthsSameDay(baseDate, monthCount) {
        const safeBase = baseDate instanceof Date && !Number.isNaN(baseDate.getTime()) ? baseDate : new Date();
        const nextMonth = safeBase.getMonth() + (monthCount || 1);
        const year = safeBase.getFullYear();
        const day = safeBase.getDate();
        const maxDay = new Date(year, nextMonth + 1, 0).getDate();
        return new Date(year, nextMonth, Math.min(day, maxDay));
    }

    function formatRupiah(value) {
        return "Rp. " + new Intl.NumberFormat("id-ID", {
            minimumFractionDigits: 0,
            maximumFractionDigits: 0
        }).format(asNumber(value));
    }

    function formatDate(value, options) {
        const date = parseFlexibleDate(value);
        if (!date) {
            return "-";
        }
        return date.toLocaleDateString("id-ID", options || {});
    }

    function requestHeaders(includeJson) {
        const headers = {};
        if (includeJson) {
            headers["Content-Type"] = "application/json";
        }
        headers[window.csrfHeader || "X-CSRF-TOKEN"] = window.csrfToken || "";
        return headers;
    }

    function showAlert(type, message) {
        if (typeof window.showAlert === "function" && window.showAlert !== showAlert) {
            window.showAlert(type, message);
            return;
        }
        if (window.nmxNotify) {
            window.nmxNotify({ type: type, message: message });
            return;
        }
        window.alert(message || "Terjadi kesalahan.");
    }

    function openModal(modalId) {
        const modal = document.getElementById(modalId);
        if (!modal) {
            return;
        }
        modal.classList.add("open");
        document.body.style.overflow = "hidden";
    }

    function closeModal(modalId) {
        const modal = document.getElementById(modalId);
        if (!modal) {
            return;
        }
        modal.classList.remove("open");
        const anyModalOpen = document.querySelector(".modal.open");
        document.body.style.overflow = anyModalOpen ? "hidden" : "";
    }

    async function viewInvoiceDetail(invoiceId) {
        const normalizedId = Number(invoiceId);
        if (!Number.isFinite(normalizedId) || normalizedId <= 0) {
            showAlert("error", "Invoice tidak ditemukan");
            return;
        }

        try {
            const response = await fetch("/api/invoices/" + normalizedId);
            const result = await response.json();
            if (!result.success || !result.data) {
                throw new Error(result.message || "Detail invoice tidak ditemukan");
            }

            const invoice = result.data;
            const assignText = function (id, value) {
                const element = document.getElementById(id);
                if (element) {
                    element.textContent = value;
                }
            };

            const normalizedStatus = String(invoice.status || "").toLowerCase();
            let statusLabel = "Belum Lunas";
            if (normalizedStatus === "paid") {
                statusLabel = "Lunas";
            } else if (normalizedStatus === "overdue") {
                statusLabel = "Jatuh Tempo";
            } else if (normalizedStatus === "cancelled") {
                statusLabel = "Dibatalkan";
            }

            assignText("detailInvoiceNumber", invoice.invoiceNumber || "-");
            assignText("detailInvoiceStatus", statusLabel);
            assignText("detailBillingMonth", invoice.invoiceTypeLabel || "Pembayaran Langganan Bulanan");
            assignText("detailDueDate", formatDate(invoice.dueDate));
            assignText("detailTotalAmount", formatRupiah(invoice.totalAmount));
            assignText("detailAmountPaid", formatRupiah(invoice.amountPaid || 0));
            assignText("detailPaymentDate", formatDate(invoice.paymentDate));
            assignText("detailPaymentMethod", invoice.paymentMethod || "-");
            assignText("detailInvoiceNotes", invoice.notes || invoice.paymentNotes || "-");
            openModal("invoiceDetailModal");
        } catch (error) {
            console.error("Error loading invoice detail:", error);
            showAlert("error", error.message || "Gagal memuat detail invoice");
        }
    }

    function openInvoiceDocument(invoiceId) {
        const normalizedId = Number(invoiceId);
        if (!Number.isFinite(normalizedId) || normalizedId <= 0) {
            showAlert("error", "Invoice tidak ditemukan");
            return;
        }
        window.open("/finance/invoice/" + normalizedId + "/document", "_blank", "noopener");
    }

    async function openCreateInvoice(customerId) {
        const normalizedCustomerId = Number(customerId);
        if (!Number.isFinite(normalizedCustomerId) || normalizedCustomerId <= 0) {
            showAlert("error", "Pelanggan tidak ditemukan");
            return;
        }

        try {
            const [customerResponse, invoiceResponse] = await Promise.all([
                fetch("/pelanggan/api/customers/" + normalizedCustomerId),
                fetch("/api/customers/" + normalizedCustomerId + "/invoices")
            ]);
            const customerResult = await customerResponse.json();
            const invoiceResult = await invoiceResponse.json();

            if (!customerResult.success || !customerResult.data) {
                throw new Error(customerResult.message || "Data pelanggan tidak ditemukan");
            }

            const customer = customerResult.data;
            const customerIdInput = document.getElementById("invoiceCustomerId");
            const customerNameEl = document.getElementById("invoiceCustomerName");
            const billingMonthInput = document.getElementById("invoiceBillingMonth");
            const dueDateInput = document.getElementById("invoiceDueDate");
            const monthlyFeeInput = document.getElementById("invoiceMonthlyFee");
            const installationFeeInput = document.getElementById("invoiceInstallationFee");
            const notesInput = document.getElementById("invoiceNotes");

            if (!customerIdInput || !customerNameEl || !billingMonthInput || !dueDateInput) {
                throw new Error("Modal invoice tidak tersedia");
            }

            customerIdInput.value = String(normalizedCustomerId);
            customerNameEl.textContent = customer.fullName || "-";

            let nextDate = addMonthsSameDay(new Date(), 1);
            let monthlyFee = asNumber(customer.monthlyFee);
            if (invoiceResult.success && Array.isArray(invoiceResult.data) && invoiceResult.data.length > 0) {
                const sorted = invoiceResult.data.slice().sort(function (left, right) {
                    const leftDate = parseFlexibleDate(left.dueDate || left.billingMonth);
                    const rightDate = parseFlexibleDate(right.dueDate || right.billingMonth);
                    return (rightDate ? rightDate.getTime() : 0) - (leftDate ? leftDate.getTime() : 0);
                });
                const latestInvoice = sorted[0];
                monthlyFee = asNumber(latestInvoice.monthlyFee);
                const anchorDate = parseFlexibleDate(latestInvoice.dueDate || latestInvoice.billingMonth);
                nextDate = addMonthsSameDay(anchorDate || new Date(), 1);
            }

            const nextDateValue = formatDateInput(nextDate);
            billingMonthInput.value = nextDateValue.slice(0, 7);
            dueDateInput.value = nextDateValue;
            if (monthlyFeeInput) {
                monthlyFeeInput.value = String(monthlyFee);
            }
            if (installationFeeInput) {
                installationFeeInput.value = "0";
            }
            if (notesInput) {
                notesInput.value = "";
            }
            openModal("invoiceModal");
        } catch (error) {
            console.error("Error opening create invoice:", error);
            showAlert("error", error.message || "Gagal memuat data pelanggan");
        }
    }

    async function openPayInvoiceFromHistory(invoiceId) {
        const normalizedId = Number(invoiceId);
        if (!Number.isFinite(normalizedId) || normalizedId <= 0) {
            showAlert("error", "Invoice tidak ditemukan");
            return;
        }

        try {
            const response = await fetch("/api/invoices/" + normalizedId);
            const result = await response.json();
            if (!result.success || !result.data) {
                throw new Error(result.message || "Invoice tidak ditemukan");
            }

            const invoice = result.data;
            const outstandingAmount = Math.max(0, asNumber(invoice.outstandingAmount));
            const amountToPay = outstandingAmount > 0 ? outstandingAmount : asNumber(invoice.totalAmount);

            const invoiceIdInput = document.getElementById("payInvoiceId");
            const invoiceNumberEl = document.getElementById("payInvoiceNumber");
            const totalAmountEl = document.getElementById("payTotalAmount");
            const amountInput = document.getElementById("payAmount");
            const methodSelect = document.getElementById("payMethod");
            const notesInput = document.getElementById("payNotes");

            if (!invoiceIdInput || !invoiceNumberEl || !totalAmountEl || !amountInput) {
                throw new Error("Modal pembayaran tidak tersedia");
            }

            invoiceIdInput.value = String(normalizedId);
            invoiceNumberEl.textContent = invoice.invoiceNumber || "-";
            totalAmountEl.textContent = formatRupiah(amountToPay);
            amountInput.value = String(amountToPay);
            if (methodSelect) {
                methodSelect.value = "cash";
            }
            if (notesInput) {
                notesInput.value = "";
            }

            closeModal("historyModal");
            openModal("payModal");
        } catch (error) {
            console.error("Error opening pay invoice from history:", error);
            showAlert("error", error.message || "Gagal memuat detail invoice");
        }
    }

    async function saveInvoice() {
        const customerId = Number((document.getElementById("invoiceCustomerId") || {}).value);
        if (!Number.isFinite(customerId) || customerId <= 0) {
            showAlert("error", "Pelanggan belum dipilih");
            return;
        }

        const payload = {
            customerId: customerId,
            billingMonth: (document.getElementById("invoiceBillingMonth") || {}).value || null,
            dueDate: (document.getElementById("invoiceDueDate") || {}).value || null,
            monthlyFee: asNumber((document.getElementById("invoiceMonthlyFee") || {}).value),
            installationFee: asNumber((document.getElementById("invoiceInstallationFee") || {}).value),
            notes: (document.getElementById("invoiceNotes") || {}).value || ""
        };

        try {
            const response = await fetch("/api/invoices", {
                method: "POST",
                headers: requestHeaders(true),
                credentials: "same-origin",
                body: JSON.stringify(payload)
            });
            const result = await response.json();
            if (!result.success) {
                throw new Error(result.message || "Gagal membuat invoice");
            }
            showAlert("success", "Invoice berhasil dibuat");
            closeModal("invoiceModal");
            window.location.reload();
        } catch (error) {
            console.error("Error saving invoice:", error);
            showAlert("error", error.message || "Gagal membuat invoice");
        }
    }

    async function processPayment() {
        const invoiceId = Number((document.getElementById("payInvoiceId") || {}).value);
        if (!Number.isFinite(invoiceId) || invoiceId <= 0) {
            showAlert("error", "Invoice tidak valid");
            return;
        }

        const payload = {
            amount: asNumber((document.getElementById("payAmount") || {}).value),
            paymentDate: ((document.getElementById("payDate") || {}).value) || new Date().toISOString().slice(0, 10),
            paymentMethod: (document.getElementById("payMethod") || {}).value || "cash",
            notes: (document.getElementById("payNotes") || {}).value || ""
        };

        try {
            const response = await fetch("/api/invoices/" + invoiceId + "/pay", {
                method: "POST",
                headers: requestHeaders(true),
                credentials: "same-origin",
                body: JSON.stringify(payload)
            });
            const result = await response.json();
            if (!result.success) {
                throw new Error(result.message || "Gagal memproses pembayaran");
            }
            showAlert("success", "Pembayaran berhasil");
            closeModal("payModal");
            window.location.reload();
        } catch (error) {
            console.error("Error processing payment:", error);
            showAlert("error", error.message || "Gagal memproses pembayaran");
        }
    }

    async function deleteInvoicePayment(invoiceId) {
        const normalizedId = Number(invoiceId);
        if (!Number.isFinite(normalizedId) || normalizedId <= 0) {
            showAlert("error", "Invoice tidak valid");
            return false;
        }

        const confirmed = typeof window.nmxConfirm === "function"
            ? await window.nmxConfirm("Hapus data pembayaran invoice ini?", {
                title: "Konfirmasi Hapus Pembayaran",
                confirmText: "Ya, Hapus",
                confirmClass: "btn btn-danger"
            })
            : window.confirm("Hapus data pembayaran invoice ini?");

        if (!confirmed) {
            return false;
        }

        try {
            const response = await fetch("/api/invoices/" + normalizedId + "/payments/delete", {
                method: "POST",
                headers: requestHeaders(),
                credentials: "same-origin"
            });
            const result = await response.json();
            if (!response.ok || !result.success) {
                throw new Error((result && result.message) || "Gagal menghapus data pembayaran");
            }

            if (window.nmxBilling && typeof window.nmxBilling.emit === "function") {
                window.nmxBilling.emit("payment");
            }
            showAlert("success", (result && result.message) || "Data pembayaran berhasil dihapus");
            window.location.reload();
        } catch (error) {
            showAlert("error", error.message || "Gagal menghapus data pembayaran");
        }
        return false;
    }

    async function parseJsonResponse(response) {
        let result = null;
        try {
            result = await response.json();
        } catch (error) {
            result = null;
        }
        return result;
    }

    async function sendManualInvoiceOrReceipt(invoiceId, preferredDocumentType) {
        const normalizedId = Number(invoiceId);
        if (!Number.isFinite(normalizedId) || normalizedId <= 0) {
            throw new Error("Invoice tidak ditemukan");
        }
        if (manualSendInFlight.has(normalizedId)) {
            return null;
        }
        manualSendInFlight.add(normalizedId);
        const response = await fetch("/api/whatsapp/invoices/" + normalizedId + "/manual-send", {
            method: "POST",
            headers: requestHeaders(),
            credentials: "same-origin"
        });
        try {
            const result = await parseJsonResponse(response);
            if (!response.ok || !result || !result.success) {
                throw new Error((result && result.message) || "Gagal mengirim dokumen ke WhatsApp");
            }

            const payload = result.data || {};
            const expectedType = String(preferredDocumentType || "").toLowerCase();
            if (expectedType === "receipt" && String(payload.documentType || "").toLowerCase() !== "receipt") {
                throw new Error("Dokumen struk pembayaran tidak tersedia untuk invoice ini");
            }
            const documentLabel = payload.documentLabel || "dokumen";
            const phone = payload.phone ? " ke " + payload.phone : "";
            showAlert("success", documentLabel + " berhasil dikirim" + phone);
            return payload;
        } finally {
            manualSendInFlight.delete(normalizedId);
        }
    }

    async function handleManualInvoiceOrReceipt(invoiceId, preferredDocumentType) {
        const normalizedId = Number(invoiceId);
        if (!Number.isFinite(normalizedId) || normalizedId <= 0) {
            showAlert("error", "Invoice tidak ditemukan");
            return;
        }

        try {
            await sendManualInvoiceOrReceipt(normalizedId, preferredDocumentType);
        } catch (error) {
            console.error("Error sending manual invoice or receipt:", error);
            showAlert("error", error.message || "Gagal mengirim invoice/tagihan WhatsApp");
        }
    }

    function formatDateTimeDisplay(value) {
        if (!value) {
            return "-";
        }
        const parsed = new Date(value);
        if (Number.isNaN(parsed.getTime())) {
            return "-";
        }
        return parsed.toLocaleString("id-ID", {
            dateStyle: "medium",
            timeStyle: "short"
        });
    }

    function renderWaHistoryBadge(label, tone) {
        return '<span class="payment-badge ' + (tone || 'unpaid') + '">' + String(label || "-") + '</span>';
    }

    function setWaHistoryCount(id, value) {
        const element = document.getElementById(id);
        if (element) {
            element.textContent = String(value || 0);
        }
    }

    function updateWaHistoryStats(items) {
        const safeItems = Array.isArray(items) ? items : [];
        setWaHistoryCount("waHistoryTotalCount", safeItems.length);
        setWaHistoryCount("waHistorySentCount", safeItems.filter(function (item) { return item && item.sent; }).length);
        setWaHistoryCount("waHistoryDeliveredCount", safeItems.filter(function (item) { return item && item.delivered; }).length);
        setWaHistoryCount("waHistoryReadCount", safeItems.filter(function (item) { return item && item.read; }).length);
    }

    function renderWaHistory(items) {
        const tbody = document.getElementById("waBotHistoryTableBody");
        if (!tbody) {
            return;
        }

        const safeItems = Array.isArray(items) ? items : [];
        updateWaHistoryStats(safeItems);
        if (!safeItems.length) {
            tbody.innerHTML = '<tr><td colspan="9" class="table-empty">Belum ada history pengiriman BOT WhatsApp.</td></tr>';
            return;
        }

        tbody.innerHTML = safeItems.map(function (item) {
            const customerName = item && item.customerName ? item.customerName : "-";
            const customerCode = item && item.customerCode ? item.customerCode : "-";
            const customerLabel = customerCode !== "-"
                ? escapeHtml(customerName) + '<br><span class="customer-code">' + escapeHtml(customerCode) + '</span>'
                : escapeHtml(customerName);
            const dispatchTone = item && item.dispatchStatus === "error"
                ? "overdue"
                : (item && item.dispatchStatus === "skipped" ? "cancelled" : "paid");
            const deliveryTone = item && item.read
                ? "paid"
                : (item && item.delivered ? "overdue" : (item && item.sent ? "unpaid" : "cancelled"));
            return '' +
                '<tr>' +
                '<td>' + customerLabel + '</td>' +
                '<td><span class="invoice-number">' + escapeHtml(item && item.invoiceNumber ? item.invoiceNumber : "-") + '</span></td>' +
                '<td>' + escapeHtml(item && (item.documentLabel || item.documentType) ? (item.documentLabel || item.documentType) : "-") + '</td>' +
                '<td>' + escapeHtml(item && item.phoneNumber ? item.phoneNumber : "-") + '</td>' +
                '<td>' + renderWaHistoryBadge(escapeHtml(item && item.dispatchStatusLabel ? item.dispatchStatusLabel : "-"), dispatchTone) + '</td>' +
                '<td>' + renderWaHistoryBadge(escapeHtml(item && item.deliveryStatusLabel ? item.deliveryStatusLabel : "-"), deliveryTone) + '</td>' +
                '<td>' + escapeHtml(formatDateTimeDisplay(item && item.sentAt)) + '</td>' +
                '<td>' + escapeHtml(formatDateTimeDisplay(item && item.readAt)) + '</td>' +
                '<td>' + escapeHtml(item && item.gatewayMessage ? item.gatewayMessage : "-") + '</td>' +
                '</tr>';
        }).join("");
    }

    async function viewWaBotHistory() {
        const tbody = document.getElementById("waBotHistoryTableBody");
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="9" class="table-empty">Memuat history BOT WhatsApp...</td></tr>';
        }
        updateWaHistoryStats([]);
        openModal("waBotHistoryModal");
        try {
            const response = await fetch("/api/whatsapp/history", {
                credentials: "same-origin"
            });
            const result = await response.json();
            if (!response.ok || !result.success) {
                throw new Error((result && result.message) || "Gagal memuat history BOT WhatsApp");
            }
            renderWaHistory(result.data || []);
        } catch (error) {
            if (tbody) {
                tbody.innerHTML = '<tr><td colspan="9" class="table-empty">' + escapeHtml(error.message || "Gagal memuat history BOT WhatsApp") + '</td></tr>';
            }
            showAlert("error", error.message || "Gagal memuat history BOT WhatsApp");
        }
    }
    if (typeof window.openModal !== "function") {
        window.openModal = openModal;
    }
    if (typeof window.closeModal !== "function") {
        window.closeModal = closeModal;
    }
    if (typeof window.showAlert !== "function") {
        window.showAlert = showAlert;
    }
    if (typeof window.viewInvoiceDetail !== "function") {
        window.viewInvoiceDetail = viewInvoiceDetail;
    }
    if (typeof window.openInvoiceDocument !== "function") {
        window.openInvoiceDocument = openInvoiceDocument;
    }
    if (typeof window.openCreateInvoice !== "function") {
        window.openCreateInvoice = openCreateInvoice;
    }
    if (typeof window.openPayInvoiceFromHistory !== "function") {
        window.openPayInvoiceFromHistory = openPayInvoiceFromHistory;
    }
    if (typeof window.saveInvoice !== "function") {
        window.saveInvoice = saveInvoice;
    }
    if (typeof window.processPayment !== "function") {
        window.processPayment = processPayment;
    }
    if (typeof window.deleteInvoicePayment !== "function") {
        window.deleteInvoicePayment = deleteInvoicePayment;
    }
    window.handleManualInvoiceOrReceipt = handleManualInvoiceOrReceipt;
    window.viewWaBotHistory = viewWaBotHistory;
    window.sendManualInvoiceOrReceipt = handleManualInvoiceOrReceipt;
})();


