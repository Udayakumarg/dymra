package com.tirupurconnect.consumer;

import com.tirupurconnect.config.AppProperties;
import com.tirupurconnect.model.VitalityEvent.VitalitySignal;
import com.tirupurconnect.service.VitalityService;
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
public class VitalitySignalConsumer extends BaseStreamConsumer {

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyGuard    idempotencyGuard;
    private final AppProperties       appProperties;
    private final VitalityService     vitalityService;

    @Override protected StringRedisTemplate redisTemplate()    { return redisTemplate; }
    @Override protected IdempotencyGuard    idempotencyGuard() { return idempotencyGuard; }
    @Override protected AppProperties       appProperties()    { return appProperties; }
    @Override protected String streamKey()     { return appProperties.getRedis().getStreams().getInquiryCreated(); }
    @Override protected String consumerGroup() { return "vitality-service"; }
    @Override protected String consumerName()  { return "vitality-consumer-1"; }

    @Scheduled(fixedDelay = 500)
    public void consume() { poll(); }

    @Override
    protected void process(Map<String, String> fields) {
        boolean isReplay = Boolean.parseBoolean(fields.getOrDefault("replay_mode", "false"));
        if (isReplay) return;

        UUID supplierId = UUID.fromString(fields.get("supplier_id"));

        // FIX #21: INQUIRY_CREATED should use the inquiry-specific score, not app-login score.
        // The vitality config doesn't have an explicit inquiry_created score — 
        // use a hardcoded 15 pts (between app-login 5 and catalogue-updated 20) as designed.
        // This is a real-time partial signal; full score recompute happens in weekly batch.
        short points = 15;
        vitalityService.recordSignal(supplierId, VitalitySignal.INQUIRY_CREATED, points);
        log.info("Vitality signal INQUIRY_CREATED recorded: supplier={} points={}", supplierId, points);
    }
}
