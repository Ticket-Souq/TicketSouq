package org.ticketsouq.paymentservice.service;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.ticketsouq.paymentservice.dto.PaymentRequest;
import org.ticketsouq.paymentservice.dto.PaymentResponse;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.paymentProviders.PaymentProvider;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentSuccessEvent;

import java.util.UUID;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.PAYMENT_SUCCESS;

@Service
@AllArgsConstructor
public class PaymentService {

    private final PaymentProvider paymentProvider;

    private final KafkaTemplate<String, Object> kafkaTemplate;


    public ResponseEntity<PaymentResponse> pay(PaymentRequest request){
       PaymentResponse paymentResponse = paymentProvider.pay(request);
       if(paymentResponse.paymentStatus() == PaymentStatus.SUCCESS){
           PaymentSuccessEvent paymentSuccessEvent = new PaymentSuccessEvent(UUID.randomUUID(), request.customerID(), request.reservationID(), request.amount());
           kafkaTemplate.send(PAYMENT_SUCCESS, request.customerID().toString(), paymentSuccessEvent);
       }
       return ResponseEntity.ok(paymentResponse);
    }

    public ResponseEntity<PaymentResponse> getPayment(UUID paymentId){
        PaymentResponse paymentResponse = paymentProvider.getPayment(paymentId);
        return ResponseEntity.ok(paymentResponse);
    }

    public void refund(UUID paymentId){
        paymentProvider.refund(paymentId);
    }
}
