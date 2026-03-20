package com.tirupurconnect.service;

import com.tirupurconnect.dto.ListingCreateRequest;
import com.tirupurconnect.dto.ListingResponse;
import com.tirupurconnect.event.OutboxEventPublisher;
import com.tirupurconnect.event.SupplierProfileUpdatedEvent;
import com.tirupurconnect.exception.ForbiddenException;
import com.tirupurconnect.exception.ResourceNotFoundException;
import com.tirupurconnect.model.Listing;
import com.tirupurconnect.model.Supplier;
import com.tirupurconnect.repository.ListingRepository;
import com.tirupurconnect.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ListingService {

    private final ListingRepository   listingRepository;
    private final SupplierRepository  supplierRepository;
    private final OutboxEventPublisher eventPublisher;

    @Transactional
    public ListingResponse create(ListingCreateRequest req, UUID userId, String tenantSlug) {
        Supplier supplier = getSupplierForUser(userId);

        Listing listing = new Listing();
        listing.setSupplier(supplier);
        listing.setType(req.type());
        listing.setTitleEn(req.titleEn());
        listing.setTitleTa(req.titleTa());
        listing.setDescription(req.description());
        listing.setCategoryId(req.categoryId());
        listing.setActive(true);

        listing = listingRepository.save(listing);

        // Profile updated → trigger ES re-sync
        SupplierProfileUpdatedEvent event = SupplierProfileUpdatedEvent.of(
            supplier.getId(), tenantSlug, "CATALOGUE_UPDATED", supplier.getProfileCompletePct()
        );
        eventPublisher.saveToOutbox(supplier.getId().toString(),
            SupplierProfileUpdatedEvent.TYPE, tenantSlug, event);

        log.info("Listing created: id={} supplier={}", listing.getId(), supplier.getId());
        return ListingResponse.from(listing);
    }

    public List<ListingResponse> getBySupplier(UUID supplierId) {
        return listingRepository.findBySupplierId(supplierId)
            .stream().map(ListingResponse::from).toList();
    }

    @Transactional
    public ListingResponse update(UUID listingId, ListingCreateRequest req,
                                   UUID userId, String tenantSlug) {
        Listing listing = listingRepository.findById(listingId)
            .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        Supplier supplier = getSupplierForUser(userId);
        if (!listing.getSupplier().getId().equals(supplier.getId())) {
            throw new ForbiddenException("Cannot update another supplier's listing");
        }

        listing.setTitleEn(req.titleEn());
        if (req.titleTa()     != null) listing.setTitleTa(req.titleTa());
        if (req.description() != null) listing.setDescription(req.description());
        if (req.categoryId()  != null) listing.setCategoryId(req.categoryId());

        listing = listingRepository.save(listing);

        SupplierProfileUpdatedEvent event = SupplierProfileUpdatedEvent.of(
            supplier.getId(), tenantSlug, "CATALOGUE_UPDATED", supplier.getProfileCompletePct()
        );
        eventPublisher.saveToOutbox(supplier.getId().toString(),
            SupplierProfileUpdatedEvent.TYPE, tenantSlug, event);

        return ListingResponse.from(listing);
    }

    @Transactional
    public void delete(UUID listingId, UUID userId) {
        Listing listing = listingRepository.findById(listingId)
            .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        Supplier supplier = getSupplierForUser(userId);
        if (!listing.getSupplier().getId().equals(supplier.getId())) {
            throw new ForbiddenException("Cannot delete another supplier's listing");
        }
        listing.setActive(false);
        listingRepository.save(listing);
    }

    private Supplier getSupplierForUser(UUID userId) {
        return supplierRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Supplier profile not found for user"));
    }
}
