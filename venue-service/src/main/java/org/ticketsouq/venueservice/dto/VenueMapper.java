package org.ticketsouq.venueservice.dto;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.ticketsouq.venueservice.model.Venue;

@Mapper(componentModel = "spring")
public interface VenueMapper {

    Venue toEntity(CreateVenueRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateVenueRequest request, @MappingTarget Venue venue);

    VenueResponse toResponse(Venue venue);
}
