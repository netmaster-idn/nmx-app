package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.Odc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OdcRepository extends JpaRepository<Odc, Long> {
    List<Odc> findByIsActiveTrueOrderByNameAsc();
    List<Odc> findByServerIdOrderByNameAsc(Long serverId);
    
    // Fetch join query to eagerly load Server relationship for JSON serialization
    @Query("SELECT DISTINCT o FROM Odc o LEFT JOIN FETCH o.server WHERE o.isActive = true ORDER BY o.name")
    List<Odc> findAllActiveWithServer();
    
    @Query("SELECT o FROM Odc o LEFT JOIN FETCH o.server WHERE o.isActive = true AND o.server.id = :serverId ORDER BY o.name")
    List<Odc> findByServerIdWithServer(Long serverId);

    @Modifying
    @Query("""
            UPDATE Odc o
            SET o.name = :name,
                o.latitude = :latitude,
                o.longitude = :longitude,
                o.location = :location,
                o.server = :server
            WHERE o.id = :id
            """)
    int updateMappingNode(
            @Param("id") Long id,
            @Param("name") String name,
            @Param("latitude") java.math.BigDecimal latitude,
            @Param("longitude") java.math.BigDecimal longitude,
            @Param("location") String location,
            @Param("server") com.netmaster.nmx.model.Server server
    );
}

