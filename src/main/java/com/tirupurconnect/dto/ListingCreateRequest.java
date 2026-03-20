package com.tirupurconnect.dto;
import com.tirupurconnect.model.Listing;
import jakarta.validation.constraints.*;

public record ListingCreateRequest(
    @NotBlank @Size(max = 300) String titleEn,
    @Size(max = 300) String titleTa,
    @Size(max = 2000) String description,
    @NotNull Listing.ListingType type,
    Integer categoryId
) {}
