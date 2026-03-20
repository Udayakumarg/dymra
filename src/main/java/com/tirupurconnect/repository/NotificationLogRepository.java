package com.tirupurconnect.repository;

import com.tirupurconnect.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    @Query("SELECT COUNT(n) FROM NotificationLog n WHERE n.supplierId = :id AND n.delivered = false AND n.sentAt >= :since")
    long countUndeliveredSince(@Param("id") UUID id, @Param("since") Instant since);
}
