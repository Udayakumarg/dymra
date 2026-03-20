package com.tirupurconnect.dto;
import java.time.Instant;
import java.util.UUID;

public record VitalityStatusResponse(UUID supplierId, short vitalityScore, String status, Instant lastActiveAt) {}
