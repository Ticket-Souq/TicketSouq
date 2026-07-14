package org.ticketsouq.eventservice.repository;

import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.ticketsouq.eventservice.service.Search.EventIndex;

import java.util.List;
import java.util.UUID;

public interface ElasticsearchEventRepository extends ElasticsearchRepository<EventIndex, UUID> {

    @Query("{\"match\": {\"title\": {\"query\": \"?0\", \"fuzziness\": \"AUTO\"}}}")
    List<EventIndex> findByTitleFuzzy(String title);

}
