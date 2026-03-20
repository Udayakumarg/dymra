package com.tirupurconnect.dto;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank String phone,
    @NotBlank String otp
) {}
