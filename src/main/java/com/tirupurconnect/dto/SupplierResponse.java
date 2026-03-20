package com.tirupurconnect.dto;
import com.tirupurconnect.model.Supplier;
import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(UUID id, String businessName, String ownerPhone,
    String gstNumber, short trustScore, short vitalityScore, String status,
    short profileCompletePct, boolean verified, Double latitude, Double longitude,
    Instant lastActiveAt) {

    public static SupplierResponse from(Supplier s) {
        Double lat = s.getLocation() != null ? s.getLocation().getY() : null;
        Double lon = s.getLocation() != null ? s.getLocation().getX() : null;
        return new SupplierResponse(s.getId(), s.getBusinessName(), s.getOwnerPhone(),
            s.getGstNumber(), s.getTrustScore(), s.getVitalityScore(), s.getStatus().name(),
            s.getProfileCompletePct(), s.isVerified(), lat, lon, s.getLastActiveAt());
    }
}
