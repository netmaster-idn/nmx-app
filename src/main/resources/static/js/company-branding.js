(function() {
    if (window.__companyBrandingLoaded) {
        return;
    }
    window.__companyBrandingLoaded = true;

    var brandingBlocks = document.querySelectorAll('[data-company-branding]');
    if (!brandingBlocks.length) {
        return;
    }

    function hasText(value) {
        return value !== null && value !== undefined && String(value).trim() !== '';
    }

    function getInitials(name) {
        if (!hasText(name)) {
            return 'NM';
        }

        return String(name)
            .split(/\s+/)
            .filter(Boolean)
            .map(function(word) { return word.charAt(0); })
            .join('')
            .substring(0, 2)
            .toUpperCase();
    }

    function buildContactLine(data) {
        var parts = [];

        if (hasText(data.phone)) {
            parts.push('Tel ' + data.phone);
        }
        if (hasText(data.supportEmail)) {
            parts.push(data.supportEmail);
        } else if (hasText(data.email)) {
            parts.push(data.email);
        }
        if (hasText(data.website)) {
            parts.push(data.website);
        }

        return parts.length ? parts.join(' | ') : 'Data kontak company belum diatur';
    }

    function applyBranding(data) {
        var fallbackName = hasText(data && data.name) ? data.name : 'NetMaster';
        var fallbackTagline = hasText(data && data.tagline) ? data.tagline : 'Branding dokumen otomatis dari company setting';
        var fallbackAddress = hasText(data && data.address) ? data.address : 'Alamat company belum diatur';
        var fallbackContact = buildContactLine(data || {});
        var logoUrl = data && data.logoUrl;

        brandingBlocks.forEach(function(block) {
            var logo = block.querySelector('[data-company-logo]');
            var name = block.querySelector('[data-company-name]');
            var tagline = block.querySelector('[data-company-tagline]');
            var address = block.querySelector('[data-company-address]');
            var contact = block.querySelector('[data-company-contact]');

            if (logo) {
                if (hasText(logoUrl)) {
                    logo.innerHTML = '<img src="' + logoUrl + '" alt="Logo ' + fallbackName + '">';
                } else {
                    logo.textContent = getInitials(fallbackName);
                }
            }
            if (name) {
                name.textContent = fallbackName;
            }
            if (tagline) {
                tagline.textContent = fallbackTagline;
            }
            if (address) {
                address.textContent = fallbackAddress;
            }
            if (contact) {
                contact.textContent = fallbackContact;
            }
        });
    }

    fetch('/api/company')
        .then(function(response) { return response.json(); })
        .then(function(result) {
            if (result && result.success && result.data) {
                applyBranding(result.data);
                return;
            }
            applyBranding(null);
        })
        .catch(function() {
            applyBranding(null);
        });
})();
