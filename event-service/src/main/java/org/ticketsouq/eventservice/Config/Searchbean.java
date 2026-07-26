package org.ticketsouq.eventservice.Config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;
import org.ticketsouq.eventservice.repository.ElasticsearchEventRepository;
import org.ticketsouq.eventservice.repository.EventRepository;
import org.ticketsouq.eventservice.service.Search.ESSearchService;
import org.ticketsouq.eventservice.service.Search.PostgresSearchService;
import org.ticketsouq.eventservice.service.Search.SearchService;

@Component
public class Searchbean {

    @Bean
    @ConditionalOnProperty(name = "search.engine", havingValue = "ES", matchIfMissing = true)
    public SearchService esSearchProvider(ElasticsearchOperations elasticsearchOperations, ElasticsearchEventRepository elasticsearchEventRepository, EventRepository eventRepository) {
        return new ESSearchService(elasticsearchOperations,elasticsearchEventRepository,eventRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "search.engine", havingValue = "PG")
    public SearchService postgresSearchProvider(EventRepository eventRepository) {
        return new PostgresSearchService(eventRepository);
    }

}
