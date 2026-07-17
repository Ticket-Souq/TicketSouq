package org.ticketsouq.eventservice.Controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.eventservice.dto.*;
import org.ticketsouq.eventservice.service.LockService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/private/events")
@RequiredArgsConstructor
@Hidden
public class LockPrivateController {

    private final LockService lockService;

    @PostMapping("/{eventId}/locks/seats")
    public ResponseEntity<LockSeatsResponse> lockSeats(
        @PathVariable UUID eventId,
        @Valid @RequestBody LockSeatsRequest request) {
        LockSeatsResponse response = lockService.acquireSeatLocks(eventId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{eventId}/locks/zones")
    public ResponseEntity<LockZoneResponse> lockZone(
        @PathVariable UUID eventId,
        @Valid @RequestBody LockZoneRequest request) {
        LockZoneResponse response = lockService.acquireZoneLock(eventId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{eventId}/confirm")
    public ResponseEntity<ConfirmResponse> confirm(
        @PathVariable UUID eventId,
        @Valid @RequestBody ConfirmRequest request) {
        ConfirmResponse response = lockService.confirm(request.reservationId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{eventId}/release")
    public ResponseEntity<ReleaseResponse> release(
        @PathVariable UUID eventId,
        @Valid @RequestBody ReleaseRequest request) {
        ReleaseResponse response = lockService.release(request.reservationId());
        return ResponseEntity.ok(response);
    }
}
