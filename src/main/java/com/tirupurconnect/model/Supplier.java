package com.tirupurconnect.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
@Getter @Setter
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    @Column(name = "owner_phone", nullable = false, length = 15)
    private String ownerPhone;

    @Column(name = "gst_number", length = 15)
    private String gstNumber;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "zone_visibility", nullable = false)
    private Short zoneVisibility = 1;

    @Column(name = "trust_score", nullable = false)
    private Short trustScore = 0;

    @Column(name = "vitality_score", nullable = false)
    private Short vitalityScore = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplierStatus status = SupplierStatus.DORMANT;

    @Column(name = "profile_complete_pct", nullable = false)
    private Short profileCompletePct = 0;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @Column(name = "export_certified", nullable = false)
    private boolean exportCertified = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_hours", columnDefinition = "jsonb")
    private Map<String, Object> businessHours;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public enum SupplierStatus { ACTIVE, DORMANT, FADING, GHOST, CLOSED }

    /** Compute trust score from individual signal flags. */
    public void recomputeTrustScore() {
        int score = 0;
        if (this.verified)         score += 25;
        if (this.gstNumber != null) score += 15;
        if (this.profileCompletePct >= 100) score += 10;
        if (this.exportCertified)  score += 10;
        this.trustScore = (short) Math.min(100, score);
    }
}
