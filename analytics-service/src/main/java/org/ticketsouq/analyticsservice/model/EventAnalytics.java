package org.ticketsouq.analyticsservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "event_analytics")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventAnalytics {

    @Id
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "organization_id", length = 36)
    private String organizationId;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "title")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EventStatus status = EventStatus.CREATED;

    @Column(name = "total_revenue", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "total_tickets_sold")
    @Builder.Default
    private Integer totalTicketsSold = 0;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "total_failed_payments")
    @Builder.Default
    private Integer totalFailedPayments = 0;

    @Column(name = "start_date_time")
    private Instant startDateTime;

    @Column(name = "end_date_time")
    private Instant endDateTime;

    @Column(name = "last_event_timestamp")
    private Instant lastEventTimestamp;
}
