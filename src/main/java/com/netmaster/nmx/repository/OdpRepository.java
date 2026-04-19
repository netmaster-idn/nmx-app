package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.Odp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OdpRepository extends JpaRepository<Odp, Long> {
    List<Odp> findByIsActiveTrueOrderByNameAsc();
    List<Odp> findByOdcIdOrderByNameAsc(Long odcId);
    long countByOdcId(Long odcId);
    @Query("SELECT o.id FROM Odp o WHERE o.odc.id = :odcId")
    List<Long> findIdsByOdcId(@Param("odcId") Long odcId);
    boolean existsByIdAndNodeTypeIgnoreCase(Long id, String nodeType);
    
    @Query("SELECT o FROM Odp o WHERE o.isActive = true AND (o.capacity - o.usedPort) > 0 ORDER BY (o.capacity - o.usedPort) DESC")
    List<Odp> findAvailableOdps();
    
    @Query("SELECT o FROM Odp o WHERE o.id = :id")
    Odp findByIdWithDetails(@Param("id") Long id);
    
    // Fetch join query to eagerly load Odc relationship for JSON serialization
    @Query("SELECT DISTINCT o FROM Odp o LEFT JOIN FETCH o.odc WHERE o.isActive = true ORDER BY o.name")
    List<Odp> findAllActiveWithOdc();

    @Query("SELECT DISTINCT o FROM Odp o LEFT JOIN FETCH o.odc odc LEFT JOIN FETCH odc.server LEFT JOIN FETCH o.companyProfile WHERE o.isActive = true ORDER BY o.name")
    List<Odp> findAllActiveWithOdcAndServer();

    List<Odp> findByNodeTypeIgnoreCaseAndIsActiveTrue(String nodeType);

    @Query("""
            SELECT DISTINCT o
            FROM Odp o
            LEFT JOIN FETCH o.odc odc
            LEFT JOIN FETCH odc.server
            LEFT JOIN FETCH o.companyProfile
            WHERE o.isActive = true
              AND UPPER(o.nodeType) = UPPER(:nodeType)
            ORDER BY o.name
            """)
    List<Odp> findByNodeTypeIgnoreCaseAndIsActiveTrueWithRelations(@Param("nodeType") String nodeType);

    @Query("SELECT o FROM Odp o LEFT JOIN FETCH o.odc WHERE o.isActive = true AND o.odc.id = :odcId ORDER BY o.name")
    List<Odp> findByOdcIdWithOdc(Long odcId);

    @Modifying
    @Query("""
            UPDATE Odp o
            SET o.name = :name,
                o.splitter = :splitter,
                o.latitude = :latitude,
                o.longitude = :longitude,
                o.location = :location
            WHERE o.id = :id
            """)
    int updateMappingNode(
            @Param("id") Long id,
            @Param("name") String name,
            @Param("splitter") String splitter,
            @Param("latitude") java.math.BigDecimal latitude,
            @Param("longitude") java.math.BigDecimal longitude,
            @Param("location") String location
    );

    @Modifying
    @Query("DELETE FROM Odp o WHERE o.odc.id = :odcId")
    int deleteByOdcId(@Param("odcId") Long odcId);

}

