package org.ticketsouq.eventservice.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

//@Component
public class Searchbean {


    @Bean
    public Searchbean searchbean() {
        return new Searchbean();
    }

}
