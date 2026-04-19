package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.CustomerServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface CustomerServiceEntityRepository extends JpaRepository<CustomerServiceEntity, Long> {
    List<CustomerServiceEntity> findByCustomerId(Long customerId);
    Optional<CustomerServiceEntity> findByCustomerIdAndStatus(Long customerId, String status);
    Optional<CustomerServiceEntity> findTopByCustomerIdOrderByCreatedAtDesc(Long customerId);
    @EntityGraph(attributePaths = {"internetPackage"})
    Optional<CustomerServiceEntity> findTopWithInternetPackageByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = {"customer", "internetPackage"})
    @Query("""
            SELECT cs
            FROM CustomerServiceEntity cs
            WHERE cs.customer.id IN :customerIds
              AND cs.id IN (
                SELECT MAX(innerCs.id)
                FROM CustomerServiceEntity innerCs
                WHERE innerCs.customer.id IN :customerIds
                GROUP BY innerCs.customer.id
              )
            """)
    List<CustomerServiceEntity> findLatestWithInternetPackageByCustomerIds(@Param("customerIds") List<Long> customerIds);
    List<CustomerServiceEntity> findByPppoeUsernameIgnoreCaseOrderByIdDesc(String pppoeUsername);
    List<CustomerServiceEntity> findByOntSerialOrderByIdDesc(String ontSerial);
    @EntityGraph(attributePaths = {"customer", "customer.region"})
    @Query("""
            SELECT cs
            FROM CustomerServiceEntity cs
            LEFT JOIN FETCH cs.customer c
            LEFT JOIN FETCH c.region
            WHERE LOWER(cs.pppoeUsername) = LOWER(:pppoeUsername)
            ORDER BY cs.id DESC
            """)
    List<CustomerServiceEntity> findDetailedByPppoeUsername(@Param("pppoeUsername") String pppoeUsername);

    @EntityGraph(attributePaths = {"customer", "customer.region", "internetPackage"})
    @Query("""
            SELECT cs
            FROM CustomerServiceEntity cs
            LEFT JOIN FETCH cs.customer c
            LEFT JOIN FETCH c.region
            LEFT JOIN FETCH cs.internetPackage p
            WHERE cs.ipAddress = :ipAddress
            """)
    Optional<CustomerServiceEntity> findDetailedByIpAddress(@Param("ipAddress") String ipAddress);
    @EntityGraph(attributePaths = {"customer", "customer.region"})
    @Query("""
            SELECT cs
            FROM CustomerServiceEntity cs
            LEFT JOIN FETCH cs.customer c
            LEFT JOIN FETCH c.region
            WHERE cs.ontSerial = :ontSerial
            ORDER BY cs.id DESC
            """)
    List<CustomerServiceEntity> findDetailedByOntSerial(@Param("ontSerial") String ontSerial);
    Optional<CustomerServiceEntity> findByOdpIdAndOdpPort(Long odpId, Integer odpPort);
    long countByOdpId(Long odpId);
    List<CustomerServiceEntity> findByStatusOrderByCreatedAtDesc(String status);
    long countByTechnicianId(Long technicianId);

    @Query("""
            SELECT DISTINCT cs
            FROM CustomerServiceEntity cs
            LEFT JOIN FETCH cs.customer c
            LEFT JOIN FETCH cs.odp odp
            LEFT JOIN FETCH odp.companyProfile
            LEFT JOIN FETCH odp.odc odc
            LEFT JOIN FETCH odc.server
            WHERE c.latitude IS NOT NULL AND c.longitude IS NOT NULL
            ORDER BY c.fullName
            """)
    List<CustomerServiceEntity> findAllForMapping();

    @Query("""
            SELECT cs
            FROM CustomerServiceEntity cs
            LEFT JOIN FETCH cs.customer c
            LEFT JOIN FETCH cs.odp odp
            LEFT JOIN FETCH odp.companyProfile
            LEFT JOIN FETCH odp.odc odc
            LEFT JOIN FETCH odc.server
            WHERE cs.id = :id
            """)
    Optional<CustomerServiceEntity> findMappingDetailById(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM CustomerServiceEntity cs WHERE cs.odp.id IN :odpIds")
    int deleteByOdpIds(@Param("odpIds") List<Long> odpIds);

    @Modifying
    @Query("UPDATE CustomerServiceEntity cs SET cs.status = 'pending', cs.activationDate = null")
    int resetAllServiceStatusesToPending();
}

