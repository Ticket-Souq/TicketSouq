package org.ticketsouq.analyticsservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "sales_records", uniqueConstraints = {
    @UniqueConstraint(name = "uq_sales_event_date", columnNames = {"event_id", "sale_date"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SalesRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "organization_id", length = 36)
    private String organizationId;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "tickets_sold")
    @Builder.Default
    private Integer ticketsSold = 0;

    @Column(name = "revenue", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal revenue = BigDecimal.ZERO;
}
