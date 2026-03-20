package com.tirupurconnect.service;

import com.tirupurconnect.dto.InquiryCreateRequest;
import com.tirupurconnect.dto.InquiryResponse;
import com.tirupurconnect.event.InquiryCreatedEvent;
import com.tirupurconnect.event.OutboxEventPublisher;
import com.tirupurconnect.exception.DuplicateInquiryException;
import com.tirupurconnect.exception.ResourceNotFoundException;
import com.tirupurconnect.model.Inquiry;
import com.tirupurconnect.model.SearchResultItem;
import com.tirupurconnect.repository.InquiryRepository;
import com.tirupurconnect.repository.SearchResultItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InquiryService {

    private final InquiryRepository          inquiryRepository;
    private final SearchResultItemRepository searchResultItemRepository;
    private final OutboxEventPublisher       eventPublisher;

    @Transactional
    public InquiryResponse createInquiry(InquiryCreateRequest req, UUID buyerId, String tenantId) {
        // Fraud guard: no duplicate inquiry to same supplier within 10 minutes
        boolean duplicate = inquiryRepository.existsByBuyerIdAndSupplierIdAndCreatedAtAfter(
            buyerId, req.supplierId(), Instant.now().minusSeconds(600)
        );
        if (duplicate) {
            throw new DuplicateInquiryException("Duplicate inquiry submitted within 10 minutes");
        }

        // Resolve search context for context-aware ratings
        int positionShown = 0;
        String queryText  = "";
        if (req.searchResultItemId() != null) {
            SearchResultItem sri = searchResultItemRepository.findById(req.searchResultItemId())
                .orElseThrow(() -> new ResourceNotFoundException("SearchResultItem not found"));
            positionShown = sri.getPositionShown();
            queryText     = sri.getQueryText() != null ? sri.getQueryText() : "";
            sri.setContacted(true);
            searchResultItemRepository.save(sri);
        }

        // Persist inquiry (source of truth)
        Inquiry inquiry = new Inquiry();
        inquiry.setSupplierId(req.supplierId());
        inquiry.setBuyerId(buyerId);
        inquiry.setSearchResultItemId(req.searchResultItemId());
        inquiry.setMessage(req.message());
        inquiry = inquiryRepository.save(inquiry);

        // Write to outbox — atomic with the inquiry insert above
        InquiryCreatedEvent event = InquiryCreatedEvent.of(
            inquiry.getId(), req.supplierId(), buyerId,
            req.searchResultItemId(), positionShown, queryText, tenantId
        );
        eventPublisher.saveToOutbox(
            inquiry.getId().toString(), InquiryCreatedEvent.TYPE, tenantId, event
        );

        log.info("Inquiry created: id={} supplier={} buyer={} tenant={}",
            inquiry.getId(), req.supplierId(), buyerId, tenantId);

        return new InquiryResponse(inquiry.getId(), inquiry.getSupplierId(),
            inquiry.getStatus().name(), inquiry.getCreatedAt());
    }
}
