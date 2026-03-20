package com.tirupurconnect.repository;

import com.tirupurconnect.model.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface InquiryRepository extends JpaRepository<Inquiry, UUID> {

    // FIX: buyer/supplier are scalar UUID fields (not associations) so this derived query is fine
    boolean existsByBuyerIdAndSupplierIdAndCreatedAtAfter(UUID buyerId, UUID supplierId, Instant after);

    // FIX: enum comparison via com.tirupurconnect.model.Inquiry$InquiryStatus literal
    @Query("SELECT COUNT(i) FROM Inquiry i WHERE i.supplierId = :id " +
           "AND i.status = com.tirupurconnect.model.Inquiry.InquiryStatus.RESPONDED " +
           "AND i.createdAt >= :since")
    long countRespondedInquiries(@Param("id") UUID id, @Param("since") Instant since);

    @Query("SELECT COUNT(i) FROM Inquiry i WHERE i.supplierId = :id AND i.createdAt >= :since")
    long countTotalInquiries(@Param("id") UUID id, @Param("since") Instant since);
}
