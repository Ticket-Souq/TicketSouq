package org.ticketsouq.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.paymentservice.model.PaymentModel;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentModel, UUID> {
    Optional<PaymentModel> findByStripePaymentIntentId(
        String stripePaymentIntentId);

    Optional<PaymentModel> findByReservationID(UUID reservationID);

    java.util.List<PaymentModel> findByEventId(UUID eventId);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentModel p WHERE p.eventId = :eventId AND p.paymentStatus = :status")
    java.math.BigDecimal sumAmountByEventIdAndStatus(@org.springframework.data.repository.query.Param("eventId") UUID eventId,
                                                      @org.springframework.data.repository.query.Param("status") org.ticketsouq.paymentservice.enums.PaymentStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT p.eventId as eventId, COALESCE(SUM(p.amount), 0) as total FROM PaymentModel p WHERE p.eventId IN :eventIds AND p.paymentStatus = :status GROUP BY p.eventId")
    java.util.List<Object[]> sumOwedGroupedByEventIds(@org.springframework.data.repository.query.Param("eventIds") java.util.Collection<UUID> eventIds,
                                                      @org.springframework.data.repository.query.Param("status") org.ticketsouq.paymentservice.enums.PaymentStatus status);
}
