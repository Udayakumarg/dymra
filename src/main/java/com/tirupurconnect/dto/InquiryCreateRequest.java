package com.tirupurconnect.dto;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record InquiryCreateRequest(
    @NotNull UUID supplierId,
    UUID searchResultItemId,
    @NotBlank @Size(max = 1000) String message
) {}
