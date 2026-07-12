package org.ticketsouq.venueservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"org.ticketsouq"})
public class VenueServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VenueServiceApplication.class, args);
    }

}
