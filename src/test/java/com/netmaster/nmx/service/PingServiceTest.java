package com.netmaster.nmx.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PingServiceTest {

    @Test
    void buildPingCommandUsesWindowsFlags() {
        List<String> command = PingService.buildPingCommand("Windows 11", 4, 5, "10.10.10.10");

        assertEquals(List.of("ping", "-n", "4", "-w", "5000", "10.10.10.10"), command);
    }

    @Test
    void buildPingCommandUsesLinuxFlags() {
        List<String> command = PingService.buildPingCommand("Linux", 4, 5, "10.10.10.10");

        assertEquals(List.of("ping", "-c", "4", "-W", "5", "10.10.10.10"), command);
    }

    @Test
    void parsePingOutputReadsWindowsAverageLatency() {
        String output = """
                Pinging 10.10.10.10 with 32 bytes of data:
                Reply from 10.10.10.10: bytes=32 time=14ms TTL=63

                Ping statistics for 10.10.10.10:
                    Packets: Sent = 4, Received = 4, Lost = 0 (0% loss),
                Approximate round trip times in milli-seconds:
                    Minimum = 12ms, Maximum = 18ms, Average = 14ms
                """;

        PingService.PingResult result = PingService.parsePingOutput(output);

        assertTrue(result.isReachable());
        assertEquals(new BigDecimal("14"), result.getAverageLatency());
    }

    @Test
    void parsePingOutputReadsLinuxAverageLatency() {
        String output = """
                PING 10.10.10.10 (10.10.10.10) 56(84) bytes of data.
                64 bytes from 10.10.10.10: icmp_seq=1 ttl=63 time=16.2 ms

                --- 10.10.10.10 ping statistics ---
                4 packets transmitted, 4 received, 0% packet loss, time 3006ms
                rtt min/avg/max/mdev = 15.101/16.245/17.848/0.991 ms
                """;

        PingService.PingResult result = PingService.parsePingOutput(output);

        assertTrue(result.isReachable());
        assertEquals(new BigDecimal("16.245"), result.getAverageLatency());
    }

    @Test
    void parsePingOutputDetectsPacketLossFailure() {
        String output = """
                PING 10.10.10.10 (10.10.10.10) 56(84) bytes of data.

                --- 10.10.10.10 ping statistics ---
                4 packets transmitted, 0 received, 100% packet loss, time 3059ms
                """;

        PingService.PingResult result = PingService.parsePingOutput(output);

        assertFalse(result.isReachable());
        assertEquals(null, result.getAverageLatency());
    }
}
