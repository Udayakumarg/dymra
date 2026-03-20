package com.tirupurconnect.dto;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record SearchRequest(
    @NotBlank String queryText,
    @NotNull Double lat,
    @NotNull Double lon,
    int radiusKm,
    UUID sessionId,
    Short zone
) {
    public SearchRequest {
        if (radiusKm <= 0) radiusKm = 15;
        if (sessionId == null) sessionId = UUID.randomUUID();
        if (zone == null) zone = 1;
    }
}
