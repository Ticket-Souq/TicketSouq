package org.ticketsouq.paymentservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID organizerId;

    @Column(name = "event_id")
    private UUID eventId;

    private String organization;

    private BigDecimal amount;

    @Column(name = "net_amount")
    private BigDecimal netAmount;

    private String currency;
    private String status;
    private String stripeTransferId;

    @Column(name = "provider_transfer_id")
    private String providerTransferId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "retry_count")
    private Integer retryCount;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
