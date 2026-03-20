package com.tirupurconnect.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.GeoLocation;
import co.elastic.clients.elasticsearch._types.LatLonGeoLocation;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tirupurconnect.config.AppProperties;
import com.tirupurconnect.dto.*;
import com.tirupurconnect.exception.SearchException;
import com.tirupurconnect.model.SearchLog;
import com.tirupurconnect.model.SearchResultItem;
import com.tirupurconnect.repository.SearchLogRepository;
import com.tirupurconnect.repository.SearchResultItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final ElasticsearchClient        esClient;
    private final SearchLogRepository        searchLogRepository;
    private final SearchResultItemRepository searchResultItemRepository;
    private final AppProperties              props;

    private static final GeometryFactory GF         = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double          DEFAULT_LAT = 11.1085;
    private static final double          DEFAULT_LON = 77.3411;
    private static final int             DEFAULT_RADIUS = 15;

    @Transactional
    public SearchResponse search(SearchRequest req, String tenantId, UUID buyerId) {
        // Apply defaults for optional fields (FIX #11: no longer in record constructor)
        double lat      = req.lat()      != null ? req.lat()      : DEFAULT_LAT;
        double lon      = req.lon()      != null ? req.lon()      : DEFAULT_LON;
        int    radius   = req.radiusKm() != null ? req.radiusKm() : DEFAULT_RADIUS;
        UUID   session  = req.sessionId()!= null ? req.sessionId(): UUID.randomUUID();
        short  zone     = req.zone()     != null ? req.zone()     : 1;

        // FIX #15: renamed local var from "log" to "searchLog" to avoid shadowing Slf4j log field
        SearchLog searchLog = persistSearchLog(req.queryText(), tenantId, buyerId, lat, lon, zone, session);

        List<Hit<ObjectNode>> hits;
        try {
            hits = executeEsQuery(req.queryText(), tenantId, lat, lon, radius);
        } catch (IOException e) {
            log.error("ES query failed: tenant={} query={} error={}", tenantId, req.queryText(), e.getMessage());
            throw new SearchException("Search temporarily unavailable", e);
        }

        searchLog.setResultCount(hits.size());
        searchLog.setZeroResult(hits.isEmpty());
        searchLogRepository.save(searchLog);

        List<SearchResultResponse> results = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            Hit<ObjectNode> hit = hits.get(i);
            ObjectNode src = hit.source();
            if (src == null) continue;

            UUID supplierId = UUID.fromString(src.path("supplier_id").asText());
            SearchResultItem sri = new SearchResultItem();
            sri.setSearchLogId(searchLog.getId());
            sri.setSupplierId(supplierId);
            sri.setQueryText(req.queryText());
            sri.setPositionShown((short) (i + 1));
            sri.setRelevanceScore(hit.score() != null ? BigDecimal.valueOf(hit.score()) : BigDecimal.ZERO);
            searchResultItemRepository.save(sri);

            results.add(new SearchResultResponse(
                supplierId,
                src.path("business_name").asText(""),
                src.path("type").asText(""),
                src.path("category_slug").asText(""),
                (short) src.path("trust_score").asInt(0),
                0.0,
                null,
                false
            ));
        }

        log.info("Search complete: tenant={} query='{}' results={}", tenantId, req.queryText(), results.size());
        return new com.tirupurconnect.dto.SearchResponse(req.queryText(), results.size(), results);
    }

    private List<Hit<ObjectNode>> executeEsQuery(String queryText, String tenantId,
                                                  double lat, double lon, int radiusKm)
            throws IOException {
        String index = props.getElasticsearch().getIndexName();

        Query multiMatch = MultiMatchQuery.of(m -> m
            .query(queryText)
            .fields("title_en^3", "description", "business_name^2")
            .type(TextQueryType.BestFields)
        )._toQuery();

        GeoLocation origin = GeoLocation.of(g -> g
            .latlon(LatLonGeoLocation.of(ll -> ll.lat(lat).lon(lon))));

        Query geoFilter     = GeoDistanceQuery.of(gd -> gd
            .field("location").location(origin).distance(radiusKm + "km"))._toQuery();
        Query statusFilter  = TermQuery.of(t -> t.field("status").value("active"))._toQuery();
        Query profileFilter = TermQuery.of(t -> t.field("profile_complete").value(true))._toQuery();
        Query tenantFilter  = TermQuery.of(t -> t.field("tenant_id").value(tenantId))._toQuery();

        Query boolQuery = BoolQuery.of(b -> b
            .must(multiMatch)
            .filter(geoFilter, statusFilter, profileFilter, tenantFilter)
        )._toQuery();

        co.elastic.clients.elasticsearch.core.SearchResponse<ObjectNode> response =
            esClient.search(s -> s.index(index).query(boolQuery).size(20), ObjectNode.class);

        return response.hits().hits();
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
