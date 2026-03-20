package com.tirupurconnect.repository;

import com.tirupurconnect.model.Supplier;
import com.tirupurconnect.model.Supplier.SupplierStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByUserId(UUID userId);

    @Query("SELECT s FROM Supplier s WHERE s.tenant.slug = :slug AND s.status NOT IN ('GHOST','CLOSED') AND s.lastActiveAt < :before")
    List<Supplier> findSuppliersNotActiveSince(@Param("slug") String slug, @Param("before") Instant before);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Supplier s
        SET s.vitalityScore = :score,
            s.status = :status,
            s.updatedAt = :updatedAt
        WHERE s.id = :id
    """)
    void updateVitalityScoreAndStatus(
            UUID id,
            short score,
            SupplierStatus status,
            Instant updatedAt
    );
}