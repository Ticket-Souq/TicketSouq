package org.ticketsouq.reservationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ticketsouq.reservationservice.dto.CheckoutRequest;
import org.ticketsouq.reservationservice.dto.CheckoutResponse;
import org.ticketsouq.reservationservice.dto.ReservationResponse;
import org.ticketsouq.reservationservice.enums.ReservationStatus;
import org.ticketsouq.reservationservice.exception.ReservationExceptionHandler;
import org.ticketsouq.reservationservice.service.ReservationService;
import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    @Mock private ReservationService reservationService;
    @InjectMocks private ReservationController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    private void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ReservationExceptionHandler())
            .build();
    }

    @Test
    void checkoutSeats_shouldReturn201() throws Exception {
        setUp();
        UUID customerId = UUID.randomUUID();
        CheckoutRequest request = new CheckoutRequest(UUID.randomUUID(), List.of(UUID.randomUUID()), null, null);
        CheckoutResponse response = new CheckoutResponse(
            UUID.randomUUID(), customerId, request.eventId(),
            ReservationStatus.LOCKED, LocalDateTime.now().plusMinutes(10),
            List.of(), BigDecimal.TEN, Instant.now());

        when(reservationService.createCheckout(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/reservations/checkout")
                .header("X-User-Id", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reservationId").value(response.reservationId().toString()));
    }

    @Test
    void checkoutSeats_withIdempotencyKey_shouldReturnCached() throws Exception {
        setUp();
        UUID customerId = UUID.randomUUID();
        CheckoutRequest request = new CheckoutRequest(UUID.randomUUID(), List.of(UUID.randomUUID()), null, null);
        CheckoutResponse cached = new CheckoutResponse(
            UUID.randomUUID(), customerId, request.eventId(),
            ReservationStatus.LOCKED, LocalDateTime.now().plusMinutes(10),
            List.of(), BigDecimal.TEN, Instant.now());

        when(reservationService.getIdempotentResponse("my-key")).thenReturn(cached);

        mockMvc.perform(post("/api/v1/reservations/checkout")
                .header("X-User-Id", customerId)
                .header("Idempotency-Key", "my-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reservationId").value(cached.reservationId().toString()));
    }

    @Test
    void checkoutSeats_withBadRequest_shouldReturn400() throws Exception {
        setUp();
        UUID customerId = UUID.randomUUID();
        CheckoutRequest request = new CheckoutRequest(UUID.randomUUID(), null, null, null);

        when(reservationService.createCheckout(any(), any())).thenThrow(new BadRequestException("Invalid request"));

        mockMvc.perform(post("/api/v1/reservations/checkout")
                .header("X-User-Id", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getReservation_shouldReturn200() throws Exception {
        setUp();
        UUID reservationId = UUID.randomUUID();
        ReservationResponse response = new ReservationResponse(
            reservationId, UUID.randomUUID(), UUID.randomUUID(),
            ReservationStatus.LOCKED, LocalDateTime.now().plusMinutes(10),
            List.of(), BigDecimal.TEN, null, Instant.now());

        when(reservationService.getReservation(reservationId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/reservations/{id}", reservationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(reservationId.toString()));
    }

    @Test
    void getReservation_notFound_shouldReturn404() throws Exception {
        setUp();
        UUID reservationId = UUID.randomUUID();
        when(reservationService.getReservation(reservationId))
            .thenThrow(new ResourceNotFoundException("Reservation", reservationId));

        mockMvc.perform(get("/api/v1/reservations/{id}", reservationId))
            .andExpect(status().isNotFound());
    }

    @Test
    void getMyReservations_shouldReturn200() throws Exception {
        setUp();
        UUID customerId = UUID.randomUUID();
        ReservationResponse response = new ReservationResponse(
            UUID.randomUUID(), customerId, UUID.randomUUID(),
            ReservationStatus.LOCKED, LocalDateTime.now().plusMinutes(10),
            List.of(), BigDecimal.TEN, null, Instant.now());

        when(reservationService.getReservationsByCustomer(customerId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/reservations").header("X-User-Id", customerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size()").value(1));
    }
}
