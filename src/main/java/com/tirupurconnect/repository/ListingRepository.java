package com.tirupurconnect.repository;

import com.tirupurconnect.model.Listing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ListingRepository extends JpaRepository<Listing, UUID> {

    @Modifying
    @Transactional
    @Query("""
    UPDATE Listing l
    SET l.active = :active,
        l.updatedAt = :updatedAt
    WHERE l.supplier.id = :id
""")
    void setActiveForSupplier(
            UUID id,
            boolean active,
            Instant updatedAt
    );

}
