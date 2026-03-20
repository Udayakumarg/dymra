package com.tirupurconnect.repository;

import com.tirupurconnect.model.Inquiry;
import com.tirupurconnect.model.Inquiry.InquiryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface InquiryRepository extends JpaRepository<Inquiry, UUID> {

    boolean existsByBuyerIdAndSupplierIdAndCreatedAtAfter(UUID buyerId, UUID supplierId, Instant after);

    // FIX: use :status param bound to enum constant — fully-qualified path doesn't work in Hibernate 6
    @Query("SELECT COUNT(i) FROM Inquiry i WHERE i.supplierId = :id AND i.status = :status AND i.createdAt >= :since")
    long countRespondedInquiries(@Param("id") UUID id,
                                  @Param("status") InquiryStatus status,
                                  @Param("since") Instant since);

    @Query("SELECT COUNT(i) FROM Inquiry i WHERE i.supplierId = :id AND i.createdAt >= :since")
    long countTotalInquiries(@Param("id") UUID id, @Param("since") Instant since);
}
