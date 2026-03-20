package com.tirupurconnect.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tirupurconnect.config.AppProperties;
import com.tirupurconnect.model.OutboxEvent;
import com.tirupurconnect.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Transactional Outbox Pattern implementation.
 *
 * Phase 1 (in-band, same transaction as caller):
 *   Write event to outbox_events table with status=PENDING.
 *   This is atomic with the business write — guaranteed by shared transaction.
 *
 * Phase 2 (out-of-band, OutboxScheduler):
 *   Scheduler reads PENDING rows, publishes to Redis Stream, marks PROCESSED.
 *   If Redis is down, rows stay PENDING and are retried.
 *
 * Direct publish (best-effort, bypasses outbox):
 *   Used for non-critical events where at-most-once is acceptable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxRepository;
    private final StringRedisTemplate   redisTemplate;
    private final ObjectMapper          objectMapper;
    private final AppProperties         props;

    /**
     * Write to outbox table within the caller's transaction.
     * Call this for all critical events (inquiry.created, supplier.profile.updated, vitality.score.updated).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveToOutbox(String aggregateId, String eventType, String tenantId, Object event) {
        Map<String, Object> payload = objectMapper.convertValue(event, new TypeReference<>() {});
        OutboxEvent outboxEvent = new OutboxEvent(aggregateId, eventType, tenantId, payload);
        outboxRepository.save(outboxEvent);
        log.debug("Outbox event saved: type={} aggregateId={}", eventType, aggregateId);
    }

    /**
     * Publish a single outbox event to Redis Stream.
     * Called by OutboxScheduler — runs outside a transaction.
     */
    public boolean publishToStream(OutboxEvent outboxEvent) {
        String streamKey = resolveStreamKey(outboxEvent.getEventType());
        if (streamKey == null) {
            log.warn("No stream key for event type: {}", outboxEvent.getEventType());
            return false;
        }

        try {
            Map<String, String> fields = flattenPayload(outboxEvent);
            RecordId recordId = redisTemplate.opsForStream()
                .add(StreamRecords.newRecord().in(streamKey).ofMap(fields));
            log.debug("Published to stream: key={} recordId={} eventId={}",
                streamKey, recordId, outboxEvent.getEventId());
            return true;
        } catch (Exception e) {
            log.error("Failed to publish to Redis Stream: eventId={} error={}",
                outboxEvent.getEventId(), e.getMessage());
            return false;
        }
    }

    /**
     * Direct publish to Redis Stream — bypasses outbox.
     * Use for analytics/search-log events where loss is acceptable.
     */
    public void publishDirect(String streamKey, Map<String, String> fields) {
        try {
            redisTemplate.opsForStream()
                .add(StreamRecords.newRecord().in(streamKey).ofMap(fields));
        } catch (Exception e) {
            log.warn("Direct publish failed: stream={} error={}", streamKey, e.getMessage());
        }
    }

    private String resolveStreamKey(String eventType) {
        AppProperties.Redis.Streams s = props.getRedis().getStreams();
        return switch (eventType) {
            case InquiryCreatedEvent.TYPE          -> s.getInquiryCreated();
            case SupplierProfileUpdatedEvent.TYPE  -> s.getSupplierUpdated();
            case VitalityScoreUpdatedEvent.TYPE    -> s.getVitalityUpdated();
            default -> null;
        };
    }

    private Map<String, String> flattenPayload(OutboxEvent event) {
        // Flatten payload JSONB + metadata into Redis Stream field map (all strings)
        Map<String, Object> payload = event.getPayload();
        java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("event_id",    event.getEventId().toString());
        fields.put("event_type",  event.getEventType());
        fields.put("tenant_id",   event.getTenantId());
        fields.put("aggregate_id", event.getAggregateId());
        // Flatten nested payload fields
        payload.forEach((k, v) -> fields.put(k, v != null ? v.toString() : ""));
        return fields;
    }
}
