package org.ticketsouq.venueservice.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;
import org.ticketsouq.venueservice.dto.CreateVenueRequest;
import org.ticketsouq.venueservice.dto.UpdateVenueRequest;
import org.ticketsouq.venueservice.dto.VenueMapper;
import org.ticketsouq.venueservice.dto.VenueResponse;
import org.ticketsouq.venueservice.model.Venue;
import org.ticketsouq.venueservice.repos.VenueRepository;

import java.util.UUID;

@Service
@Transactional
public class VenueService {

    private final VenueRepository venueRepository;
    private final VenueMapper venueMapper;

    public VenueService(VenueRepository venueRepository, VenueMapper venueMapper) {
        this.venueRepository = venueRepository;
        this.venueMapper = venueMapper;
    }

    public VenueResponse create(CreateVenueRequest request) {
        Venue venue = venueMapper.toEntity(request);
        Venue saved = venueRepository.save(venue);
        return venueMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public VenueResponse getById(UUID id) {
        return venueRepository.findById(id)
            .map(venueMapper::toResponse)
            .orElseThrow(() -> new BusinessException("Venue not found: " + id, HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<VenueResponse> listByOrg(UUID orgId, Pageable pageable) {
        return venueRepository.findByOrgId(orgId, pageable)
            .map(venueMapper::toResponse);
    }

    public VenueResponse update(UUID id, UpdateVenueRequest request) {
        Venue venue = venueRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Venue not found: " + id, HttpStatus.NOT_FOUND));
        venueMapper.updateEntity(request, venue);
        Venue saved = venueRepository.save(venue);
        return venueMapper.toResponse(saved);
    }

    public void delete(UUID id) {
        Venue venue = venueRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Venue not found: " + id, HttpStatus.NOT_FOUND));
        venue.setDeleted(true);
        venueRepository.save(venue);
    }
}
