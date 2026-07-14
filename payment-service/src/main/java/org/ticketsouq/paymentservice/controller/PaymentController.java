package org.ticketsouq.paymentservice.controller;


import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.ticketsouq.paymentservice.dto.PaymentRequest;
import org.ticketsouq.paymentservice.dto.PaymentResponse;
import org.ticketsouq.paymentservice.service.PaymentService;

import java.util.UUID;

@RestController
@RequestMapping("api/v1/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    ResponseEntity<PaymentResponse> pay(@Valid @RequestBody PaymentRequest request){
        return paymentService.pay(request);
    }

    @GetMapping("/{paymentId}")
    ResponseEntity<PaymentResponse> getPaymentDetails(@PathVariable UUID paymentId){
        return paymentService.getPayment(paymentId);
    }

    @PostMapping("/{paymentId}/refund")
    ResponseEntity<Void> refund(@PathVariable UUID paymentId){
        paymentService.refund(paymentId);
        return ResponseEntity.noContent().build();
    }

}
