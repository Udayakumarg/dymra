package com.tirupurconnect.repository;

import com.tirupurconnect.model.Supplier;
import com.tirupurconnect.model.Supplier.SupplierStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    @Query("SELECT s FROM Supplier s WHERE s.user.id = :userId")
    Optional<Supplier> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT s FROM Supplier s WHERE s.tenant.slug = :slug AND s.status NOT IN :excludedStatuses AND s.lastActiveAt < :before")
    List<Supplier> findSuppliersNotActiveSince(@Param("slug") String slug,
                                                @Param("excludedStatuses") List<SupplierStatus> excludedStatuses,
                                                @Param("before") Instant before);

    // FIX: NOW() → CURRENT_TIMESTAMP (valid JPQL)
    @Modifying
    @Query("UPDATE Supplier s SET s.vitalityScore = :score, s.status = :status, s.updatedAt = CURRENT_TIMESTAMP WHERE s.id = :id")
    void updateVitalityScoreAndStatus(@Param("id") UUID id,
                                       @Param("score") short score,
                                       @Param("status") SupplierStatus status);
}
