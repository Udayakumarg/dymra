package com.tirupurconnect.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "listings")
@Getter @Setter
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingType type;

    @Column(name = "title_en", nullable = false, length = 300)
    private String titleEn;

    @Column(name = "title_ta", length = 300)
    private String titleTa;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public enum ListingType { PRODUCT, SERVICE }
}
