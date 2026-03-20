package com.tirupurconnect.dto;
import java.util.UUID;

public record SearchResultResponse(UUID supplierId, String businessName,
    String type, String categorySlug, short trustScore, double distanceKm,
    String thumbUrl, boolean openNow) {}
