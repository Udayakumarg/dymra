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

@Component
@RequiredArgsConstructor
@Slf4j
public class SearchSyncConsumer extends BaseStreamConsumer {

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyGuard    idempotencyGuard;
    private final AppProperties       appProperties;
    private final SearchIndexService  searchIndexService;

    @Override protected StringRedisTemplate redisTemplate()   { return redisTemplate; }
    @Override protected IdempotencyGuard    idempotencyGuard() { return idempotencyGuard; }
    @Override protected AppProperties       appProperties()    { return appProperties; }
    @Override protected String streamKey()     { return appProperties.getRedis().getStreams().getSupplierUpdated(); }
    @Override protected String consumerGroup() { return "search-service"; }
    @Override protected String consumerName()  { return "search-sync-consumer-1"; }

    @Scheduled(fixedDelay = 500)
    public void consume() { poll(); }

    @Override
    protected void process(Map<String, String> fields) {
        UUID supplierId = UUID.fromString(fields.get("supplier_id"));
        searchIndexService.reindexSupplier(supplierId);
        log.info("ES re-indexed: supplier={}", supplierId);
    }
}
