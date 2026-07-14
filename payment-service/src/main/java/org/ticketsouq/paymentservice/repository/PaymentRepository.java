package org.ticketsouq.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.paymentservice.model.PaymentModel;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentModel, UUID> {
}
