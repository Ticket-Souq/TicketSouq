package org.ticketsouq.eventservice.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.ticketsouq.eventservice.service.Search.ESSearchService;
import org.ticketsouq.eventservice.service.Search.PostgresSearchService;
import org.ticketsouq.eventservice.service.Search.SearchService;

//@Component
public class Searchbean {

    @Bean
    public SearchService eventSearchService(ESSearchService esSearchService) {
        return esSearchService;
    }

//    @Bean
//    public SearchService eventSearchService(PostgresSearchService esSearchService) {
//        return esSearchService;
//    }

}
