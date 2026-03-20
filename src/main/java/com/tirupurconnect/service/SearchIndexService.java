package com.tirupurconnect.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import com.tirupurconnect.config.AppProperties;
import com.tirupurconnect.exception.ResourceNotFoundException;
import com.tirupurconnect.model.Listing;
import com.tirupurconnect.model.Supplier;
import com.tirupurconnect.repository.ListingRepository;
import com.tirupurconnect.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * Maintains the Elasticsearch 'listings' read model.
 * PostgreSQL is always source of truth.
 * This service is called by SearchSyncConsumer and VitalityScoreSearchSyncConsumer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchIndexService {

    private final ElasticsearchClient esClient;
    private final SupplierRepository  supplierRepository;
    private final ListingRepository   listingRepository;
    private final AppProperties       props;

    /** Full re-index of a supplier and all their active listings. */
    public void reindexSupplier(UUID supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
            .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + supplierId));

        // Only index suppliers with complete profiles
        if (supplier.getProfileCompletePct() < 70) {
            log.debug("Skipping incomplete profile: supplier={} pct={}", supplierId, supplier.getProfileCompletePct());
            return;
        }

        List<Listing> listings = listingRepository.findBySupplierIdAndActive(supplierId, true);

        if (listings.isEmpty()) {
            // Index supplier with a synthetic "supplier profile" document
            indexDocument(supplierId.toString(), buildSupplierDoc(supplier, null));
        } else {
            for (Listing listing : listings) {
                String docId = listing.getId().toString();
                indexDocument(docId, buildListingDoc(supplier, listing));
            }
        }
    }

    /** Partial update — only vitality/trust score fields. Avoids full re-index. */
    public void updateVitalityScore(UUID supplierId, short newScore) {
        // Update all documents for this supplier
        List<Listing> listings = listingRepository.findBySupplierIdAndActive(supplierId, true);
        List<String> docIds = listings.isEmpty()
            ? List.of(supplierId.toString())
            : listings.stream().map(l -> l.getId().toString()).toList();

        Map<String, Object> partialDoc = Map.of(
            "vitality_score", newScore,
            "updated_at", Instant.now().toString()
        );

        String index = props.getElasticsearch().getIndexName();
        for (String docId : docIds) {
            try {
                esClient.update(UpdateRequest.of(u -> u
                    .index(index)
                    .id(docId)
                    .doc(partialDoc)
                ), Map.class);
            } catch (IOException e) {
                log.warn("Partial update failed: docId={} error={}", docId, e.getMessage());
            }
        }
    }

    private void indexDocument(String docId, Map<String, Object> doc) {
        String index = props.getElasticsearch().getIndexName();
        try {
            esClient.index(IndexRequest.of(i -> i
                .index(index)
                .id(docId)
                .document(doc)
            ));
            log.debug("Indexed document: index={} id={}", index, docId);
        } catch (IOException e) {
            log.error("ES index failed: docId={} error={}", docId, e.getMessage());
            throw new RuntimeException("ES indexing failed for docId=" + docId, e);
        }
    }

    private Map<String, Object> buildSupplierDoc(Supplier s, Listing l) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("supplier_id",        s.getId().toString());
        doc.put("tenant_id",           s.getTenant().getSlug());
        doc.put("type",                l != null ? l.getType().name().toLowerCase() : "supplier");
        doc.put("business_name",       s.getBusinessName());
        doc.put("status",              s.getStatus().name().toLowerCase());
        doc.put("zone_visibility",     s.getZoneVisibility());
        doc.put("profile_complete",    s.getProfileCompletePct() >= 70);
        doc.put("trust_score",         s.getTrustScore());
        doc.put("vitality_score",      s.getVitalityScore());
        doc.put("updated_at",          s.getUpdatedAt().toString());
        if (s.getLocation() != null) {
            doc.put("location", Map.of(
                "lat", s.getLocation().getY(),
                "lon", s.getLocation().getX()
            ));
        }
        return doc;
    }

    private Map<String, Object> buildListingDoc(Supplier s, Listing l) {
        Map<String, Object> doc = buildSupplierDoc(s, l);
        doc.put("listing_id",   l.getId().toString());
        doc.put("title_en",     l.getTitleEn());
        doc.put("title_ta",     l.getTitleTa());
        doc.put("description",  l.getDescription());
        doc.put("category_id",  l.getCategoryId());
        doc.put("updated_at",   l.getUpdatedAt().toString());
        return doc;
    }
}
