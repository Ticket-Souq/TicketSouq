package org.ticketsouq.reservationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.mockito.ArgumentCaptor;
import org.ticketsouq.sharedmodule.ReservationService.dto.ReleaseRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private EventServiceClient eventServiceClient;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(reservationRepository, eventServiceClient);
    }

    @Test
    @DisplayName("Should lock seats via Event Service, then save LOCKED reservation")
    void givenSeatCheckoutRequest_whenCreateCheckout_thenSucceed() {
        UUID customerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        CheckoutRequest request = new CheckoutRequest(eventId, List.of(seatId), null, null);
        LockItem item = new LockItem(seatId, "A", "1", "VIP", BigDecimal.valueOf(250), 1);
        LockSeatsResponse lockResponse = new LockSeatsResponse(
            "LOCKED", LocalDateTime.now().plusMinutes(10),
            List.of(item), BigDecimal.valueOf(250));

        when(eventServiceClient.lockSeats(eq(eventId), any(LockSeatsRequest.class))).thenReturn(lockResponse);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckoutResponse response = reservationService.createCheckout(customerId, request);

        assertThat(response.reservationId()).isNotNull();
        assertThat(response.status()).isEqualTo(ReservationStatus.LOCKED);
        assertThat(response.items()).hasSize(1);
        assertThat(response.totalPrice()).isEqualByComparingTo(BigDecimal.valueOf(250));

        verify(reservationRepository, times(1)).save(argThat(r -> r.getStatus() == ReservationStatus.LOCKED));
        verify(eventServiceClient).lockSeats(eq(eventId), any(LockSeatsRequest.class));
    }

    @Test
    @DisplayName("Should lock zone via Event Service, then save LOCKED reservation")
    void givenZoneCheckoutRequest_whenCreateCheckout_thenSucceed() {
        UUID customerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        CheckoutRequest request = new CheckoutRequest(eventId, null, zoneId, 3);

        LockZoneResponse lockResponse = new LockZoneResponse(
            "LOCKED", LocalDateTime.now().plusMinutes(10), zoneId, 3, BigDecimal.valueOf(300));

        when(eventServiceClient.lockZone(eq(eventId), any(LockZoneRequest.class))).thenReturn(lockResponse);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckoutResponse response = reservationService.createCheckout(customerId, request);

        assertThat(response.reservationId()).isNotNull();
        assertThat(response.status()).isEqualTo(ReservationStatus.LOCKED);
        assertThat(response.items()).isEmpty();
        assertThat(response.totalPrice()).isEqualByComparingTo(BigDecimal.valueOf(300));

        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(eventServiceClient).lockZone(eq(eventId), any(LockZoneRequest.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when both seatIds and zoneId are null")
    void givenMissingSeatIdsAndZoneId_whenCreateCheckout_thenThrowBadRequest() {
        CheckoutRequest request = new CheckoutRequest(UUID.randomUUID(), null, null, null);

        assertThatThrownBy(() -> reservationService.createCheckout(UUID.randomUUID(), request))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Should throw BadRequestException when zoneId provided but quantity is null")
    void givenZoneIdWithoutQuantity_whenCreateCheckout_thenThrowBadRequest() {
        CheckoutRequest request = new CheckoutRequest(UUID.randomUUID(), null, UUID.randomUUID(), null);

        assertThatThrownBy(() -> reservationService.createCheckout(UUID.randomUUID(), request))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Should throw BadRequestException when zoneId provided but quantity is zero")
    void givenZoneIdWithZeroQuantity_whenCreateCheckout_thenThrowBadRequest() {
        CheckoutRequest request = new CheckoutRequest(UUID.randomUUID(), null, UUID.randomUUID(), 0);

        assertThatThrownBy(() -> reservationService.createCheckout(UUID.randomUUID(), request))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("No reservation saved and no release called if lock fails")
    void givenLockFails_thenNoReservationSaved() {
        UUID customerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        CheckoutRequest request = new CheckoutRequest(eventId, List.of(seatId), null, null);

        when(eventServiceClient.lockSeats(eq(eventId), any(LockSeatsRequest.class)))
            .thenThrow(new RuntimeException("Event Service unavailable"));

        assertThatThrownBy(() -> reservationService.createCheckout(customerId, request))
            .isInstanceOf(RuntimeException.class);

        verify(reservationRepository, never()).save(any());
        verify(eventServiceClient, never()).release(any());
    }

    @Test
    @DisplayName("Release called if lock succeeds but save fails")
    void givenSaveFails_thenReleaseCalled() {
        UUID customerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        CheckoutRequest request = new CheckoutRequest(eventId, List.of(seatId), null, null);

        LockItem item = new LockItem(seatId, "A", "1", "VIP", BigDecimal.valueOf(250), 1);
        LockSeatsResponse lockResponse = new LockSeatsResponse(
            "LOCKED", LocalDateTime.now().plusMinutes(10),
            List.of(item), BigDecimal.valueOf(250));

        when(eventServiceClient.lockSeats(eq(eventId), any(LockSeatsRequest.class))).thenReturn(lockResponse);
        when(reservationRepository.save(any(Reservation.class))).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> reservationService.createCheckout(customerId, request))
            .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<ReleaseRequest> releaseCaptor = ArgumentCaptor.forClass(ReleaseRequest.class);
        verify(eventServiceClient).release(releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().reservationId()).isNotNull();
    }

    @Test
    @DisplayName("Idempotency: same key returns cached response without calling Event Service")
    void givenIdempotencyKey_whenCached_thenReturnsCachedResponse() {
        UUID reservationId = UUID.randomUUID();
        CheckoutResponse cached = new CheckoutResponse(
            reservationId, UUID.randomUUID(), UUID.randomUUID(),
            ReservationStatus.LOCKED, LocalDateTime.now().plusMinutes(10),
            List.of(), BigDecimal.TEN, java.time.Instant.now());

        reservationService.cacheIdempotentResponse("test-key", cached);

        CheckoutResponse result = reservationService.getIdempotentResponse("test-key");
        assertThat(result).isNotNull();
        assertThat(result.reservationId()).isEqualTo(reservationId);
    }
}
