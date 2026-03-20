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

    @Override protected StringRedisTemplate redisTemplate()   { return redisTemplate; }
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
        vitalityService.recordSignal(supplierId, VitalitySignal.INQUIRY_CREATED,
            appProperties.getVitality().getScores().getAppLogin());
        log.info("Vitality signal recorded: supplier={} signal=INQUIRY_CREATED", supplierId);
    }
}
