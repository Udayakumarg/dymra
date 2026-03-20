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
import com.tirupurconnect.event.OutboxEventPublisher;
import com.tirupurconnect.event.SearchLogEvent;
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

    private final ElasticsearchClient       esClient;
    private final SearchLogRepository       searchLogRepository;
    private final SearchResultItemRepository searchResultItemRepository;
    private final OutboxEventPublisher      eventPublisher;
    private final AppProperties             props;

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    @Transactional
    public com.tirupurconnect.dto.SearchResponse search(SearchRequest req,
                                                         String tenantId,
                                                         UUID buyerId) {
        // 1. Persist search log (before ES query so we capture intent even on ES failure)
        SearchLog searchLog = persistSearchLog(req, tenantId, buyerId, 0);

        // 2. Build and execute ES query
        List<Hit<ObjectNode>> hits;
        try {
            hits = executeEsQuery(req, tenantId);
        } catch (IOException e) {
            log.error("ES query failed: tenant={} query={} error={}", tenantId, req.queryText(), e.getMessage());
            throw new SearchException("Search temporarily unavailable", e);
        }

        // 3. Update result count on log
        searchLog.setResultCount(hits.size());
        searchLog.setZeroResult(hits.isEmpty());
        searchLogRepository.save(searchLog);

        // 4. Persist search_result_items for context-aware ratings
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
                0.0,      // distance computed client-side from GPS; omitted from ES response for brevity
                null,     // thumbUrl — served from CDN, resolved by frontend
                false     // openNow — computed from business_hours in service layer if needed
            ));
        }

        log.info("Search complete: tenant={} query='{}' results={}", tenantId, req.queryText(), results.size());
        return new com.tirupurconnect.dto.SearchResponse(req.queryText(), results.size(), results);
    }

    private List<Hit<ObjectNode>> executeEsQuery(SearchRequest req, String tenantId) throws IOException {
        String index = props.getElasticsearch().getIndexName();

        // BM25 multi-match across text fields
        Query multiMatch = MultiMatchQuery.of(m -> m
            .query(req.queryText())
            .fields("title_en^3", "title_ta^3", "description", "business_name^2")
            .type(TextQueryType.BestFields)
        )._toQuery();

        // Geo-distance filter
        GeoLocation origin = GeoLocation.of(g -> g.latlon(
            LatLonGeoLocation.of(ll -> ll.lat(req.lat()).lon(req.lon()))
        ));
        Query geoFilter = GeoDistanceQuery.of(gd -> gd
            .field("location")
            .location(origin)
            .distance(req.radiusKm() + "km")
        )._toQuery();

        // Status and profile completeness filters
        Query statusFilter   = TermQuery.of(t -> t.field("status").value("active"))._toQuery();
        Query profileFilter  = TermQuery.of(t -> t.field("profile_complete").value(true))._toQuery();
        Query tenantFilter   = TermQuery.of(t -> t.field("tenant_id").value(tenantId))._toQuery();

        Query boolQuery = BoolQuery.of(b -> b
            .must(multiMatch)
            .filter(geoFilter, statusFilter, profileFilter, tenantFilter)
        )._toQuery();

        SearchResponse<ObjectNode> response = esClient.search(s -> s
            .index(index)
            .query(boolQuery)
            .size(20),
            ObjectNode.class
        );

        return response.hits().hits();
    }

    private SearchLog persistSearchLog(SearchRequest req, String tenantId, UUID buyerId, int resultCount) {
        SearchLog log = new SearchLog();
        log.setSessionId(req.sessionId());
        log.setTenantId(tenantId);
        log.setBuyerId(buyerId);
        log.setQueryText(req.queryText());
        log.setZone(req.zone());
        log.setResultCount(resultCount);
        log.setZeroResult(resultCount == 0);
        if (req.lat() != null && req.lon() != null) {
            var point = GF.createPoint(new Coordinate(req.lon(), req.lat()));
            point.setSRID(4326);
            log.setLocation(point);
        }
        return searchLogRepository.save(log);
    }
}
