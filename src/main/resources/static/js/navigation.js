// Make functions globally available
window.toggleMenu = function(menuId, event) {
    if (event) {
        event.stopPropagation();
        event.preventDefault();
    }

    // Close all other menus first
    const allMenus = document.querySelectorAll('.nav-dropdown');
    allMenus.forEach(function(dropdown) {
        const submenu = dropdown.querySelector('.submenu');
        if (submenu && submenu.id !== menuId) {
            dropdown.classList.remove('open');
        }
    });

    // Toggle the clicked menu
    const menu = document.getElementById(menuId);
    if (menu && menu.parentElement) {
        menu.parentElement.classList.toggle('open');
    }
};

window.handleSubmenuClick = function(event) {
    if (event) {
        event.stopPropagation();
    }
};

function showPage(page) {
    document.querySelectorAll(".page-content")
    .forEach(p=>p.classList.remove("active"))
    document
    .getElementById("page-"+page)
    .classList.add("active")
}

function updateClock() {
    let now = new Date()
    let h = String(now.getHours()).padStart(2,"0")
    let m = String(now.getMinutes()).padStart(2,"0")
    let s = String(now.getSeconds()).padStart(2,"0")
    document.getElementById("clock")
    .innerText = h+":"+m+":"+s
}

setInterval(updateClock, 1000)
updateClock()

// Close all dropdowns when clicking outside
document.addEventListener('click', function(e) {
    if (!e.target.closest('.nav-dropdown')) {
        const allDropdowns = document.querySelectorAll('.nav-dropdown');
        allDropdowns.forEach(function(dropdown) {
            dropdown.classList.remove('open');
        });
    }
});

// Initialize submenu click handlers on DOM ready
document.addEventListener('DOMContentLoaded', function() {
    const submenuItems = document.querySelectorAll('.submenu-item');
    submenuItems.forEach(function(item) {
        item.addEventListener('click', function(e) {
            handleSubmenuClick(e);
        });
    });
});

const NMX_PREVIOUS_ROUTE_KEY = 'nmx.previousRoute';
const NMX_CURRENT_ROUTE_KEY = 'nmx.currentRoute';
const NMX_MODAL_STACK = [];
let nmxModalObserverInitialized = false;

function getModalSelector() {
    return [
        '.modal',
        '.modal-overlay',
        '.mk-monitor-modal',
        '.acs-modal-overlay',
        '.superadmin-modal-overlay',
        '.customer-modal',
        '.user-modal',
        '.network-modal',
        '.hybrid-modal',
        '.device-modal',
        '.superadmin-notify-overlay'
    ].join(',');
}

function getModalKey(element) {
    if (!element || !(element instanceof Element)) {
        return '';
    }
    return element.id || element.getAttribute('data-modal') || element.getAttribute('aria-label') || element.className || '';
}

function syncVisibleModalStack() {
    const visibleModals = Array.from(document.querySelectorAll(getModalSelector()))
        .filter(isElementVisible);
    const visibleKeys = visibleModals.map(getModalKey).filter(Boolean);

    for (let index = NMX_MODAL_STACK.length - 1; index >= 0; index -= 1) {
        if (!visibleKeys.includes(NMX_MODAL_STACK[index])) {
            NMX_MODAL_STACK.splice(index, 1);
        }
    }

    visibleModals.forEach(function(modal) {
        const key = getModalKey(modal);
        if (!key) {
            return;
        }
        const existingIndex = NMX_MODAL_STACK.indexOf(key);
        if (existingIndex >= 0) {
            NMX_MODAL_STACK.splice(existingIndex, 1);
        }
        NMX_MODAL_STACK.push(key);
    });
}

function getTopModalFromStack() {
    syncVisibleModalStack();
    for (let index = NMX_MODAL_STACK.length - 1; index >= 0; index -= 1) {
        const key = NMX_MODAL_STACK[index];
        const modal = document.querySelector('#' + CSS.escape(key)) || Array.from(document.querySelectorAll(getModalSelector()))
            .find(function(candidate) { return getModalKey(candidate) === key; });
        if (modal && isElementVisible(modal)) {
            return modal;
        }
    }
    return null;
}

