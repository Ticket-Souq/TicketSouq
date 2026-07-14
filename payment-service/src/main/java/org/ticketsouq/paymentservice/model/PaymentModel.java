package org.ticketsouq.paymentservice.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketsouq.paymentservice.enums.PaymentProviderEnum;
import org.ticketsouq.paymentservice.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    UUID reservationID;
    UUID customerID;
    BigDecimal amount;

    String currency;

    @Enumerated(EnumType.STRING)
    PaymentStatus paymentStatus;


    @Enumerated(EnumType.STRING)
    PaymentProviderEnum paymentProvider;

    private String transactionRef;

    private String stripePaymentIntentId;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

}
