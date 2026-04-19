package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.DeviceMetrics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceMetricsRepository extends JpaRepository<DeviceMetrics, Long> {

    // Find latest metrics for a device
    Optional<DeviceMetrics> findTopByDeviceIdOrderByTimestampDesc(Long deviceId);

    // Find latest metrics for multiple devices
    @Query("SELECT m FROM DeviceMetrics m WHERE m.device.id IN :deviceIds " +
           "AND m.timestamp = (SELECT MAX(m2.timestamp) FROM DeviceMetrics m2 WHERE m2.device.id = m.device.id)")
    List<DeviceMetrics> findLatestByDeviceIds(@Param("deviceIds") List<Long> deviceIds);

    @Query("SELECT m FROM DeviceMetrics m WHERE m.device.id IN :deviceIds " +
           "AND (m.interfaceName IS NULL OR m.interfaceName = '') " +
           "AND m.timestamp = (SELECT MAX(m2.timestamp) FROM DeviceMetrics m2 WHERE m2.device.id = m.device.id " +
           "AND (m2.interfaceName IS NULL OR m2.interfaceName = ''))")
    List<DeviceMetrics> findLatestSummaryByDeviceIds(@Param("deviceIds") List<Long> deviceIds);

    // Find metrics by device and time range
    List<DeviceMetrics> findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
            Long deviceId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT m FROM DeviceMetrics m WHERE m.device.id = :deviceId " +
           "AND (m.interfaceName IS NULL OR m.interfaceName = '') " +
           "AND m.timestamp BETWEEN :start AND :end ORDER BY m.timestamp ASC")
    List<DeviceMetrics> findSummaryMetricsByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
            @Param("deviceId") Long deviceId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // Find latest metrics by device
    @Query("SELECT m FROM DeviceMetrics m WHERE m.device.id = :deviceId ORDER BY m.timestamp DESC")
    List<DeviceMetrics> findLatestMetrics(@Param("deviceId") Long deviceId, Pageable pageable);

    @Query("SELECT m FROM DeviceMetrics m " +
           "JOIN FETCH m.device d " +
           "WHERE d.id IN :deviceIds " +
           "AND m.timestamp >= :since " +
           "AND m.interfaceName IS NOT NULL AND m.interfaceName <> '' " +
           "ORDER BY m.timestamp DESC")
    List<DeviceMetrics> findRecentInterfaceMetricsWithDevice(
            @Param("deviceIds") List<Long> deviceIds,
            @Param("since") LocalDateTime since);

    // Get average CPU for a device in time range
    @Query("SELECT AVG(m.cpuUsage) FROM DeviceMetrics m WHERE m.device.id = :deviceId " +
           "AND m.timestamp BETWEEN :start AND :end")
    Double getAverageCpu(@Param("deviceId") Long deviceId,
                         @Param("start") LocalDateTime start,
                         @Param("end") LocalDateTime end);

    // Get average memory for a device in time range
    @Query("SELECT AVG(m.memoryUsage) FROM DeviceMetrics m WHERE m.device.id = :deviceId " +
           "AND m.timestamp BETWEEN :start AND :end")
    Double getAverageMemory(@Param("deviceId") Long deviceId,
                            @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);

    // Get max CPU in time range
    @Query("SELECT MAX(m.cpuUsage) FROM DeviceMetrics m WHERE m.device.id = :deviceId " +
           "AND m.timestamp BETWEEN :start AND :end")
    Double getMaxCpu(@Param("deviceId") Long deviceId,
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);

    // Get average latency
    @Query("SELECT AVG(m.latencyMs) FROM DeviceMetrics m WHERE m.device.id = :deviceId " +
           "AND m.timestamp BETWEEN :start AND :end AND m.latencyMs IS NOT NULL")
    Double getAverageLatency(@Param("deviceId") Long deviceId,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    // Get average packet loss
    @Query("SELECT AVG(m.packetLoss) FROM DeviceMetrics m WHERE m.device.id = :deviceId " +
           "AND m.timestamp BETWEEN :start AND :end AND m.packetLoss IS NOT NULL")
    Double getAveragePacketLoss(@Param("deviceId") Long deviceId,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    // Get total traffic (RX + TX) in time range
    @Query("SELECT SUM(m.trafficRxBytes + m.trafficTxBytes) FROM DeviceMetrics m " +
           "WHERE m.device.id = :deviceId AND m.timestamp BETWEEN :start AND :end")
    Long getTotalTraffic(@Param("deviceId") Long deviceId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);

    // Get metrics with interface name
    List<DeviceMetrics> findByDeviceIdAndInterfaceNameOrderByTimestampDesc(
            Long deviceId, String interfaceName);

    // Get latest interface metrics
    Optional<DeviceMetrics> findTopByDeviceIdAndInterfaceNameOrderByTimestampDesc(
            Long deviceId, String interfaceName);

    // Delete old metrics (for cleanup)
    void deleteByTimestampBefore(LocalDateTime timestamp);

    // Count metrics for a device
    long countByDeviceId(Long deviceId);

    // Get OLT specific metrics
    @Query("SELECT m FROM DeviceMetrics m WHERE m.device.id = :deviceId " +
           "AND m.onuTotal IS NOT NULL ORDER BY m.timestamp DESC")
    List<DeviceMetrics> findOltMetrics(@Param("deviceId") Long deviceId, Pageable pageable);

    // Get traffic data for charting
    @Query("SELECT m.timestamp, m.trafficRxBps, m.trafficTxBps FROM DeviceMetrics m " +
           "WHERE m.device.id = :deviceId AND m.timestamp BETWEEN :start AND :end " +
           "ORDER BY m.timestamp ASC")
    List<Object[]> getTrafficChartData(@Param("deviceId") Long deviceId,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    // Get average bandwidth for all online devices
    @Query("SELECT AVG(m.trafficRxBps + m.trafficTxBps) FROM DeviceMetrics m " +
           "WHERE m.device.status = 'ONLINE' AND m.timestamp > :since")
    Double getAverageTotalBandwidth(@Param("since") LocalDateTime since);
}

