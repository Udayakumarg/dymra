package com.tirupurconnect.service;

import com.tirupurconnect.config.AppProperties;
import com.tirupurconnect.dto.SearchRequest;
import com.tirupurconnect.dto.SearchResponse;
import com.tirupurconnect.dto.SearchResultResponse;
import com.tirupurconnect.model.Listing;
import com.tirupurconnect.model.SearchLog;
import com.tirupurconnect.model.SearchResultItem;
import com.tirupurconnect.model.Supplier;
import com.tirupurconnect.repository.ListingRepository;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final SupplierRepository         supplierRepository;
    private final ListingRepository          listingRepository;
    private final SearchLogRepository        searchLogRepository;
    private final SearchResultItemRepository searchResultItemRepository;
    private final AppProperties              props;

    private static final GeometryFactory GF          = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double          TIRUPPUR_LAT = 11.1085;
    private static final double          TIRUPPUR_LON = 77.3411;

    // Words to strip from natural language queries
    private static final Set<String> STOP_WORDS = Set.of(
            "show", "me", "find", "search", "get", "list", "give", "need",
            "want", "looking", "for", "in", "at", "near", "around", "the",
            "a", "an", "and", "or", "with", "tirupur", "tiruppur", "tirupure",
            "suppliers", "supplier", "units", "unit", "dealers", "dealer",
            "manufacturers", "manufacturer", "services", "service", "vendors"
    );

    @Transactional
    public SearchResponse search(SearchRequest req, String tenantId, UUID buyerId) {
        double lat    = isValidCoord(req.lat(), req.lon()) ? req.lat() : TIRUPPUR_LAT;
        double lon    = isValidCoord(req.lat(), req.lon()) ? req.lon() : TIRUPPUR_LON;
        int    radius = req.radiusKm()  != null ? req.radiusKm()  : 15;
        UUID   session= req.sessionId() != null ? req.sessionId() : UUID.randomUUID();
        short  zone   = req.zone()      != null ? req.zone()      : 1;
        String raw    = req.queryText() != null ? req.queryText().toLowerCase().trim() : "";

        // Extract meaningful keywords from natural language query
        List<String> keywords = extractKeywords(raw);
        log.info("Search: raw='{}' keywords={} radius={}km lat={} lon={}", raw, keywords, radius, lat, lon);

        SearchLog sl = persistSearchLog(raw, tenantId, buyerId, lat, lon, zone, session);

        List<Listing> allListings = listingRepository.findAll().stream()
                .filter(Listing::isActive)
                .filter(l -> l.getSupplier() != null)
                .filter(l -> l.getSupplier().getStatus() == Supplier.SupplierStatus.ACTIVE)
                .filter(l -> l.getSupplier().getProfileCompletePct() >= 60)
                .filter(l -> l.getSupplier().getTenant().getSlug().equals(tenantId))
                .collect(Collectors.toList());

        // Group by supplier — pick best matching listing per supplier
        Map<UUID, Supplier>     supplierMap   = new LinkedHashMap<>();
        Map<UUID, String>       matchedType   = new LinkedHashMap<>();
        Map<UUID, Double>       matchScore    = new LinkedHashMap<>();

        for (Listing listing : allListings) {
            Supplier s = listing.getSupplier();

            // Distance filter — use a generous multiplier to account for GPS inaccuracy
            // If user lat/lon is same as default, skip distance filter entirely
            boolean isDefaultLocation = Math.abs(lat - TIRUPPUR_LAT) < 0.001 && Math.abs(lon - TIRUPPUR_LON) < 0.001;
            if (!isDefaultLocation) {
                double dist = distanceKm(s, lat, lon);
                if (dist > radius * 1.5) continue; // 50% buffer for GPS inaccuracy
            }

            double score = matchScore(s, listing, keywords, raw);
            if (score > 0) {
                UUID sid = s.getId();
                if (!supplierMap.containsKey(sid) || score > matchScore.getOrDefault(sid, 0.0)) {
                    supplierMap.put(sid, s);
                    matchedType.put(sid, listing.getType().name());
                    matchScore.put(sid, score);
                }
            }
        }

        // Sort: score DESC, then trust score DESC, then distance ASC
        List<Supplier> ranked = supplierMap.values().stream()
                .sorted((a, b) -> {
                    double sa = matchScore.getOrDefault(a.getId(), 0.0);
                    double sb = matchScore.getOrDefault(b.getId(), 0.0);
                    if (Math.abs(sa - sb) > 0.1) return Double.compare(sb, sa);
                    int trustCmp = Short.compare(b.getTrustScore(), a.getTrustScore());
                    if (trustCmp != 0) return trustCmp;
                    return Double.compare(distanceKm(a, lat, lon), distanceKm(b, lat, lon));
                })
                .limit(20)
                .collect(Collectors.toList());

        sl.setResultCount(ranked.size());
        sl.setZeroResult(ranked.isEmpty());
        searchLogRepository.save(sl);

        List<SearchResultResponse> results = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Supplier s = ranked.get(i);

            SearchResultItem sri = new SearchResultItem();
            sri.setSearchLogId(sl.getId());
            sri.setSupplierId(s.getId());
            sri.setQueryText(raw);
            sri.setPositionShown((short) (i + 1));
            sri.setRelevanceScore(BigDecimal.valueOf(matchScore.getOrDefault(s.getId(), 0.0)));
            searchResultItemRepository.save(sri);

            double dist = Math.round(distanceKm(s, lat, lon) * 10.0) / 10.0;
            results.add(new SearchResultResponse(
                    s.getId(),
                    s.getBusinessName(),
                    matchedType.getOrDefault(s.getId(), "PRODUCT"),
                    "",
                    s.getTrustScore(),
                    dist,
                    null,
                    false
            ));
        }

        log.info("Search done: query='{}' keywords={} results={}", raw, keywords, results.size());
        return new SearchResponse(req.queryText(), results.size(), results);
    }

    /**
     * Extract meaningful keywords from natural language query.
     * "show me knitting in tirupur" → ["knitting"]
     * "grey yarn 30s" → ["grey", "yarn", "30s"]
     */
    private List<String> extractKeywords(String query) {
        if (query.isBlank()) return List.of();
        return Arrays.stream(query.split("\\s+"))
                .map(String::toLowerCase)
                .filter(w -> w.length() > 1)
                .filter(w -> !STOP_WORDS.contains(w))
                .collect(Collectors.toList());
    }

    /**
     * Score how well a listing matches the keywords.
     * Returns 0 if no match, higher = better match.
     */
    private double matchScore(Supplier s, Listing l, List<String> keywords, String rawQuery) {
        if (keywords.isEmpty()) return 1.0; // empty query = show all

        String name  = s.getBusinessName() != null ? s.getBusinessName().toLowerCase() : "";
        String title = l.getTitleEn()       != null ? l.getTitleEn().toLowerCase()       : "";
        String desc  = l.getDescription()   != null ? l.getDescription().toLowerCase()   : "";
        String full  = name + " " + title + " " + desc;

        double score = 0;
        for (String kw : keywords) {
            if (name.contains(kw))  score += 3.0; // business name match = highest weight
            if (title.contains(kw)) score += 2.0; // title match
            if (desc.contains(kw))  score += 1.0; // description match
        }

        // Bonus: full raw query substring match in title
        if (title.contains(rawQuery)) score += 2.0;

        return score;
    }

    private boolean isValidCoord(Double lat, Double lon) {
        if (lat == null || lon == null) return false;
        // Check it's roughly in Tamil Nadu / South India
        return lat > 8.0 && lat < 14.0 && lon > 76.0 && lon < 80.0;
    }

    private boolean withinRadius(Supplier s, double lat, double lon, int radiusKm) {
        if (s.getLocation() == null) return true;
        return distanceKm(s, lat, lon) <= radiusKm;
    }

    private double distanceKm(Supplier s, double lat, double lon) {
        if (s.getLocation() == null) return 0.0;
        return haversineKm(lat, lon, s.getLocation().getY(), s.getLocation().getX());
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
