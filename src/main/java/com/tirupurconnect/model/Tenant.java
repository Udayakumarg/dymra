package com.tirupurconnect.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter @Setter
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    @Column(name = "city_name", nullable = false, length = 100)
    private String cityName;

    @Column(name = "default_zone_radius_km", nullable = false)
    private Short defaultZoneRadiusKm = 15;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vitality_weights", columnDefinition = "jsonb")
    private Map<String, Object> vitalityWeights;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seasonal_pauses", columnDefinition = "jsonb")
    private Object seasonalPauses;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "search_weights", columnDefinition = "jsonb")
    private Map<String, Object> searchWeights;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
