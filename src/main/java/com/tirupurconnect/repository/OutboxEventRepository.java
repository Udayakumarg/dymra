package com.tirupurconnect.repository;
import com.tirupurconnect.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC LIMIT 50")
    List<OutboxEvent> findPendingEvents();

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'FAILED' AND o.retryCount < :max ORDER BY o.createdAt ASC LIMIT 20")
    List<OutboxEvent> findRetryableEvents(@Param("max") int max);
}
