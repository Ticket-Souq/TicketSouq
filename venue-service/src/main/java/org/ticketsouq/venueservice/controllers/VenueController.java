package org.ticketsouq.venueservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.sharedmodule.utils.UUIDUtils;
import org.ticketsouq.venueservice.dto.CreateVenueRequest;
import org.ticketsouq.venueservice.dto.UpdateVenueRequest;
import org.ticketsouq.venueservice.dto.VenueResponse;
import org.ticketsouq.venueservice.services.VenueService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/venue")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @PostMapping
    public ResponseEntity<VenueResponse> create(@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody CreateVenueRequest request) {
        VenueResponse response = venueService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VenueResponse> getById(@PathVariable String id) {
        UUID uuid = UUIDUtils.parse(id);
        VenueResponse response = venueService.getById(uuid);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<VenueResponse>> listByOrganization(@RequestHeader("X-User-Id") UUID userId, Pageable pageable) {
        Page<VenueResponse> page = venueService.listByOrganization(userId, pageable);
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
