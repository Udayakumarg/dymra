package com.tirupurconnect.dto;
import java.time.Instant;
import java.util.UUID;

public record InquiryResponse(UUID id, UUID supplierId, String status, Instant createdAt) {}
