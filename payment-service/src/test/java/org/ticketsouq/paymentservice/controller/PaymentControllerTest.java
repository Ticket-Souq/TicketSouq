package org.ticketsouq.paymentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.GlobalExceptionHandler;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();
    private static final UUID RESERVATION_ID = UUID.randomUUID();
    private static final String CLIENT_SECRET = "pi_secret_test";

    @Mock
    private PaymentRepository paymentRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new PaymentController(paymentRepository))
            .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
            .build();
    }

    private PaymentModel payment(PaymentStatus status, UUID owner) {
        return PaymentModel.builder()
            .id(UUID.randomUUID())
            .reservationID(RESERVATION_ID)
            .customerID(owner)
            .amount(new BigDecimal("100.00"))
            .paymentStatus(status)
            .stripePaymentIntentId("pi_123")
            .clientSecret(CLIENT_SECRET)
            .build();
    }

    @Test
    void ownerCanReadOwnPendingPaymentByReservation_andGetsClientSecret() throws Exception {
        PaymentModel payment = payment(PaymentStatus.PENDING, OWNER);
        when(paymentRepository.findByReservationID(RESERVATION_ID)).thenReturn(Optional.of(payment));

        mockMvc.perform(get("/api/v1/payment/reservation/{reservationId}", RESERVATION_ID)
                .header("X-User-Id", OWNER.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentID").value(payment.getId().toString()))
            .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
            .andExpect(jsonPath("$.clientSecret").value(CLIENT_SECRET));
    }

    @Test
    void ownerCanReadOwnPaymentById() throws Exception {
        PaymentModel payment = payment(PaymentStatus.SUCCESS, OWNER);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        mockMvc.perform(get("/api/v1/payment/{paymentId}", payment.getId())
                .header("X-User-Id", OWNER.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"));
    }

    @Test
    void customerCannotReadAnotherUsersPayment() throws Exception {
        PaymentModel payment = payment(PaymentStatus.PENDING, OWNER);
        when(paymentRepository.findByReservationID(RESERVATION_ID)).thenReturn(Optional.of(payment));

        mockMvc.perform(get("/api/v1/payment/reservation/{reservationId}", RESERVATION_ID)
                .header("X-User-Id", OTHER_USER.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void missingUserHeaderIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/payment/reservation/{reservationId}", RESERVATION_ID))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void nonPendingResponsesDoNotExposeClientSecret() throws Exception {
        for (PaymentStatus status : new PaymentStatus[]{
            PaymentStatus.SUCCESS, PaymentStatus.FAILED, PaymentStatus.REFUNDED}) {
            PaymentModel payment = payment(status, OWNER);
            when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

            mockMvc.perform(get("/api/v1/payment/{paymentId}", payment.getId())
                    .header("X-User-Id", OWNER.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value(status.name()))
                .andExpect(jsonPath("$.clientSecret").value(nullValue()));
        }
    }

    @Test
    void missingPaymentReturns404() throws Exception {
        when(paymentRepository.findByReservationID(RESERVATION_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/payment/reservation/{reservationId}", RESERVATION_ID)
                .header("X-User-Id", OWNER.toString()))
            .andExpect(status().isNotFound());
    }
}