package org.ticketsouq.venueservice.controllers;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ticketsouq.sharedmodule.utils.UUIDUtils;
import org.ticketsouq.venueservice.dto.CreateVenueRequest;
import org.ticketsouq.venueservice.dto.UpdateVenueRequest;
import org.ticketsouq.venueservice.dto.VenueResponse;
import org.ticketsouq.venueservice.services.VenueService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/venue")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    public ResponseEntity<VenueResponse> create(@Valid @RequestBody CreateVenueRequest request) {
        VenueResponse response = venueService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VenueResponse> getById(@PathVariable String id) {
        UUID uuid = UUIDUtils.parse(id);
        VenueResponse response = venueService.getById(uuid);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<VenueResponse>> listByOrg(@RequestParam UUID orgId, Pageable pageable) {
        Page<VenueResponse> page = venueService.listByOrg(orgId, pageable);
        return ResponseEntity.ok(page);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VenueResponse> update(@PathVariable String id,
                                                 @Valid @RequestBody UpdateVenueRequest request) {
        UUID uuid = UUIDUtils.parse(id);
        VenueResponse response = venueService.update(uuid, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        UUID uuid = UUIDUtils.parse(id);
        venueService.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
