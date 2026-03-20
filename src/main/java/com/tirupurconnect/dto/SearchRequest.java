package com.tirupurconnect.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

// FIX: Removed compact constructor reassignment (illegal in records).
// radiusKm, sessionId, zone now have defaults handled in SearchService.
// lat/lon removed @NotNull — anonymous searches without GPS are valid.
public record SearchRequest(
    @NotBlank String queryText,
    Double lat,
    Double lon,
    Integer radiusKm,
    UUID sessionId,
    Short zone
) {}
