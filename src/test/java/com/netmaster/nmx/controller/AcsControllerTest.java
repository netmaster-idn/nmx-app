package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.model.AcsSettings;
import com.netmaster.nmx.repository.AcsDeviceRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import com.netmaster.nmx.service.GenieAcsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AcsControllerTest {

    @Mock
    private AcsDeviceRepository acsRepository;

    @Mock
    private GenieAcsService genieAcsService;

    @Mock
    private CustomerServiceEntityRepository customerServiceEntityRepository;

    @InjectMocks
    private AcsController acsController;

    private MockMvc buildMockMvc() {
        return MockMvcBuilders.standaloneSetup(acsController).build();
    }

    @Test
    void getSettings_returnsOkWhenSettingsExist() {
        AcsSettings settings = new AcsSettings();
        settings.setServerUrl("http://localhost:7557");
        settings.setUsername("admin");
        settings.setPassword("secret");
        settings.setActive(true);
        settings.setLastConnectionStatus("success");

        when(genieAcsService.getOrCreateSettings()).thenReturn(settings);

        ResponseEntity<ApiResponse<AcsSettingsResponse>> response = acsController.getSettings();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().serverUrl()).isEqualTo("http://localhost:7557");
        assertThat(response.getBody().getData().username()).isEqualTo("admin");
        assertThat(response.getBody().getData().passwordConfigured()).isTrue();
        assertThat(response.getBody().getData().active()).isTrue();
    }

    @Test
    void getSettings_returnsDefaultPayloadWhenServiceFails() {
        when(genieAcsService.getOrCreateSettings()).thenThrow(new IllegalStateException("boom"));

        ResponseEntity<ApiResponse<AcsSettingsResponse>> response = acsController.getSettings();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().serverUrl()).isNull();
        assertThat(response.getBody().getData().active()).isFalse();
        assertThat(response.getBody().getData().lastConnectionStatus()).isEqualTo("unavailable");
    }

    @Test
    void getSettings_routeIsResolvedBySpringMvc() throws Exception {
        AcsSettings settings = new AcsSettings();
        settings.setServerUrl("http://localhost:7557");
        settings.setActive(true);
        when(genieAcsService.getOrCreateSettings()).thenReturn(settings);

        buildMockMvc().perform(get("/api/network/acs/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.serverUrl").value("http://localhost:7557"));
    }
}