function initializeModalStackObserver() {
    if (nmxModalObserverInitialized || !document.body) {
        return;
    }

    const observer = new MutationObserver(function() {
        syncVisibleModalStack();
    });

    observer.observe(document.body, {
        attributes: true,
        childList: true,
        subtree: true,
        attributeFilter: ['class', 'style', 'hidden', 'aria-hidden']
    });

    document.addEventListener('click', function() {
        syncVisibleModalStack();
    }, true);

    syncVisibleModalStack();
    nmxModalObserverInitialized = true;
}

function getCurrentRoute() {
    return window.location.pathname + window.location.search + window.location.hash;
}

function rememberRouteHistory() {
    const currentRoute = getCurrentRoute();
    const storedCurrentRoute = sessionStorage.getItem(NMX_CURRENT_ROUTE_KEY);

    if (storedCurrentRoute && storedCurrentRoute !== currentRoute) {
        sessionStorage.setItem(NMX_PREVIOUS_ROUTE_KEY, storedCurrentRoute);
    }

    sessionStorage.setItem(NMX_CURRENT_ROUTE_KEY, currentRoute);
}

rememberRouteHistory();
initializeModalStackObserver();

function isEditableTarget(target) {
    if (!target || !(target instanceof Element)) {
        return false;
    }
    if (target.isContentEditable) {
        return true;
    }
    const editableSelector = 'input, textarea, select, [contenteditable="true"], [contenteditable=""]';
    return Boolean(target.closest(editableSelector));
}

function isElementVisible(element) {
    if (!element || !(element instanceof Element)) {
        return false;
    }
    const style = window.getComputedStyle(element);
    if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
        return false;
    }
    const rect = element.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
}

function getVisibleOverlayContainers() {
    const selectors = [
        '.modal.open',
        '.modal-overlay.open',
        '.modal-overlay[style*="display: block"]',
        '.modal[aria-hidden="false"]',
        '.mk-monitor-modal.open',
        '.superadmin-modal-overlay',
        '.superadmin-notify-overlay',
        '.acs-modal-overlay.open',
        '.customer-modal.open',
        '.user-modal.open',
        '.network-modal.open',
        '.hybrid-modal.open',
        '.hybrid-modal.is-open',
        '.device-modal.open',
        '.open[data-modal]',
        '.show[data-modal]'
    ];

    return Array.from(document.querySelectorAll(selectors.join(',')))
        .filter(isElementVisible);
}

function closeVisibleOverlayOrPanel() {
    const closeSelectors = [
        '[data-close-modal]',
        '[data-wa-compose-close]',
        '[data-wa-close]',
        '.modal-close',
        '.modal-close-btn',
        '.modal-close-modern',
        '.hybrid-modal-close',
        '#superadminDeleteCancel',
        '#confirmModalCancel',
        '#closeErrorDetailModal',
        '[data-close-device-modal]',
        '[aria-label="Tutup"]'
    ];

    const stackedOverlay = getTopModalFromStack();
    if (stackedOverlay) {
        const stackedCloseTarget = stackedOverlay.querySelector(closeSelectors.join(','));
        if (stackedCloseTarget instanceof HTMLElement) {
            stackedCloseTarget.click();
            setTimeout(syncVisibleModalStack, 0);
            return true;
        }
        if (closeOverlayByKnownHandler(stackedOverlay)) {
            setTimeout(syncVisibleModalStack, 0);
            return true;
        }
        if (forceCloseOverlayState(stackedOverlay)) {
            setTimeout(syncVisibleModalStack, 0);
            return true;
        }
    }

    const overlays = getVisibleOverlayContainers();
    for (let index = overlays.length - 1; index >= 0; index -= 1) {
        const overlay = overlays[index];
        const closeTarget = overlay.querySelector(closeSelectors.join(','));
        if (closeTarget instanceof HTMLElement) {
            closeTarget.click();
            setTimeout(syncVisibleModalStack, 0);
            return true;
        }
        if (closeOverlayByKnownHandler(overlay)) {
            setTimeout(syncVisibleModalStack, 0);
            return true;
        }
        if (forceCloseOverlayState(overlay)) {
            setTimeout(syncVisibleModalStack, 0);
            return true;
        }
    }

    return false;
}

