package com.tirupurconnect.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vitality_events")
@Getter @Setter
@NoArgsConstructor
public class VitalityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VitalitySignal signal;

    @Column(nullable = false)
    private Short points;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    public VitalityEvent(UUID supplierId, VitalitySignal signal, short points) {
        this.supplierId = supplierId;
        this.signal = signal;
        this.points = points;
    }

    public enum VitalitySignal {
        WA_RESPONSE, INQUIRY_RESPONDED, CATALOGUE_UPDATED,
        PHONE_VERIFIED, APP_LOGIN, INQUIRY_CREATED
    }
}
