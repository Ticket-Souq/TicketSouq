package org.ticketsouq.eventservice.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.ticketsouq.eventservice.service.Search.EventIndex;

import java.util.UUID;

public interface ElasticsearchEventRepository extends ElasticsearchRepository<EventIndex, UUID> {

}
