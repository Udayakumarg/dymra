package com.tirupurconnect.consumer;

import com.tirupurconnect.config.AppProperties;
import com.tirupurconnect.service.SearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** Syncs vitality score changes to Elasticsearch trust_score field. */
@Component
@RequiredArgsConstructor
@Slf4j
public class VitalityScoreSearchSyncConsumer extends BaseStreamConsumer {

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyGuard    idempotencyGuard;
    private final AppProperties       appProperties;
    private final SearchIndexService  searchIndexService;

    @Override protected StringRedisTemplate redisTemplate()   { return redisTemplate; }
    @Override protected IdempotencyGuard    idempotencyGuard() { return idempotencyGuard; }
    @Override protected AppProperties       appProperties()    { return appProperties; }
    @Override protected String streamKey()     { return appProperties.getRedis().getStreams().getVitalityUpdated(); }
    @Override protected String consumerGroup() { return "search-service"; }
    @Override protected String consumerName()  { return "vitality-search-sync-consumer-1"; }

    @Scheduled(fixedDelay = 500)
    public void consume() { poll(); }

    @Override
    protected void process(Map<String, String> fields) {
        UUID supplierId = UUID.fromString(fields.get("supplier_id"));
        short newScore  = Short.parseShort(fields.getOrDefault("new_score", "0"));
        searchIndexService.updateVitalityScore(supplierId, newScore);
        log.info("ES vitality score synced: supplier={} score={}", supplierId, newScore);
    }
}
