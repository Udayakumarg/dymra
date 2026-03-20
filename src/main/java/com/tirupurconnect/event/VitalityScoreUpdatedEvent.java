package com.tirupurconnect.event;
import java.time.Instant;
import java.util.UUID;

public record VitalityScoreUpdatedEvent(
    String eventId, String eventType, String tenantId, UUID supplierId,
    short oldScore, short newScore, String oldStatus, String newStatus,
    String signalApplied, short signalPoints, boolean replayMode, Instant timestamp
) {
    public static final String TYPE = "vitality.score.updated";

    public static VitalityScoreUpdatedEvent of(UUID supplierId, String tenantId,
            short oldScore, short newScore, String oldStatus, String newStatus,
            String signal, short points) {
        return new VitalityScoreUpdatedEvent(UUID.randomUUID().toString(), TYPE,
            tenantId, supplierId, oldScore, newScore, oldStatus, newStatus,
            signal, points, false, Instant.now());
    }
}
