package com.tirupurconnect.repository;

import com.tirupurconnect.model.OutboxEvent;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // FIX: JPQL does not support LIMIT — use Pageable instead
    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
    List<OutboxEvent> findPendingEvents(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'FAILED' AND o.retryCount < :max ORDER BY o.createdAt ASC")
    List<OutboxEvent> findRetryableEvents(@Param("max") int max,
                                           org.springframework.data.domain.Pageable pageable);

    // Convenience defaults used by OutboxScheduler
    default List<OutboxEvent> findPendingEvents() {
        return findPendingEvents(PageRequest.of(0, 50));
    }

    default List<OutboxEvent> findRetryableEvents(int max) {
        return findRetryableEvents(max, PageRequest.of(0, 20));
    }
}
