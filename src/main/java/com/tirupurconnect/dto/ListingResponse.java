package com.tirupurconnect.dto;
import com.tirupurconnect.model.Listing;
import java.time.Instant;
import java.util.UUID;

public record ListingResponse(UUID id, String titleEn, String titleTa,
    String description, String type, Integer categoryId, boolean active, Instant updatedAt) {

    public static ListingResponse from(Listing l) {
        return new ListingResponse(l.getId(), l.getTitleEn(), l.getTitleTa(),
            l.getDescription(), l.getType().name(), l.getCategoryId(),
            l.isActive(), l.getUpdatedAt());
    }
}
