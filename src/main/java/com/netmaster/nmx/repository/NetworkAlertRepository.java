package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.NetworkAlert;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NetworkAlertRepository extends JpaRepository<NetworkAlert, Long> {
    
    // Basic queries
    List<NetworkAlert> findByDeviceId(Long deviceId);
    
    List<NetworkAlert> findBySeverity(String severity);
    
    List<NetworkAlert> findByStatus(String status);
    
    List<NetworkAlert> findByDeviceName(String deviceName);
    
    List<NetworkAlert> findByLocation(String location);
    
    List<NetworkAlert> findByDeviceType(String deviceType);
    
    Optional<NetworkAlert> findByAlertId(String alertId);
    
    // Active alerts - ordered by severity priority
    @Query("SELECT a FROM NetworkAlert a WHERE a.status != 'closed' ORDER BY " +
           "CASE a.severity WHEN 'critical' THEN 1 WHEN 'major' THEN 2 WHEN 'warning' THEN 3 ELSE 4 END, a.createdAt DESC")
    List<NetworkAlert> findActiveAlerts();
    
    // Unacknowledged alerts
    @Query("SELECT a FROM NetworkAlert a WHERE a.isAcknowledged = false AND a.status != 'closed' ORDER BY " +
           "CASE a.severity WHEN 'critical' THEN 1 WHEN 'major' THEN 2 WHEN 'warning' THEN 3 ELSE 4 END, a.createdAt DESC")
    List<NetworkAlert> findUnacknowledgedAlerts();
    
    // Count queries
    @Query("SELECT COUNT(a) FROM NetworkAlert a WHERE a.isAcknowledged = false AND a.status != 'closed'")
    Long countUnacknowledged();
    
    @Query("SELECT COUNT(a) FROM NetworkAlert a WHERE a.status = 'active'")
    Long countActive();
    
    @Query("SELECT COUNT(a) FROM NetworkAlert a WHERE a.status = 'acknowledged'")
    Long countAcknowledged();
    
    @Query("SELECT COUNT(a) FROM NetworkAlert a WHERE a.status = 'investigating'")
    Long countInvestigating();
    
    @Query("SELECT COUNT(a) FROM NetworkAlert a WHERE a.status = 'resolved'")
    Long countResolved();
    
    @Query("SELECT COUNT(a) FROM NetworkAlert a WHERE a.status = 'closed'")
    Long countClosed();
    
    @Query("SELECT COUNT(a) FROM NetworkAlert a WHERE a.severity = :severity AND a.status != 'closed'")
    Long countBySeverity(@Param("severity") String severity);
    
    // Filtered queries
    @Query("SELECT a FROM NetworkAlert a WHERE " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:deviceType IS NULL OR a.deviceType = :deviceType) AND " +
           "(:location IS NULL OR a.location = :location) AND " +
           "(:deviceName IS NULL OR a.deviceName LIKE %:deviceName%) AND " +
           "(:alertId IS NULL OR a.alertId LIKE %:alertId%) AND " +
           "(:source IS NULL OR a.source = :source) " +
           "ORDER BY CASE a.severity WHEN 'critical' THEN 1 WHEN 'major' THEN 2 WHEN 'warning' THEN 3 ELSE 4 END, a.createdAt DESC")
    List<NetworkAlert> findByFilters(
            @Param("severity") String severity,
            @Param("status") String status,
            @Param("deviceType") String deviceType,
            @Param("location") String location,
            @Param("deviceName") String deviceName,
            @Param("alertId") String alertId,
            @Param("source") String source
    );
    
    // Time-based queries
    @Query("SELECT a FROM NetworkAlert a WHERE a.createdAt >= :startTime ORDER BY a.createdAt DESC")
    List<NetworkAlert> findAlertsAfter(@Param("startTime") LocalDateTime startTime);
    
    @Query("SELECT a FROM NetworkAlert a WHERE a.createdAt BETWEEN :startTime AND :endTime ORDER BY a.createdAt DESC")
    List<NetworkAlert> findAlertsBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    
    // Analytics queries
    @Query("SELECT a.deviceName, COUNT(a) FROM NetworkAlert a WHERE a.createdAt >= :since GROUP BY a.deviceName ORDER BY COUNT(a) DESC")
    List<Object[]> findTopProblemDevices(@Param("since") LocalDateTime since, org.springframework.data.domain.Pageable pageable);
    
    @Query("SELECT a.alertType, COUNT(a) FROM NetworkAlert a WHERE a.createdAt >= :since GROUP BY a.alertType ORDER BY COUNT(a) DESC")
    List<Object[]> findMostFrequentAlerts(@Param("since") LocalDateTime since, org.springframework.data.domain.Pageable pageable);
    
    @Query("SELECT a.severity, COUNT(a) FROM NetworkAlert a WHERE a.createdAt >= :since GROUP BY a.severity")
    List<Object[]> countBySeveritySince(@Param("since") LocalDateTime since);
    
    // MTTR Calculation
    List<NetworkAlert> findByResolvedAtIsNotNullAndCreatedAtGreaterThanEqual(LocalDateTime since);

    default Double calculateAverageMTTRMinutes(LocalDateTime since) {
        return findByResolvedAtIsNotNullAndCreatedAtGreaterThanEqual(since).stream()
                .map(NetworkAlert::getResolutionTimeMinutes)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0d);
    }
    
    // Grouping queries
    @Query("SELECT a.location, COUNT(a) FROM NetworkAlert a WHERE a.status != 'closed' GROUP BY a.location ORDER BY COUNT(a) DESC")
    List<Object[]> countByLocation();
    
    @Query("SELECT a.deviceType, COUNT(a) FROM NetworkAlert a WHERE a.status != 'closed' GROUP BY a.deviceType ORDER BY COUNT(a) DESC")
    List<Object[]> countByDeviceType();
    
    // Alert timeline for detail view
    @Query("SELECT a FROM NetworkAlert a WHERE a.deviceId = :deviceId AND a.status != 'closed' ORDER BY a.createdAt DESC")
    List<NetworkAlert> findRelatedAlertsByDevice(@Param("deviceId") Long deviceId);
    
    // Recent alerts for timeline
    List<NetworkAlert> findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);

    default List<NetworkAlert> findRecentAlerts(int limit) {
        return findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }
}

