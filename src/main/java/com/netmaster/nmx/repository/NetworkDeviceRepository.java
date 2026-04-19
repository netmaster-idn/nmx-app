package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.NetworkDevice;
import com.netmaster.nmx.model.NetworkDevice.DeviceStatus;
import com.netmaster.nmx.model.NetworkDevice.DeviceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NetworkDeviceRepository extends JpaRepository<NetworkDevice, Long> {

    // Find all active devices
    List<NetworkDevice> findByIsActiveTrue();

    // Find by device type
    List<NetworkDevice> findByDeviceTypeAndIsActiveTrue(DeviceType deviceType);

    // Find by status
    List<NetworkDevice> findByStatusAndIsActiveTrue(DeviceStatus status);

    // Find by location
    List<NetworkDevice> findByLocationAndIsActiveTrue(String location);

    // Find monitored devices
    List<NetworkDevice> findByIsMonitoredTrueAndIsActiveTrue();

    // Search by name or IP
    @Query("SELECT d FROM NetworkDevice d WHERE d.isActive = true AND " +
           "(LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "d.ipAddress LIKE CONCAT('%', :search, '%'))")
    List<NetworkDevice> searchDevices(@Param("search") String search);

    // Find devices by type and status
    List<NetworkDevice> findByDeviceTypeAndStatusAndIsActiveTrue(DeviceType deviceType, DeviceStatus status);

    // Find child devices (by parent)
    List<NetworkDevice> findByParentDeviceId(Long parentId);

    // Count by status
    long countByStatus(DeviceStatus status);

    // Count by type
    long countByDeviceType(DeviceType deviceType);

    // Find devices with issues (offline or warning)
    @Query("SELECT d FROM NetworkDevice d WHERE d.isActive = true AND " +
           "(d.status = 'OFFLINE' OR d.status = 'WARNING')")
    List<NetworkDevice> findDevicesWithIssues();

    // Find online devices
    @Query("SELECT d FROM NetworkDevice d WHERE d.isActive = true AND d.status = 'ONLINE'")
    List<NetworkDevice> findOnlineDevices();

    // Get devices by location with status
    @Query("SELECT d FROM NetworkDevice d WHERE d.location = :location AND d.isActive = true")
    List<NetworkDevice> findByLocation(@Param("location") String location);

    // Get dashboard stats
    @Query("SELECT " +
           "COUNT(d) as total, " +
           "SUM(CASE WHEN d.status = 'ONLINE' THEN 1 ELSE 0 END) as online, " +
           "SUM(CASE WHEN d.status = 'OFFLINE' THEN 1 ELSE 0 END) as offline, " +
           "SUM(CASE WHEN d.status = 'WARNING' THEN 1 ELSE 0 END) as warning, " +
           "SUM(CASE WHEN d.status = 'MAINTENANCE' THEN 1 ELSE 0 END) as maintenance " +
           "FROM NetworkDevice d WHERE d.isActive = true")
    Object[] getDashboardStats();

    // Find devices by type with pagination
    Page<NetworkDevice> findByDeviceType(DeviceType deviceType, Pageable pageable);

    // Find devices to monitor (ping)
    @Query("SELECT d FROM NetworkDevice d WHERE d.isActive = true AND d.isMonitored = true")
    List<NetworkDevice> findDevicesToMonitor();

    // Find device by IP
    Optional<NetworkDevice> findByIpAddress(String ipAddress);

    // Get unique locations
    @Query("SELECT DISTINCT d.location FROM NetworkDevice d WHERE d.location IS NOT NULL")
    List<String> findDistinctLocations();

    // Get device types count
    @Query("SELECT d.deviceType, COUNT(d) FROM NetworkDevice d WHERE d.isActive = true GROUP BY d.deviceType")
    List<Object[]> countByDeviceTypeGrouped();
}

