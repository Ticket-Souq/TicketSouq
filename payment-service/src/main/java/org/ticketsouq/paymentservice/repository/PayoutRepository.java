package org.ticketsouq.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.paymentservice.model.Payout;

import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {
}
