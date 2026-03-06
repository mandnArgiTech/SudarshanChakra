package com.sudarshanchakra.alert.repository;

import com.sudarshanchakra.alert.model.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Page<Alert> findByPriority(String priority, Pageable pageable);

    Page<Alert> findByStatus(String status, Pageable pageable);

    Page<Alert> findByNodeId(String nodeId, Pageable pageable);

    @Query("SELECT a FROM Alert a WHERE a.status IN ('new', 'acknowledged') ORDER BY a.createdAt DESC")
    Page<Alert> findActiveAlerts(Pageable pageable);

    @Query("SELECT a FROM Alert a WHERE " +
            "(:priority IS NULL OR a.priority = :priority) AND " +
            "(:status IS NULL OR a.status = :status) AND " +
            "(:nodeId IS NULL OR a.nodeId = :nodeId) " +
            "ORDER BY a.createdAt DESC")
    Page<Alert> findFiltered(
            @Param("priority") String priority,
            @Param("status") String status,
            @Param("nodeId") String nodeId,
            Pageable pageable);
}
