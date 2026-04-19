package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.DeviceHealthSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface DeviceHealthSnapshotRepository extends JpaRepository<DeviceHealthSnapshot, Long> {

    List<DeviceHealthSnapshot> findTop48ByDeviceIdOrderByCapturedAtDesc(Long deviceId);

    List<DeviceHealthSnapshot> findByCapturedAtAfterOrderByCapturedAtAsc(LocalDateTime capturedAt);

    List<DeviceHealthSnapshot> findByDeviceIdAndCapturedAtAfterOrderByCapturedAtAsc(Long deviceId, LocalDateTime capturedAt);

    long countByDeviceIdAndStatusAndCapturedAtAfter(Long deviceId, String status, LocalDateTime capturedAt);

    void deleteByCapturedAtBefore(LocalDateTime capturedAt);
}
