package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.NetworkTopologyLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NetworkTopologyLinkRepository extends JpaRepository<NetworkTopologyLink, Long> {

    List<NetworkTopologyLink> findByIsActiveTrueOrderByIdAsc();

    Optional<NetworkTopologyLink> findByIsActiveTrueAndFromNodeTypeAndFromNodeIdAndToNodeTypeAndToNodeId(
            String fromNodeType,
            Long fromNodeId,
            String toNodeType,
            Long toNodeId
    );

    @Modifying
    @Query("""
            UPDATE NetworkTopologyLink l
            SET l.isActive = false
            WHERE l.isActive = true
              AND ((l.fromNodeType = :nodeType AND l.fromNodeId = :nodeId)
                OR (l.toNodeType = :nodeType AND l.toNodeId = :nodeId))
            """)
    int deactivateLinksForNode(@Param("nodeType") String nodeType, @Param("nodeId") Long nodeId);

    @Modifying
    @Query("""
            DELETE FROM NetworkTopologyLink l
            WHERE (l.fromNodeType = :nodeType AND l.fromNodeId = :nodeId)
               OR (l.toNodeType = :nodeType AND l.toNodeId = :nodeId)
            """)
    int deleteLinksForNode(@Param("nodeType") String nodeType, @Param("nodeId") Long nodeId);

    @Modifying
    @Query("""
            DELETE FROM NetworkTopologyLink l
            WHERE (l.fromNodeType = :nodeType AND l.fromNodeId IN :nodeIds)
               OR (l.toNodeType = :nodeType AND l.toNodeId IN :nodeIds)
            """)
    int deleteLinksForNodes(@Param("nodeType") String nodeType, @Param("nodeIds") List<Long> nodeIds);
}
