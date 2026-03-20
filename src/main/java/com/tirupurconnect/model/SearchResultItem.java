package com.tirupurconnect.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "search_result_item")
@Getter @Setter
@NoArgsConstructor
public class SearchResultItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "search_log_id", nullable = false)
    private UUID searchLogId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "query_text", columnDefinition = "text")
    private String queryText;

    @Column(name = "position_shown", nullable = false)
    private Short positionShown;

    @Column(name = "relevance_score", precision = 8, scale = 4)
    private BigDecimal relevanceScore;

    @Column(name = "buyer_rating")
    private Short buyerRating;

    @Column(nullable = false)
    private boolean contacted = false;
}
