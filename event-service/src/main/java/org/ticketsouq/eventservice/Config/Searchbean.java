package org.ticketsouq.eventservice.Config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.ticketsouq.eventservice.service.Search.ESSearchService;
import org.ticketsouq.eventservice.service.Search.PostgresSearchService;
import org.ticketsouq.eventservice.service.Search.SearchService;

@Component
public class Searchbean {

    @Bean
    @ConditionalOnProperty(name = "search.engine", havingValue = "ES")
    public SearchService SearchProvider(ESSearchService esSearchService) {
        return esSearchService;
    }

    @Bean
    @ConditionalOnProperty(name = "search.engine", havingValue = "PG", matchIfMissing = true)
    public SearchService SearchProvider(PostgresSearchService postgresSearchService) {
        return postgresSearchService;
    }

}
