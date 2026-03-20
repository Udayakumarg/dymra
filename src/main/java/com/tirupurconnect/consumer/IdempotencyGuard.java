package com.tirupurconnect.consumer;

import com.tirupurconnect.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Prevents duplicate event processing across consumer restarts.
 *
 * Uses Redis SETNX (SET if Not eXists) with TTL per event_id key.
 * TTL defaults to 48h — long enough to cover retry windows.
 *
 * Key pattern: idempotency:{consumerGroup}:{eventId}
 */
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    private final StringRedisTemplate redisTemplate;
    private final AppProperties       props;

    private static final String KEY_PREFIX = "idempotency:";

    /**
     * Returns true if this event has already been processed by this consumer group.
     * Call before processing; if true, ACK and skip.
     */
    public boolean alreadyProcessed(String consumerGroup, String eventId) {
        String key = KEY_PREFIX + consumerGroup + ":" + eventId;
        return Boolean.FALSE.equals(
            redisTemplate.opsForValue().setIfAbsent(key, "1",
                Duration.ofHours(props.getRedis().getIdempotencyTtlHours()))
        );
        // setIfAbsent returns true when key was NEW (first time), false when key already existed
        // We return !result: alreadyProcessed = key existed before this call
    }

    /**
     * Convenience: check + mark in one call.
     * Returns true if already processed (caller should skip).
     * Returns false if this is the first time (caller should process).
     */
    public boolean checkAndMark(String consumerGroup, String eventId) {
        return alreadyProcessed(consumerGroup, eventId);
    }
}
