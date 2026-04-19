package com.netmaster.nmx.service;

import com.netmaster.nmx.model.MikrotikDevice;
import com.netmaster.nmx.repository.MikrotikDeviceMetricRepository;
import com.netmaster.nmx.repository.MikrotikDeviceRepository;
import com.netmaster.nmx.repository.MikrotikInterfaceTrafficRepository;
import com.netmaster.nmx.repository.MikrotikPppoeEventRepository;
import com.netmaster.nmx.repository.MikrotikPppoeSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "nmx.crypto.secret=test-hybrid-secret-key",
        "nmx.bootstrap.seed-default-users=false"
})
class MikrotikHybridSchedulerIntegrationTest {

    @Autowired
    private MikrotikHybridScheduler scheduler;

    @Autowired
    private MikrotikDeviceRepository mikrotikDeviceRepository;

    @Autowired
    private MikrotikDeviceMetricRepository mikrotikDeviceMetricRepository;

    @Autowired
    private MikrotikInterfaceTrafficRepository mikrotikInterfaceTrafficRepository;

    @Autowired
    private MikrotikPppoeSessionRepository mikrotikPppoeSessionRepository;

    @Autowired
    private MikrotikPppoeEventRepository mikrotikPppoeEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private MikrotikApiService mikrotikApiService;

    @Test
    void schedulerPersistsMonitoringAndApiSnapshotsAndEncryptsSensitiveFields() {
        MikrotikDevice device = new MikrotikDevice();
        device.applyDeviceName("MikroTik Test Hybrid");
        device.applySiteName("POP Test");
        device.setIpAddress("10.10.10.1");
        device.setApiIpAddress("10.10.10.1");
        device.setApiPort(8728);

        device.setSnmpCommunity("public-secret");
        device.applyApiCredentials("admin", "super-secret");
        device.setSnmpEnabled(false);
        device.setApiEnabled(true);
        device.setPollingIntervalSnmp(60);
        device.setSyncIntervalApi(120);
        device.setActive(true);
        device = mikrotikDeviceRepository.save(device);

        LocalDateTime now = LocalDateTime.now();
        when(mikrotikApiService.collectMonitoringSnapshot(any(MikrotikDevice.class)))
                .thenReturn(new MikrotikApiService.MonitoringSnapshot(
                        "RTR-POP-TEST",
                        true,
                        7200L,
                        24,
                        1024L,
                        512L,
                        512L,
                        new BigDecimal("43.50"),
                        new BigDecimal("24.10"),
                        "healthy",
                        "7.16.2",
                        "RB4011",
                        List.of(new MikrotikApiService.MonitoringInterfaceSnapshot(
                                1,
                                "ether1",
                                "ethernet",
                                "WAN Primary",
                                "up",
                                "up",
                                1_000_000L,
                                2_000_000L
                        )),
                        now
                ));

        when(mikrotikApiService.collectPppSnapshot(any(MikrotikDevice.class)))
                .thenReturn(new MikrotikApiService.PppSyncSnapshot(
                        List.of(new MikrotikRouterOsApiClient.MikrotikPppSessionSnapshot(
                                "cust01",
                                "172.16.1.10",
                                "AA:BB:CC:DD:EE:FF",
                                "SID-001",
                                "basic-profile",
                                180L,
                                "{name=cust01}"
                        )),
                        Map.of("cust01", "basic-profile"),
                        List.of(new MikrotikRouterOsApiClient.MikrotikLogSnapshot(
                                now,
                                "ppp,info",
                                "cust01 logged in",
                                "*1"
                        )),
                        now
                ));

        scheduler.runMonitoringPoller();
        scheduler.runApiSyncWorker();

        assertThat(mikrotikDeviceMetricRepository.findTopByDeviceIdOrderByCollectedAtDesc(device.getId())).isPresent();
        assertThat(mikrotikInterfaceTrafficRepository.findRecentWithDeviceAndInterface(now.minusMinutes(5))).hasSize(1);
        assertThat(mikrotikPppoeSessionRepository.findByDeviceIdOrderByLastSyncAtDesc(device.getId()))
                .filteredOn(session -> "cust01".equals(session.getUsername()))
                .hasSize(1);
        assertThat(mikrotikPppoeEventRepository.findTop100ByDeviceIdOrderByEventTimeDesc(device.getId()))
                .filteredOn(event -> "cust01".equals(event.getUsername()))
                .hasSize(1);

        Map<String, Object> raw = jdbcTemplate.queryForMap(
                "select api_password, snmp_community from mikrotik_devices where id = ?",
                device.getId()
        );
        assertThat(String.valueOf(raw.get("api_password"))).doesNotContain("super-secret");
        assertThat(String.valueOf(raw.get("snmp_community"))).doesNotContain("public-secret");

        MikrotikDevice reloaded = mikrotikDeviceRepository.findById(device.getId()).orElseThrow();
        assertThat(reloaded.resolveApiPassword()).isEqualTo("super-secret");
        assertThat(reloaded.getSnmpCommunity()).isEqualTo("public-secret");
        assertThat(reloaded.getApiPasswordMasked()).isEqualTo("su****et");
        assertThat(reloaded.getSnmpCommunityMasked()).isEqualTo("pu****et");
    }
}
