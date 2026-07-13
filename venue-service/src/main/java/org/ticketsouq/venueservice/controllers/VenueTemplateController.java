package org.ticketsouq.venueservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ticketsouq.sharedmodule.utils.UUIDUtils;
import org.ticketsouq.venueservice.dto.CreateVenueTemplateRequest;
import org.ticketsouq.venueservice.dto.VenueTemplateResponse;
import org.ticketsouq.venueservice.services.VenueTemplateService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/venue/{venueId}/templates")
//@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class VenueTemplateController {

    private final VenueTemplateService templateService;

    @GetMapping
    public ResponseEntity<List<VenueTemplateResponse>> listTemplates(@PathVariable String venueId) {
        UUID uuid = UUIDUtils.parse(venueId);
        List<VenueTemplateResponse> templates = templateService.listByVenue(uuid);
        return ResponseEntity.ok(templates);
    }

    @PostMapping
    public ResponseEntity<VenueTemplateResponse> createTemplate(@PathVariable String venueId,
                                                                  @Valid @RequestBody CreateVenueTemplateRequest request) {
        UUID uuid = UUIDUtils.parse(venueId);
        VenueTemplateResponse response = templateService.create(uuid, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<VenueTemplateResponse> getTemplate(@PathVariable String venueId,
                                                              @PathVariable String templateId) {
        UUID venueUuid = UUIDUtils.parse(venueId);
        UUID templateUuid = UUIDUtils.parse(templateId);
        VenueTemplateResponse response = templateService.getById(venueUuid, templateUuid);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String venueId,
                                                @PathVariable String templateId) {
        UUID venueUuid = UUIDUtils.parse(venueId);
        UUID templateUuid = UUIDUtils.parse(templateId);
        templateService.delete(venueUuid, templateUuid);
        return ResponseEntity.noContent().build();
    }
}
