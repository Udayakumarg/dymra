package com.tirupurconnect.consumer;

import com.tirupurconnect.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class BaseStreamConsumer {

    protected abstract StringRedisTemplate redisTemplate();
    protected abstract IdempotencyGuard    idempotencyGuard();
    protected abstract AppProperties       appProperties();
    protected abstract String streamKey();
    protected abstract String consumerGroup();
    protected abstract String consumerName();
    protected abstract void process(Map<String, String> fields) throws Exception;

    protected void poll() {
        List<MapRecord<String, Object, Object>> records = readPending();
        if (records == null || records.isEmpty()) return;

        for (MapRecord<String, Object, Object> record : records) {
            String eventId = String.valueOf(record.getValue().get("event_id"));
            try {
                if (idempotencyGuard().checkAndMark(consumerGroup(), eventId)) {
                    ack(record.getId());
                    log.debug("Skipped duplicate: group={} eventId={}", consumerGroup(), eventId);
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, String> fields = (Map<String, String>) (Map<?, ?>) record.getValue();
                process(fields);
                ack(record.getId());
                log.debug("Processed: group={} eventId={}", consumerGroup(), eventId);
            } catch (Exception e) {
                log.error("Processing failed: group={} eventId={} error={}", consumerGroup(), eventId, e.getMessage());
                handleFailure(record, eventId, e);
            }
        }
    }

    private List<MapRecord<String, Object, Object>> readPending() {
        try {
            return redisTemplate().opsForStream().read(
                Consumer.from(consumerGroup(), consumerName()),
                StreamReadOptions.empty().count(10).block(Duration.ofMillis(200)),
                StreamOffset.create(streamKey(), ReadOffset.lastConsumed())
            );
        } catch (Exception e) {
            log.warn("Stream read failed: stream={} error={}", streamKey(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private void ack(RecordId recordId) {
        redisTemplate().opsForStream().acknowledge(streamKey(), consumerGroup(), recordId);
    }

    private void handleFailure(MapRecord<String, Object, Object> record, String eventId, Exception e) {
        try {
            PendingMessages pending = redisTemplate().opsForStream().pending(
                streamKey(), consumerGroup(),
                Range.closed(record.getId().getValue(), record.getId().getValue()), 1L
            );
            long deliveryCount = pending.isEmpty() ? 1 : pending.get(0).getTotalDeliveryCount();
            if (deliveryCount >= 3) {
                sendToDeadLetter(eventId, e.getMessage());
                ack(record.getId());
            }
        } catch (Exception ex) {
            log.warn("Could not check delivery count for eventId={}: {}", eventId, ex.getMessage());
        }
    }

    private void sendToDeadLetter(String eventId, String error) {
        String dlq = appProperties().getRedis().getStreams().getDeadLetter();
        try {
            redisTemplate().opsForStream().add(
                StreamRecords.newRecord().in(dlq).ofMap(Map.of(
                    "original_stream", streamKey(),
                    "consumer_group",  consumerGroup(),
                    "event_id",        eventId,
                    "error",           error != null ? error : "unknown",
                    "failed_at",       Instant.now().toString()
                ))
            );
            log.error("Sent to dead-letter: stream={} eventId={}", dlq, eventId);
        } catch (Exception ex) {
            log.error("Could not write to dead-letter stream: {}", ex.getMessage());
        }
    }
}