function closeOverlayByKnownHandler(overlay) {
    if (!overlay || !(overlay instanceof HTMLElement)) {
        return false;
    }

    const id = overlay.id || '';
    const knownHandlers = {
        userFormModal: 'closeUserModal',
        editCustomerModal: function() { return window.closeModal && window.closeModal('editCustomerModal'); },
        paymentHistoryModal: function() { return window.closeModal && window.closeModal('paymentHistoryModal'); },
        activationCustomerModal: function() { return window.closeModal && window.closeModal('activationCustomerModal'); },
        deviceFormModal: 'closeForm',
        serverModal: 'closeServerModal',
        modalOverlay: 'closeCompanyModal',
        deleteModalOverlay: 'closeDeleteConfirmModal',
        bankAccountModalOverlay: 'closeBankAccountModal',
        technicianModal: 'closeModal',
        historyModal: function() { return window.closeModal && window.closeModal('historyModal'); },
        invoiceModal: function() { return window.closeModal && window.closeModal('invoiceModal'); },
        payModal: function() { return window.closeModal && window.closeModal('payModal'); },
        invoiceDetailModal: function() { return window.closeModal && window.closeModal('invoiceDetailModal'); },
        printModal: function() { return window.closeModal && window.closeModal('printModal'); },
        confirmModal: function() { return window.closeModal && window.closeModal('confirmModal'); },
        packageModal: 'closePackageModal',
        serviceModal: 'closeServiceModal',
        packagePanelOverlay: 'closePackagePanel',
        servicePanelOverlay: 'closeServicePanel'
    };

    const handler = knownHandlers[id];
    if (!handler) {
        return false;
    }

    if (typeof handler === 'string' && typeof window[handler] === 'function') {
        window[handler]();
        return true;
    }

    if (typeof handler === 'function') {
        handler();
        return true;
    }

    return false;
}

function forceCloseOverlayState(overlay) {
    if (!overlay || !(overlay instanceof HTMLElement)) {
        return false;
    }

    overlay.classList.remove('open', 'show', 'is-open', 'active');
    overlay.setAttribute('aria-hidden', 'true');
    overlay.hidden = true;

    if (overlay.style.display && overlay.style.display !== 'none') {
        overlay.style.display = 'none';
    }

    const siblingPanel = overlay.querySelector('.modal-content, .user-modal-panel, .network-modal-panel, .hybrid-modal-panel, .acs-modal');
    if (siblingPanel instanceof HTMLElement) {
        siblingPanel.classList.remove('open', 'show', 'is-open', 'active');
    }

    if (typeof overlay.close === 'function' && (overlay.tagName === 'DIALOG' || overlay.hasAttribute('open'))) {
        try {
            overlay.close();
        } catch (error) {
            // Ignore unsupported close attempts and rely on class/style fallback.
        }
    }

    return !isElementVisible(overlay);
}

function navigateBackFromEscape() {
    const previousRoute = sessionStorage.getItem(NMX_PREVIOUS_ROUTE_KEY);
    const currentRoute = getCurrentRoute();

    if (previousRoute && previousRoute !== currentRoute) {
        sessionStorage.setItem(NMX_CURRENT_ROUTE_KEY, previousRoute);
        window.location.href = previousRoute;
        return true;
    }

    if (window.history.length > 1) {
        window.history.back();
        return true;
    }

    if (document.referrer) {
        try {
            const referrerUrl = new URL(document.referrer, window.location.origin);
            if (referrerUrl.origin === window.location.origin) {
                window.location.href = referrerUrl.href;
                return true;
            }
        } catch (error) {
            // Ignore invalid referrer and fall through.
        }
    }

    return false;
}

let lastEscapeHandledAt = 0;

function handleGlobalEscape(event) {
    if (event.key !== 'Escape' || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) {
        return;
    }

    const now = Date.now();
    if (now - lastEscapeHandledAt < 250) {
        return;
    }

    const hadVisibleOverlay = getVisibleOverlayContainers().length > 0;
    if (hadVisibleOverlay) {
        if (closeVisibleOverlayOrPanel()) {
            event.preventDefault();
            event.stopPropagation();
            lastEscapeHandledAt = now;
        }
        return;
    }

    if (navigateBackFromEscape()) {
        event.preventDefault();
        event.stopPropagation();
        lastEscapeHandledAt = now;
    }
}

window.addEventListener('keydown', handleGlobalEscape, true);
document.addEventListener('keydown', handleGlobalEscape, true);
