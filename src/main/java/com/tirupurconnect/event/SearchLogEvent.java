package com.tirupurconnect.event;
import java.time.Instant;
import java.util.UUID;

public record SearchLogEvent(
    String eventId, String tenantId, UUID sessionId, UUID buyerId,
    String queryText, double lat, double lon, short zone,
    long resultCount, boolean zeroResult, Instant timestamp
) {
    public static SearchLogEvent of(String tenantId, UUID sessionId, UUID buyerId,
            String queryText, double lat, double lon, short zone, long resultCount) {
        return new SearchLogEvent(UUID.randomUUID().toString(), tenantId, sessionId,
            buyerId, queryText, lat, lon, zone, resultCount, resultCount == 0, Instant.now());
    }
}
