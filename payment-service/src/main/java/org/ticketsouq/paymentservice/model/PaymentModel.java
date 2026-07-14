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

    @Enumerated(EnumType.STRING)
    PaymentStatus paymentStatus;


    @Enumerated(EnumType.STRING)
    PaymentProviderEnum paymentProvider;

    private String transactionRef;

    private Instant createdAt;

    private Instant updatedAt;


}
