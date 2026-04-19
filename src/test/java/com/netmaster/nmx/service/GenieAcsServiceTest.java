package com.netmaster.nmx.service;

import com.netmaster.nmx.model.AcsSettings;
import com.netmaster.nmx.repository.AcsDeviceRepository;
import com.netmaster.nmx.repository.AcsSettingsRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenieAcsServiceTest {

    @Mock
    private AcsSettingsRepository acsSettingsRepository;

    @Mock
    private AcsDeviceRepository acsDeviceRepository;

    @Mock
    private CustomerServiceEntityRepository customerServiceEntityRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private GenieAcsService genieAcsService;

    @Test
    void getOrCreateSettings_retriesWhenSettingsTableIsMissing() {
        AcsSettings settings = new AcsSettings();
        settings.setId(AcsSettings.SINGLETON_ID);

        doNothing().when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(AcsSettings.SINGLETON_ID))).thenReturn(0);
        when(jdbcTemplate.update(anyString(), eq(AcsSettings.SINGLETON_ID), eq(true))).thenReturn(1);
        when(acsSettingsRepository.findById(AcsSettings.SINGLETON_ID))
                .thenThrow(new RuntimeException("ERROR: relation \"acs_settings\" does not exist"))
                .thenReturn(Optional.of(settings));

        AcsSettings result = genieAcsService.getOrCreateSettings();

        assertThat(result).isSameAs(settings);
        verify(acsSettingsRepository, times(2)).findById(AcsSettings.SINGLETON_ID);
    }
}
