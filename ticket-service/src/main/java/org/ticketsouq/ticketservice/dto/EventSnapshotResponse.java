package org.ticketsouq.ticketservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventSnapshotResponse(
    UUID id,
    String title,
    String description,
    UUID venueTemplateId,
    String organization,
    String status,
    String categoryName,
    String posterUrl,
    Instant startDate,
    Instant finishDate
) {}
