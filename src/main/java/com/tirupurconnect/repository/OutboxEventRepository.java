package com.tirupurconnect.repository;

import com.tirupurconnect.model.OutboxEvent;
import com.tirupurconnect.model.OutboxEvent.OutboxStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // FIX: pass enum constant as param — avoids varchar vs outbox_status cast error in PostgreSQL
    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status ORDER BY o.createdAt ASC")
    List<OutboxEvent> findByStatusOrdered(@Param("status") OutboxStatus status, Pageable pageable);

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status AND o.retryCount < :max ORDER BY o.createdAt ASC")
    List<OutboxEvent> findRetryableByStatus(@Param("status") OutboxStatus status,
                                             @Param("max") int max,
                                             Pageable pageable);

    default List<OutboxEvent> findPendingEvents() {
        return findByStatusOrdered(OutboxStatus.PENDING, PageRequest.of(0, 50));
    }

    default List<OutboxEvent> findRetryableEvents(int maxRetries) {
        return findRetryableByStatus(OutboxStatus.FAILED, maxRetries, PageRequest.of(0, 20));
    }
}
