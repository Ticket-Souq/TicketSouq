package org.ticketsouq.eventservice.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ticketsouq.eventservice.service.SeatService;
import org.ticketsouq.eventservice.service.SectionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/private/events")
@RequiredArgsConstructor
@Hidden
public class LockPrivateController {

    private final SeatService seatService;
    private final SectionService sectionService;

    @PostMapping("/seats/lock")
    public ResponseEntity<Void> lockSeats(@RequestBody List<UUID> seatIds) {
        seatService.lockSeats(seatIds);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/seats/unlock")
    public ResponseEntity<Void> unlockSeats(@RequestBody List<UUID> seatIds) {
        seatService.unlockSeats(seatIds);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/sections/{sectionId}/remaining-capacity")
    public ResponseEntity<Void> updateRemainingCapacity(
            @PathVariable UUID sectionId,
            @RequestBody Integer remainingCapacity) {
        sectionService.updateRemainingCapacity(sectionId, remainingCapacity);
        return ResponseEntity.ok().build();
    }
}
