package org.ticketsouq.venueservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;
import org.ticketsouq.venueservice.client.UserServiceClient;
import org.ticketsouq.venueservice.dto.CreateVenueRequest;
import org.ticketsouq.venueservice.dto.UpdateVenueRequest;
import org.ticketsouq.venueservice.dto.VenueMapper;
import org.ticketsouq.venueservice.dto.VenueResponse;
import org.ticketsouq.venueservice.model.Venue;
import org.ticketsouq.venueservice.repos.VenueRepository;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;
    private final VenueMapper venueMapper;
    private final UserServiceClient userServiceClient;

    public VenueResponse create(UUID userId, CreateVenueRequest request) {
        String organization = userServiceClient.getOrganizationNameByUserId(userId);
        if (organization == null || organization.isBlank()) {
            throw new BusinessException("User does not belong to an organization", HttpStatus.BAD_REQUEST);
        }

        Venue venue = venueMapper.toEntity(request);
        venue.setOrganization(organization);
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
    public Page<VenueResponse> listByOrganization(UUID userId, Pageable pageable) {
        String organization = userServiceClient.getOrganizationNameByUserId(userId);
        if (organization == null || organization.isBlank()) {
            throw new BusinessException("User does not belong to an organization", HttpStatus.BAD_REQUEST);
        }
        return venueRepository.findByOrganization(organization, pageable)
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
