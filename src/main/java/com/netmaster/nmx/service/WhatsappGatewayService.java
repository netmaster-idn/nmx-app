package com.netmaster.nmx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netmaster.nmx.config.WhatsappGatewayProperties;
import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.WhatsappSendDocumentRequest;
import com.netmaster.nmx.dto.WhatsappSendMessageRequest;
import com.netmaster.nmx.dto.WhatsappStatusData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappGatewayService {

    private final WhatsappGatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final WhatsappTenantSessionResolver whatsappTenantSessionResolver;

    public ApiResponse<WhatsappStatusData> getStatus() {
        try {
            return exchange("/api/whatsapp/status", HttpMethod.GET, "Status WhatsApp berhasil diambil");
        } catch (RuntimeException ex) {
            log.warn("WhatsApp gateway status fallback activated: {}", ex.getMessage());
            WhatsappStatusData data = new WhatsappStatusData(
                    "error",
                    "Error",
                    false,
                    false,
                    null,
                    resolveSessionId(),
                    null,
                    null,
                    "WhatsApp gateway tidak dapat dijangkau: " + ex.getMessage(),
                    null
            );
            return ApiResponse.success("Status WhatsApp fallback", data);
        }
    }

    public ApiResponse<WhatsappStatusData> initClient() {
        return exchange("/api/whatsapp/init", HttpMethod.POST, "Inisialisasi WhatsApp berhasil dijalankan");
    }

    public ApiResponse<WhatsappStatusData> getQrCode() {
        return exchange("/api/whatsapp/qr", HttpMethod.GET, "QR WhatsApp berhasil diambil");
    }

    public ApiResponse<WhatsappStatusData> regenerateQrCode() {
        return exchange("/api/whatsapp/qr/regenerate", HttpMethod.POST, "QR WhatsApp berhasil diperbarui");
    }

    public ApiResponse<WhatsappStatusData> logout() {
        return exchange("/api/whatsapp/logout", HttpMethod.POST, "Sesi WhatsApp berhasil diputuskan");
    }

    public ApiResponse<WhatsappStatusData> resetSession() {
        return exchange("/api/whatsapp/reset-session", HttpMethod.POST, "Sesi WhatsApp berhasil direset");
    }

    public ApiResponse<Map<String, Object>> sendMessage(WhatsappSendMessageRequest request) {
        try {
            return exchangeMap("/api/whatsapp/messages/send", request, "Pesan WhatsApp berhasil dikirim");
        } catch (RestClientResponseException ex) {
            String message = extractGatewayErrorMessage(ex);
            throw new IllegalStateException(message, ex);
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("Gateway WhatsApp tidak dapat dihubungi", ex);
        }
    }

    public ApiResponse<Map<String, Object>> sendDocument(WhatsappSendDocumentRequest request) {
        try {
            return exchangeMap("/api/whatsapp/messages/send-document", request, "Dokumen WhatsApp berhasil dikirim");
        } catch (RestClientResponseException ex) {
            String message = extractGatewayErrorMessage(ex);
            throw new IllegalStateException(message, ex);
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("Gateway WhatsApp tidak dapat dihubungi", ex);
        }
    }

    public ApiResponse<Map<String, Object>> getChatOverview(Integer limit, String search) {
        try {
            return exchangeMap("/api/whatsapp/chats?limit=" + sanitizeLimit(limit) + buildSearchQuery(search), HttpMethod.GET, "Daftar chat WhatsApp berhasil diambil");
        } catch (RestClientResponseException ex) {
            String message = extractGatewayErrorMessage(ex);
            throw new IllegalStateException(message, ex);
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("Gateway WhatsApp tidak dapat dihubungi", ex);
        }
    }

    public ApiResponse<Map<String, Object>> getChatMessages(String chatId, Integer limit) {
        try {
            return exchangeMap("/api/whatsapp/chats/" + encodePath(chatId) + "/messages?limit=" + sanitizeLimit(limit), HttpMethod.GET, "Riwayat chat WhatsApp berhasil diambil");
        } catch (RestClientResponseException ex) {
            String message = extractGatewayErrorMessage(ex);
            throw new IllegalStateException(message, ex);
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("Gateway WhatsApp tidak dapat dihubungi", ex);
        }
    }

    public ApiResponse<Map<String, Object>> getMessageStatus(String messageId) {
        try {
            return exchangeMap("/api/whatsapp/messages/" + encodePath(messageId) + "/status", HttpMethod.GET, "Status pesan WhatsApp berhasil diambil");
        } catch (RestClientResponseException ex) {
            String message = extractGatewayErrorMessage(ex);
            throw new IllegalStateException(message, ex);
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("Gateway WhatsApp tidak dapat dihubungi", ex);
        }
    }

    public boolean isReady(ApiResponse<?> response) {
        if (response == null || response.getData() == null) {
            return false;
        }
        Object payload = response.getData();
        if (payload instanceof WhatsappStatusData statusData) {
            return statusData.isReady() || "connected".equalsIgnoreCase(statusData.getStatus());
        }
        return false;
    }

    public boolean isBotActive() {
        try {
            return isReady(getStatus());
        } catch (RuntimeException ex) {
            log.warn("Unable to determine WhatsApp bot status: {}", ex.getMessage());
            return false;
        }
    }

    private ApiResponse<WhatsappStatusData> exchange(String path, HttpMethod method, String fallbackMessage) {
        try {
            Map<?, ?> body = switch (method) {
                case GET -> restClient().get()
                        .uri(path)
                        .retrieve()
                        .body(Map.class);
                case POST -> restClient().post()
                        .uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of())
                        .retrieve()
                        .body(Map.class);
            };

            if (body == null) {
                throw new IllegalStateException("Gateway WhatsApp mengembalikan response kosong");
            }

            boolean success = Boolean.TRUE.equals(body.get("success"));
            String message = body.get("message") instanceof String value && !value.isBlank() ? value : fallbackMessage;
            WhatsappStatusData data = objectMapper.convertValue(body.get("data"), WhatsappStatusData.class);
            return success ? ApiResponse.success(message, data) : new ApiResponse<>(false, message, data);
        } catch (RestClientResponseException ex) {
            String message = extractGatewayErrorMessage(ex);
            log.error("WhatsApp gateway returned {} on {}: {}", ex.getStatusCode(), path, message);
            throw new IllegalStateException(message, ex);
        } catch (ResourceAccessException ex) {
            log.error("WhatsApp gateway unreachable on {}: {}", path, ex.getMessage());
            throw new IllegalStateException("Gateway WhatsApp tidak dapat dihubungi", ex);
        }
    }

    private RestClient restClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeoutMs());
        requestFactory.setReadTimeout(properties.getTimeoutMs());

        return RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Whatsapp-Session-Id", resolveSessionId())
                .requestFactory(requestFactory)
                .build();
    }

    private String resolveSessionId() {
        return whatsappTenantSessionResolver.resolveSessionId();
    }

    private ApiResponse<Map<String, Object>> exchangeMap(String path, HttpMethod method, String fallbackMessage) {
        Map<?, ?> response = switch (method) {
            case GET -> restClient().get()
                    .uri(path)
                    .retrieve()
                    .body(Map.class);
            case POST -> restClient().post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(Map.class);
        };

        if (response == null) {
            throw new IllegalStateException("Gateway WhatsApp mengembalikan response kosong");
        }

        boolean success = Boolean.TRUE.equals(response.get("success"));
        String message = response.get("message") instanceof String value && !value.isBlank() ? value : fallbackMessage;
        Map<String, Object> data = objectMapper.convertValue(response.get("data"), Map.class);
        return success ? ApiResponse.success(message, data) : new ApiResponse<>(false, message, data);
    }

    private ApiResponse<Map<String, Object>> exchangeMap(String path, Object body, String fallbackMessage) {
        Map<?, ?> response = restClient().post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalStateException("Gateway WhatsApp mengembalikan response kosong");
        }

        boolean success = Boolean.TRUE.equals(response.get("success"));
        String message = response.get("message") instanceof String value && !value.isBlank() ? value : fallbackMessage;
        Map<String, Object> data = objectMapper.convertValue(response.get("data"), Map.class);
        return success ? ApiResponse.success(message, data) : new ApiResponse<>(false, message, data);
    }

    private String extractGatewayErrorMessage(RestClientResponseException ex) {
        try {
            Map<?, ?> payload = objectMapper.readValue(ex.getResponseBodyAsString(), Map.class);
            if (payload.get("message") instanceof String message && !message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
            // Fall back to generic text below when gateway does not return JSON.
        }
        return "Gateway WhatsApp gagal memproses permintaan";
    }

    private int sanitizeLimit(Integer limit) {
        if (limit == null) {
            return 30;
        }
        return Math.max(1, Math.min(limit, 100));
    }

    private String buildSearchQuery(String search) {
        if (search == null || search.isBlank()) {
            return "";
        }
        return "&search=" + URLEncoder.encode(search, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Chat WhatsApp tidak valid");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:3001";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private enum HttpMethod {
        GET,
        POST
    }
}
