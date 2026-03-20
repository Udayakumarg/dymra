package com.tirupurconnect.event;
import java.time.Instant;
import java.util.UUID;

public record SupplierProfileUpdatedEvent(
    String eventId, String eventType, String tenantId, UUID supplierId,
    String vitalitySignal, short profileCompletePct, boolean replayMode, Instant timestamp
) {
    public static final String TYPE = "supplier.profile.updated";

    public static SupplierProfileUpdatedEvent of(UUID supplierId, String tenantId,
            String vitalitySignal, short pct) {
        return new SupplierProfileUpdatedEvent(UUID.randomUUID().toString(), TYPE,
            tenantId, supplierId, vitalitySignal, pct, false, Instant.now());
    }
}
