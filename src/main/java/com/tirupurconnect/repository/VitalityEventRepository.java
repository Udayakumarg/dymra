package com.tirupurconnect.repository;

import com.tirupurconnect.model.VitalityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface VitalityEventRepository extends JpaRepository<VitalityEvent, Long> {

    // Returns Long — COALESCE(SUM(...)) is Long in JPQL aggregation
    @Query("SELECT COALESCE(SUM(v.points), 0) FROM VitalityEvent v WHERE v.supplierId = :id AND v.occurredAt >= :since")
    Long sumPointsSince(@Param("id") UUID id, @Param("since") Instant since);
}
