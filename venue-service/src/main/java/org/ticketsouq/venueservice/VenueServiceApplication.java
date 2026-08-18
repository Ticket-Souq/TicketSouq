package org.ticketsouq.venueservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"org.ticketsouq"})
@EnableFeignClients
public class VenueServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VenueServiceApplication.class, args);
    }

}
