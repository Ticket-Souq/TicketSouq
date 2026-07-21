package org.ticketsouq.eventservice.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.sharedmodule.EventService.dto.ReservationRequest;
import org.ticketsouq.eventservice.service.LockService;
import org.ticketsouq.sharedmodule.EventService.dto.*;
import org.ticketsouq.sharedmodule.ReservationService.dto.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/locks")
@RequiredArgsConstructor
public class LocksController {

    private final LockService lockService;

    @PostMapping("/{eventId}/seats")
    public ResponseEntity<LockSeatsResponse> lockSeats(
        @PathVariable UUID eventId,
        @Valid @RequestBody LockSeatsRequest request) {
        LockSeatsResponse response = lockService.acquireSeatLocks(eventId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{eventId}/zones")
    public ResponseEntity<LockZoneResponse> lockZone(
        @PathVariable UUID eventId,
        @Valid @RequestBody LockZoneRequest request) {
        LockZoneResponse response = lockService.acquireZoneLock(eventId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reserve")
    public ResponseEntity<Void> reserve(@Valid @RequestBody ReservationRequest request , @RequestHeader("X-User-Id") UUID userId) {
        lockService.reserve(request,userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<ConfirmResponse> confirm(@Valid @RequestBody ConfirmRequest request) {
        ConfirmResponse response = lockService.confirm(request.reservationId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/release")
    public ResponseEntity<ReleaseResponse> release(@Valid @RequestBody ReleaseRequest request) {
        ReleaseResponse response = lockService.release(request.reservationId());
        return ResponseEntity.ok(response);
    }
}
