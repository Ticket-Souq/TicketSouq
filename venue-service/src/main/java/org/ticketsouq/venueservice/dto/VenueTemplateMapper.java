package org.ticketsouq.venueservice.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.ticketsouq.venueservice.model.Venue;
import org.ticketsouq.venueservice.model.VenueTemplate;

@Mapper(componentModel = "spring")
public interface VenueTemplateMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "venue", source = "venue")
    VenueTemplate toEntity(CreateVenueTemplateRequest request, Venue venue);

    VenueTemplateResponse toResponse(VenueTemplate template);
}
