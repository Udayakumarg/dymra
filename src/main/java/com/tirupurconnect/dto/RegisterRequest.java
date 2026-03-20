package com.tirupurconnect.dto;
import com.tirupurconnect.model.User;
import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank @Pattern(regexp = "^\\+?[1-9]\\d{9,14}$", message = "Invalid phone number") String phone,
    @NotBlank String name,
    @NotNull User.UserRole role,
    @NotBlank String tenantSlug
) {}
