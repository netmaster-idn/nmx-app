package com.netmaster.nmx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netmaster.nmx.dto.RegionOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndonesiaRegionService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final ObjectMapper objectMapper;

    @Value("${nmx.company.region-api-base-url:https://emsifa.github.io/api-wilayah-indonesia/api}")
    private String regionApiBaseUrl;

    @Value("${nmx.company.region-api-fallback-base-url:https://wilayah.id/api}")
    private String regionFallbackApiBaseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Map<String, List<RegionOption>> cache = new ConcurrentHashMap<>();

    public List<RegionOption> getProvinces() {
        return fetch("provinces", RegionResource.PROVINCES, null);
    }

    public List<RegionOption> getRegencies(String provinceCode) {
        return fetch("regencies:" + normalizeCode(provinceCode), RegionResource.REGENCIES, provinceCode);
    }

    public List<RegionOption> getDistricts(String regencyCode) {
        return fetch("districts:" + normalizeCode(regencyCode), RegionResource.DISTRICTS, regencyCode);
    }

    public List<RegionOption> getVillages(String districtCode) {
        return fetch("villages:" + normalizeCode(districtCode), RegionResource.VILLAGES, districtCode);
    }

    private List<RegionOption> fetch(String cacheKey, RegionResource resource, String parentCode) {
        return cache.computeIfAbsent(cacheKey, ignored -> doFetch(resource, parentCode));
    }

    private List<RegionOption> doFetch(RegionResource resource, String parentCode) {
        IllegalStateException lastError = null;

        for (RegionApiProvider provider : buildProviders()) {
            try {
                return fetchFromProvider(provider, resource, parentCode);
            } catch (IllegalStateException ex) {
                lastError = ex;
                log.warn("Gagal mengambil data {} Indonesia dari {}: {}",
                        resource.label(), provider.baseUrl(), ex.getMessage());
            }
        }

        throw new IllegalStateException("Gagal mengambil data " + resource.label() + " Indonesia", lastError);
    }

    private List<RegionOption> fetchFromProvider(RegionApiProvider provider, RegionResource resource, String parentCode) {
        List<String> requestCodes = buildRequestCodes(provider, resource, parentCode);
        IllegalStateException lastError = null;

        for (String requestCode : requestCodes) {
            String path = buildPath(resource, requestCode);
            try {
                return loadOptions(provider, path);
            } catch (IllegalStateException ex) {
                lastError = ex;
                log.debug("Request wilayah gagal untuk provider {} pada path {}: {}",
                        provider.baseUrl(), path, ex.getMessage());
            }
        }

        throw lastError != null
                ? lastError
                : new IllegalStateException("Konfigurasi request wilayah tidak valid");
    }

    private List<RegionOption> loadOptions(RegionApiProvider provider, String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(provider.baseUrl() + path))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }

            return parseOptions(response.body());
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Provider tidak dapat diakses", ex);
        }
    }

    private List<RegionOption> parseOptions(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode items = root.isArray() ? root : root.path("data");
        if (!items.isArray()) {
            throw new IllegalStateException("Format respons wilayah tidak dikenali");
        }

        List<RegionOption> options = new ArrayList<>();
        for (JsonNode node : items) {
            String id = normalizeCode(firstNonBlank(node, "id", "code", "kode", "kode_wilayah"));
            String name = firstNonBlank(node, "name", "nama", "nama_wilayah", "value");
            if (hasText(id) && hasText(name)) {
                options.add(new RegionOption(id, name.trim()));
            }
        }

        return List.copyOf(options);
    }

    private List<RegionApiProvider> buildProviders() {
        Map<String, RegionApiProvider> providers = new LinkedHashMap<>();
        addProvider(providers, regionApiBaseUrl);
        addProvider(providers, regionFallbackApiBaseUrl);
        return List.copyOf(providers.values());
    }

    private void addProvider(Map<String, RegionApiProvider> providers, String baseUrl) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        if (!hasText(normalizedBaseUrl)) {
            return;
        }
        providers.putIfAbsent(normalizedBaseUrl, new RegionApiProvider(normalizedBaseUrl, detectProviderStyle(normalizedBaseUrl)));
    }

    private ProviderStyle detectProviderStyle(String baseUrl) {
        String normalized = baseUrl.toLowerCase();
        if (normalized.contains("wilayah.id")) {
            return ProviderStyle.WILAYAH_ID;
        }
        return ProviderStyle.EMSIFA;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.trim().replaceAll("/+$", "");
    }

    private List<String> buildRequestCodes(RegionApiProvider provider, RegionResource resource, String parentCode) {
        if (resource == RegionResource.PROVINCES) {
            return List.of("");
        }

        String digits = normalizeCode(parentCode);
        if (!hasText(digits)) {
            throw new IllegalStateException("Kode wilayah parent kosong");
        }

        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (provider.style() == ProviderStyle.WILAYAH_ID) {
            addWilayahIdCodes(codes, resource, digits);
        }
        codes.add(digits);
        return List.copyOf(codes);
    }

    private void addWilayahIdCodes(Set<String> codes, RegionResource resource, String digits) {
        if (resource == RegionResource.REGENCIES && digits.length() >= 2) {
            codes.add(digits.substring(0, 2));
            return;
        }

        if (resource == RegionResource.DISTRICTS && digits.length() >= 4) {
            codes.add(joinSegments(digits.substring(0, 4), 2, 2));
            return;
        }

        if (resource == RegionResource.VILLAGES) {
            if (digits.length() >= 6) {
                codes.add(joinSegments(digits.substring(0, 6), 2, 2, 2));
            }
            if (digits.length() == 7) {
                codes.add(joinSegments(digits, 2, 2, 3));
            }
        }
    }

    private String buildPath(RegionResource resource, String requestCode) {
        if (resource == RegionResource.PROVINCES) {
            return "/provinces.json";
        }
        return "/" + resource.pathSegment() + "/" + requestCode + ".json";
    }

    private String firstNonBlank(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText();
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String normalizeCode(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String joinSegments(String digits, int... segmentLengths) {
        String normalizedDigits = normalizeCode(digits);
        int expectedLength = 0;
        for (int segmentLength : segmentLengths) {
            expectedLength += segmentLength;
        }
        if (normalizedDigits.length() != expectedLength) {
            return normalizedDigits;
        }

        StringBuilder builder = new StringBuilder();
        int offset = 0;
        for (int i = 0; i < segmentLengths.length; i++) {
            if (i > 0) {
                builder.append('.');
            }
            int length = segmentLengths[i];
            builder.append(normalizedDigits, offset, offset + length);
            offset += length;
        }
        return builder.toString();
    }

    private enum RegionResource {
        PROVINCES("provinces", "provinsi"),
        REGENCIES("regencies", "kabupaten/kota"),
        DISTRICTS("districts", "kecamatan"),
        VILLAGES("villages", "kelurahan/desa");

        private final String pathSegment;
        private final String label;

        RegionResource(String pathSegment, String label) {
            this.pathSegment = pathSegment;
            this.label = label;
        }

        public String pathSegment() {
            return pathSegment;
        }

        public String label() {
            return label;
        }
    }

    private enum ProviderStyle {
        EMSIFA,
        WILAYAH_ID
    }

    private record RegionApiProvider(String baseUrl, ProviderStyle style) {
    }
}
