package org.ticketsouq.reservationservice.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.reservationservice.client.EventServiceClient;
import org.ticketsouq.reservationservice.dto.CheckoutRequest;
import org.ticketsouq.reservationservice.dto.CheckoutResponse;
import org.ticketsouq.reservationservice.enums.ReservationStatus;
import org.ticketsouq.reservationservice.model.Reservation;
import org.ticketsouq.reservationservice.repository.ReservationRepository;
import org.ticketsouq.sharedmodule.EventService.dto.LockItem;
import org.ticketsouq.sharedmodule.EventService.dto.LockSeatsRequest;
import org.ticketsouq.sharedmodule.EventService.dto.LockSeatsResponse;
import org.ticketsouq.sharedmodule.EventService.dto.LockZoneRequest;
import org.ticketsouq.sharedmodule.EventService.dto.LockZoneResponse;
import org.ticketsouq.reservationservice.dto.ReservationResponse;
import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;
import org.ticketsouq.sharedmodule.ReservationService.dto.ReleaseRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final EventServiceClient eventServiceClient;

    @Value("${app.lock.ttl:10}")
    private int lockTtlMinutes;

    private final Cache<String, CheckoutResponse> idempotencyCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(30))
        .maximumSize(50_000)
        .build();

    public CheckoutResponse getIdempotentResponse(String idempotencyKey) {
        return idempotencyCache.getIfPresent(idempotencyKey);
    }

    public void cacheIdempotentResponse(String idempotencyKey, CheckoutResponse response) {
        idempotencyCache.put(idempotencyKey, response);
    }

    public CheckoutResponse createCheckout(UUID customerId, CheckoutRequest request) {
        validateCheckoutRequest(request);

        UUID reservationId = UUID.randomUUID();

        CheckoutResponse response = callEventService(request, reservationId);

        try {
            Reservation reservation = Reservation.builder()
                .id(reservationId)
                .customerId(customerId)
                .eventId(request.eventId())
                .status(ReservationStatus.LOCKED)
                .totalAmount(response.totalPrice())
                .build();
            reservationRepository.save(reservation);
        } catch (RuntimeException ex) {
            log.warn("Reservation save failed for {}, releasing lock: {}", reservationId, ex.getMessage());
            eventServiceClient.release(new ReleaseRequest(reservationId.toString()));
            throw ex;
        }

        return response;
    }

    private void validateCheckoutRequest(CheckoutRequest request) {
        if ((request.seatIds() == null || request.seatIds().isEmpty())
            && request.zoneId() == null) {
            throw new BadRequestException("Either seatIds or zoneId must be provided");
        }
        if (request.zoneId() != null
            && (request.quantity() == null || request.quantity() < 1)) {
            throw new BadRequestException("quantity is required and must be positive for zone-based requests");
        }
    }

    private CheckoutResponse callEventService(CheckoutRequest request, UUID reservationId) {
        if (request.seatIds() != null && !request.seatIds().isEmpty()) {
            return lockSeats(request, reservationId);
        }
        return lockZone(request, reservationId);
    }

    private CheckoutResponse lockSeats(CheckoutRequest request, UUID reservationId) {
        LockSeatsRequest lockRequest = new LockSeatsRequest(
            reservationId.toString(), request.seatIds());

        LockSeatsResponse lockResponse = eventServiceClient.lockSeats(request.eventId(), lockRequest);

        List<LockItem> items = lockResponse.items() != null ? lockResponse.items() : List.of();

        return new CheckoutResponse(
            reservationId,
            null,
            request.eventId(),
            ReservationStatus.LOCKED,
            lockResponse.expiresAt(),
            items,
            lockResponse.totalPrice() != null ? lockResponse.totalPrice() : BigDecimal.ZERO,
            Instant.now()
        );
    }

    public ReservationResponse getReservation(UUID id) {
        Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation", id));
        return toResponse(reservation);
    }

    public List<ReservationResponse> getReservationsByCustomer(UUID customerId) {
        return reservationRepository.findByCustomerId(customerId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
            reservation.getId(),
            reservation.getCustomerId(),
            reservation.getEventId(),
            reservation.getStatus(),
            null,
            List.of(),
            reservation.getTotalAmount(),
            null,
            reservation.getCreatedAt()
        );
    }

    private CheckoutResponse lockZone(CheckoutRequest request, UUID reservationId) {
        LockZoneRequest lockRequest = new LockZoneRequest(
            reservationId.toString(), request.zoneId(), request.quantity());

        LockZoneResponse lockResponse = eventServiceClient.lockZone(request.eventId(), lockRequest);

        return new CheckoutResponse(
            reservationId,
            null,
            request.eventId(),
            ReservationStatus.LOCKED,
            lockResponse.expiresAt(),
            List.of(),
            lockResponse.totalPrice() != null ? lockResponse.totalPrice() : BigDecimal.ZERO,
            Instant.now()
        );
    }
}
