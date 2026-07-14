package org.ticketsouq.eventservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
//import org.springframework.data.elasticsearch.annotations.Document;
//import org.springframework.data.elasticsearch.annotations.Field;
//import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.UUID;

//@Document(indexName = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventIndex {

    @Id
    private UUID id;

//    @Field(type = FieldType.Text)
//    private String title;
//
//    @Field(type = FieldType.Text)
//    private String description;

    private UUID venueId;

    private UUID organizationId;

    private UUID createdBy;

    private String posterUrl;

    private String status;

    private String bookingModel;

    private Instant startDate;

    private Instant finishDate;
}
