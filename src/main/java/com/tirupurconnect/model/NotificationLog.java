package com.tirupurconnect.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_log")
@Getter @Setter
@NoArgsConstructor
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(nullable = false, length = 20)
    private String channel = "WHATSAPP";

    @Column(name = "message_type", nullable = false, length = 50)
    private String messageType;

    @Column(nullable = false, length = 15)
    private String phone;

    private Boolean delivered;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt = Instant.now();

    public NotificationLog(UUID supplierId, String messageType, String phone) {
        this.supplierId = supplierId;
        this.messageType = messageType;
        this.phone = phone;
    }
}
