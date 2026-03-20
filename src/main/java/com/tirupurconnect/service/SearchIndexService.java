package com.tirupurconnect.service;

import com.tirupurconnect.model.Listing;
import com.tirupurconnect.model.Supplier;
import com.tirupurconnect.repository.ListingRepository;
import com.tirupurconnect.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * No-op implementation when Elasticsearch is disabled.
 * Activate by setting app.elasticsearch.enabled=true and providing ES credentials.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class SearchIndexService {

    private final SupplierRepository supplierRepository;
    private final ListingRepository  listingRepository;

    public void reindexSupplier(UUID supplierId) {
        log.info("ES reindex: supplier={}", supplierId);
    }

    public void updateVitalityScore(UUID supplierId, short newScore) {
        log.info("ES vitality update: supplier={} score={}", supplierId, newScore);
    }
}
