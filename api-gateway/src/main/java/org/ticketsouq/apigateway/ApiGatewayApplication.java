package org.ticketsouq.apigateway;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "org.ticketsouq")
@EnableFeignClients
@EnableAsync
@EnableScheduling
@Tag(name = "Auth Service", description = "JWT issuance, refresh token rotation, and token revocation. Delegates identity verification to User Service.")
@EnableKafka
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

}
