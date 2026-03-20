package com.tirupurconnect.dto;
import java.util.UUID;

public record AuthResponse(String token, String role, UUID userId, String tenantId) {}
