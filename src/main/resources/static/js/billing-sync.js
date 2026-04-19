(function() {
    const REFRESH_KEY = 'nmx-billing-refresh';

    function emit(reason) {
        const detail = {
            reason: reason || 'update',
            at: Date.now()
        };

        try {
            window.localStorage.setItem(REFRESH_KEY, JSON.stringify(detail));
        } catch (error) {
            console.warn('Unable to persist billing refresh event:', error);
        }

        window.dispatchEvent(new CustomEvent('nmx:billing-refresh', { detail: detail }));
    }

    function onRefresh(handler) {
        if (typeof handler !== 'function') {
            return;
        }

        window.addEventListener('storage', function(event) {
            if (event.key !== REFRESH_KEY || !event.newValue) {
                return;
            }
            handler(safeParse(event.newValue));
        });

        window.addEventListener('nmx:billing-refresh', function(event) {
            handler(event.detail || null);
        });
    }

    function safeParse(rawValue) {
        try {
            return JSON.parse(rawValue);
        } catch (error) {
            return null;
        }
    }

    function getStatusLabel(status) {
        switch ((status || '').toLowerCase()) {
            case 'no_payment':
            case 'no-payment':
            case 'tidak_bayar':
            case 'tidak-bayar':
                return 'Tidak Bayar';
            case 'paid':
            case 'lunas':
            case 'sudah-bayar':
                return 'Lunas';
            case 'overdue':
            case 'jatuh-tempo':
                return 'Jatuh Tempo';
            case 'cancelled':
                return 'Dibatalkan';
            case 'partial':
            case 'pending':
            case 'unpaid':
            case 'belum-bayar':
            case 'belum-lunas':
            default:
                return 'Belum Lunas';
        }
    }

    function getStatusClass(status) {
        switch ((status || '').toLowerCase()) {
            case 'no_payment':
            case 'no-payment':
            case 'tidak_bayar':
            case 'tidak-bayar':
                return 'cancelled';
            case 'paid':
            case 'lunas':
            case 'sudah-bayar':
                return 'paid';
            case 'overdue':
            case 'jatuh-tempo':
                return 'overdue';
            case 'cancelled':
                return 'cancelled';
            case 'partial':
            case 'pending':
            case 'unpaid':
            case 'belum-bayar':
            case 'belum-lunas':
            default:
                return 'pending';
        }
    }

    window.nmxBilling = {
        emit: emit,
        onRefresh: onRefresh,
        getStatusLabel: getStatusLabel,
        getStatusClass: getStatusClass
    };
})();
