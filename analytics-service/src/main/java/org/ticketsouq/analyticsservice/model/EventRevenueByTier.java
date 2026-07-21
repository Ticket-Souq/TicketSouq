package org.ticketsouq.analyticsservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "event_revenue_by_tier", indexes = {
    @Index(name = "idx_rev_tier_org_event", columnList = "organization_id, event_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(EventRevenueByTierId.class)
public class EventRevenueByTier {

    @Id
    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    // tier id should be replaced with enum value like VIP
    @Id
    @Column(name = "tier_id", nullable = false)
    private String tierId;

    @Id
    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "tickets_sold", nullable = false)
    @Builder.Default
    private Integer ticketsSold = 0;


    @Column(name = "revenue", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal revenue = BigDecimal.ZERO;
}
