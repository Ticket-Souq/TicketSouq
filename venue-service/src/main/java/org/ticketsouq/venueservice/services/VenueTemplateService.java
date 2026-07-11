package org.ticketsouq.venueservice.services;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;
import org.ticketsouq.venueservice.dto.CreateVenueTemplateRequest;
import org.ticketsouq.venueservice.dto.VenueTemplateMapper;
import org.ticketsouq.venueservice.dto.VenueTemplateResponse;
import org.ticketsouq.venueservice.model.Venue;
import org.ticketsouq.venueservice.model.VenueTemplate;
import org.ticketsouq.venueservice.repos.VenueRepository;
import org.ticketsouq.venueservice.repos.VenueTemplateRepository;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class VenueTemplateService {

    private final VenueTemplateRepository templateRepository;
    private final VenueRepository venueRepository;
    private final VenueTemplateMapper templateMapper;

    public VenueTemplateService(VenueTemplateRepository templateRepository,
                                VenueRepository venueRepository,
                                VenueTemplateMapper templateMapper) {
        this.templateRepository = templateRepository;
        this.venueRepository = venueRepository;
        this.templateMapper = templateMapper;
    }

    @Transactional(readOnly = true)
    public List<VenueTemplateResponse> listByVenue(UUID venueId) {
        if (!venueRepository.existsById(venueId)) {
            throw new BusinessException("Venue not found: " + venueId, HttpStatus.NOT_FOUND);
        }
        return templateRepository.findByVenueId(venueId).stream()
                .map(templateMapper::toResponse)
                .toList();
    }

    public VenueTemplateResponse create(UUID venueId, CreateVenueTemplateRequest request) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new BusinessException("Venue not found: " + venueId, HttpStatus.NOT_FOUND));
        VenueTemplate template = templateMapper.toEntity(request, venue);
        VenueTemplate saved = templateRepository.save(template);
        return templateMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public VenueTemplateResponse getById(UUID venueId, UUID templateId) {
        if (!venueRepository.existsById(venueId)) {
            throw new BusinessException("Venue not found: " + venueId, HttpStatus.NOT_FOUND);
        }
        return templateRepository.findById(templateId)
                .map(templateMapper::toResponse)
                .orElseThrow(() -> new BusinessException("VenueTemplate not found: " + templateId, HttpStatus.NOT_FOUND));
    }

    public void delete(UUID venueId, UUID templateId) {
        if (!venueRepository.existsById(venueId)) {
            throw new BusinessException("Venue not found: " + venueId, HttpStatus.NOT_FOUND);
        }
        VenueTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("VenueTemplate not found: " + templateId, HttpStatus.NOT_FOUND));
        template.setDeleted(true);
        templateRepository.save(template);
    }
}
