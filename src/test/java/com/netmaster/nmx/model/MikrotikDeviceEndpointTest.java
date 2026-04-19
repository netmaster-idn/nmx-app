package com.netmaster.nmx.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MikrotikDeviceEndpointTest {

    @Test
    void resolvesCombinedVpnEndpointAndSynchronizesApiPort() {
        MikrotikDevice device = new MikrotikDevice();
        device.setVpnIpAddress("103.103.21.27:9437");
        device.setApiPort(null);

        device.onLoad();

        assertThat(device.resolveVpnHost()).isEqualTo("103.103.21.27");
        assertThat(device.resolveVpnPort()).isEqualTo(9437);
        assertThat(device.getApiPort()).isEqualTo(9437);
        assertThat(device.getVpnEndpoint()).isEqualTo("103.103.21.27:9437");
    }

    @Test
    void appendsApiPortForLegacyVpnHostOnlyData() {
        MikrotikDevice device = new MikrotikDevice();
        device.setVpnIpAddress("10.200.0.1");
        device.setApiPort(8729);

        device.onLoad();

        assertThat(device.resolveVpnHost()).isEqualTo("10.200.0.1");
        assertThat(device.resolveVpnPort()).isEqualTo(8729);
        assertThat(device.getVpnEndpoint()).isEqualTo("10.200.0.1:8729");
        assertThat(device.getVpnIpAddress()).isEqualTo("10.200.0.1:8729");
    }
}
