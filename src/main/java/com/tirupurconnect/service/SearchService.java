package com.tirupurconnect.service;

import com.tirupurconnect.config.AppProperties;
import com.tirupurconnect.dto.SearchRequest;
import com.tirupurconnect.dto.SearchResponse;
import com.tirupurconnect.dto.SearchResultResponse;
import com.tirupurconnect.model.SearchLog;
import com.tirupurconnect.model.SearchResultItem;
import com.tirupurconnect.model.Supplier;
import com.tirupurconnect.repository.SearchLogRepository;
import com.tirupurconnect.repository.SearchResultItemRepository;
import com.tirupurconnect.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final SupplierRepository         supplierRepository;
    private final SearchLogRepository        searchLogRepository;
    private final SearchResultItemRepository searchResultItemRepository;
    private final AppProperties              props;

    private static final GeometryFactory GF             = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double          DEFAULT_LAT    = 11.1085;
    private static final double          DEFAULT_LON    = 77.3411;
    private static final int             DEFAULT_RADIUS = 15;

    @Transactional
    public SearchResponse search(SearchRequest req, String tenantId, UUID buyerId) {
        double lat    = req.lat()       != null ? req.lat()       : DEFAULT_LAT;
        double lon    = req.lon()       != null ? req.lon()       : DEFAULT_LON;
        int    radius = req.radiusKm()  != null ? req.radiusKm()  : DEFAULT_RADIUS;
        UUID   session= req.sessionId() != null ? req.sessionId() : UUID.randomUUID();
        short  zone   = req.zone()      != null ? req.zone()      : 1;
        String query  = req.queryText() != null ? req.queryText().toLowerCase().trim() : "";

        // Persist search log
        SearchLog sl = persistSearchLog(query, tenantId, buyerId, lat, lon, zone, session);

        // DB-based search — works without Elasticsearch
        List<Supplier> allSuppliers = supplierRepository.findAll();

        List<Supplier> matched = allSuppliers.stream()
            .filter(s -> s.getStatus() == Supplier.SupplierStatus.ACTIVE)
            .filter(s -> s.getProfileCompletePct() >= 70)
            .filter(s -> s.getTenant().getSlug().equals(tenantId))
            .filter(s -> matchesQuery(s, query))
            .filter(s -> withinRadius(s, lat, lon, radius))
            .sorted((a, b) -> {
                // Sort by trust score descending, then distance
                int trustCmp = Short.compare(b.getTrustScore(), a.getTrustScore());
                if (trustCmp != 0) return trustCmp;
                return Double.compare(distanceKm(a, lat, lon), distanceKm(b, lat, lon));
            })
            .limit(20)
            .collect(Collectors.toList());

        // Update log
        sl.setResultCount(matched.size());
        sl.setZeroResult(matched.isEmpty());
        searchLogRepository.save(sl);

        // Build result items
        List<SearchResultResponse> results = new ArrayList<>();
        for (int i = 0; i < matched.size(); i++) {
            Supplier s = matched.get(i);

            SearchResultItem sri = new SearchResultItem();
            sri.setSearchLogId(sl.getId());
            sri.setSupplierId(s.getId());
            sri.setQueryText(query);
            sri.setPositionShown((short) (i + 1));
            sri.setRelevanceScore(BigDecimal.valueOf(s.getTrustScore()));
            searchResultItemRepository.save(sri);

            results.add(new SearchResultResponse(
                s.getId(),
                s.getBusinessName(),
                "PRODUCT",
                "",
                s.getTrustScore(),
                Math.round(distanceKm(s, lat, lon) * 10.0) / 10.0,
                null,
                false
            ));
        }

        log.info("Search complete: tenant={} query='{}' results={}", tenantId, query, results.size());
        return new SearchResponse(req.queryText(), results.size(), results);
    }

    private boolean matchesQuery(Supplier s, String query) {
        if (query.isBlank()) return true;
        String name = s.getBusinessName() != null ? s.getBusinessName().toLowerCase() : "";
        return name.contains(query);
    }

    private boolean withinRadius(Supplier s, double lat, double lon, int radiusKm) {
        if (s.getLocation() == null) return true; // include if no location set
        double dist = distanceKm(s, lat, lon);
        return dist <= radiusKm;
    }

    private double distanceKm(Supplier s, double lat, double lon) {
        if (s.getLocation() == null) return 0.0;
        double sLat = s.getLocation().getY();
        double sLon = s.getLocation().getX();
        return haversineKm(lat, lon, sLat, sLon);
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private SearchLog persistSearchLog(String queryText, String tenantId, UUID buyerId,
                                        double lat, double lon, short zone, UUID sessionId) {
        SearchLog sl = new SearchLog();
        sl.setSessionId(sessionId);
        sl.setTenantId(tenantId);
        sl.setBuyerId(buyerId);
        sl.setQueryText(queryText);
        sl.setZone(zone);
        sl.setResultCount(0);
        sl.setZeroResult(true);
        var point = GF.createPoint(new Coordinate(lon, lat));
        point.setSRID(4326);
        sl.setLocation(point);
        return searchLogRepository.save(sl);
    }
}
