package org.ticketsouq.eventservice.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record EventCardResponse(

    UUID id,

    String title,

    String posterUrl,

    Instant startDate

) {}
