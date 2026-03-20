package com.tirupurconnect.consumer;

import com.tirupurconnect.config.AppProperties;
import com.tirupurconnect.exception.ResourceNotFoundException;
import com.tirupurconnect.model.Supplier;
import com.tirupurconnect.repository.SupplierRepository;
import com.tirupurconnect.service.WhatsAppService;
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
public class NotificationConsumer extends BaseStreamConsumer {

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyGuard    idempotencyGuard;
    private final AppProperties       appProperties;
    private final WhatsAppService     whatsAppService;
    private final SupplierRepository  supplierRepository;

    @Override protected StringRedisTemplate redisTemplate()   { return redisTemplate; }
    @Override protected IdempotencyGuard    idempotencyGuard() { return idempotencyGuard; }
    @Override protected AppProperties       appProperties()    { return appProperties; }
    @Override protected String streamKey()     { return appProperties.getRedis().getStreams().getInquiryCreated(); }
    @Override protected String consumerGroup() { return "notification-service"; }
    @Override protected String consumerName()  { return "notification-consumer-1"; }

    @Scheduled(fixedDelay = 500)
    public void consume() { poll(); }

    @Override
    protected void process(Map<String, String> fields) {
        boolean isReplay = Boolean.parseBoolean(fields.getOrDefault("replay_mode", "false"));
        if (isReplay) return;

        UUID supplierId = UUID.fromString(fields.get("supplier_id"));
        String queryText = fields.getOrDefault("query_text", "");
        int position = Integer.parseInt(fields.getOrDefault("position_shown", "0"));

        Supplier supplier = supplierRepository.findById(supplierId)
            .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + supplierId));

        whatsAppService.sendInquiryNotification(
            supplier.getOwnerPhone(),
            supplier.getBusinessName(),
            queryText,
            position
        );
        log.info("Inquiry notification sent: supplier={} phone={}", supplierId, supplier.getOwnerPhone());
    }
}
