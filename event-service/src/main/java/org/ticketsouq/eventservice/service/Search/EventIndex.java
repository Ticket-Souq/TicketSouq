package org.ticketsouq.eventservice.service.Search;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.UUID;

@Document(indexName = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventIndex {

    @Id
    private UUID id;

    @Field(type = FieldType.Text) String title;
    @Field(type = FieldType.Text) String category;
    @Field(type = FieldType.Text) String organization;
}
