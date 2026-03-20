package com.tirupurconnect.event;
import java.time.Instant;
import java.util.UUID;

public record InquiryCreatedEvent(
    String eventId, String eventType, String tenantId, UUID inquiryId,
    UUID supplierId, UUID buyerId, UUID searchResultItemId,
    int positionShown, String queryText, boolean replayMode, Instant timestamp
) {
    public static final String TYPE = "inquiry.created";

    public static InquiryCreatedEvent of(UUID inquiryId, UUID supplierId, UUID buyerId,
            UUID searchResultItemId, int positionShown, String queryText, String tenantId) {
        return new InquiryCreatedEvent(UUID.randomUUID().toString(), TYPE, tenantId,
            inquiryId, supplierId, buyerId, searchResultItemId,
            positionShown, queryText, false, Instant.now());
    }
}
