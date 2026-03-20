package com.tirupurconnect.dto;
import jakarta.validation.constraints.*;

public record SupplierCreateRequest(
    @NotBlank @Size(max = 200) String businessName,
    String gstNumber,
    @NotNull Double latitude,
    @NotNull Double longitude
) {}
