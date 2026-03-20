package com.tirupurconnect.repository;
import com.tirupurconnect.model.Listing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ListingRepository extends JpaRepository<Listing, UUID> {
    List<Listing> findBySupplierId(UUID supplierId);
    List<Listing> findBySupplierIdAndActive(UUID supplierId, boolean active);

    @Modifying
    @Query("UPDATE Listing l SET l.active = :active, l.updatedAt = NOW() WHERE l.supplier.id = :id")
    void setActiveForSupplier(@Param("id") UUID id, @Param("active") boolean active);
}
