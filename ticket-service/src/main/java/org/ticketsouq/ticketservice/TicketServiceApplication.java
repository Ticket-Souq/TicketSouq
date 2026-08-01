package org.ticketsouq.ticketservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(scanBasePackages = "org.ticketsouq")
@AutoConfigurationPackage(basePackages = "org.ticketsouq")
@EnableKafka
@EnableFeignClients(basePackages = "org.ticketsouq.ticketservice.client")
public class TicketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketServiceApplication.class, args);
    }

}
