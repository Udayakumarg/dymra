package com.tirupurconnect.consumer;

import com.tirupurconnect.config.AppProperties;
import com.tirupurconnect.model.Supplier;
import com.tirupurconnect.repository.SupplierRepository;
import com.tirupurconnect.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Sends WhatsApp nudges when supplier drops to DORMANT or FADING. */
@Component
@RequiredArgsConstructor
@Slf4j
public class VitalityNudgeConsumer extends BaseStreamConsumer {

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyGuard    idempotencyGuard;
    private final AppProperties       appProperties;
    private final WhatsAppService     whatsAppService;
    private final SupplierRepository  supplierRepository;

    @Override protected StringRedisTemplate redisTemplate()   { return redisTemplate; }
    @Override protected IdempotencyGuard    idempotencyGuard() { return idempotencyGuard; }
    @Override protected AppProperties       appProperties()    { return appProperties; }
    @Override protected String streamKey()     { return appProperties.getRedis().getStreams().getVitalityUpdated(); }
    @Override protected String consumerGroup() { return "notification-service"; }
    @Override protected String consumerName()  { return "vitality-nudge-consumer-1"; }

    @Scheduled(fixedDelay = 500)
    public void consume() { poll(); }

    @Override
    protected void process(Map<String, String> fields) {
        boolean isReplay = Boolean.parseBoolean(fields.getOrDefault("replay_mode", "false"));
        if (isReplay) return;

        String newStatus = fields.getOrDefault("new_status", "");
        if (!"DORMANT".equals(newStatus) && !"FADING".equals(newStatus)) return;

        UUID supplierId = UUID.fromString(fields.get("supplier_id"));
        Optional<Supplier> supplierOpt = supplierRepository.findById(supplierId);
        if (supplierOpt.isEmpty()) return;

        Supplier supplier = supplierOpt.get();
        whatsAppService.sendVitalityNudge(
            supplier.getOwnerPhone(),
            supplier.getBusinessName(),
            newStatus
        );
        log.info("Vitality nudge sent: supplier={} status={}", supplierId, newStatus);
    }
}
