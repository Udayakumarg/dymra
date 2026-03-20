package com.tirupurconnect.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "search_log")
@Getter @Setter
@NoArgsConstructor
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "buyer_id")
    private UUID buyerId;

    @Column(name = "query_text", nullable = false, columnDefinition = "text")
    private String queryText;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    private Short zone;

    @Column(name = "result_count", nullable = false)
    private Integer resultCount = 0;

    @Column(name = "is_zero_result", nullable = false)
    private boolean zeroResult = false;

    @Column(name = "searched_at", nullable = false, updatable = false)
    private Instant searchedAt = Instant.now();
}
