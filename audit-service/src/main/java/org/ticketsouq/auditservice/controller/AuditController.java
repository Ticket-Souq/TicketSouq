package org.ticketsouq.auditservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.auditservice.dto.AuditLogResponse;
import org.ticketsouq.auditservice.service.AuditService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

//    @PostMapping
//    public ResponseEntity<Void> create(@Valid @RequestBody CreateAuditLogRequest request) {
//        auditService.produceEvent(request);
//        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
//    }

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> getAll() {
        return ResponseEntity.ok(auditService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(auditService.findById(id));
    }

    @GetMapping(params = "madeById")
    public ResponseEntity<List<AuditLogResponse>> getByMadeById(@RequestParam UUID madeById) {
        return ResponseEntity.ok(auditService.findByMadeById(madeById));
    }

    @GetMapping(params = "action")
    public ResponseEntity<List<AuditLogResponse>> getByAction(@RequestParam String action) {
        return ResponseEntity.ok(auditService.findByAction(action));
    }

    @GetMapping(params = {"from", "to"})
    public ResponseEntity<List<AuditLogResponse>> getByDateRange(
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return ResponseEntity.ok(auditService.findByDateRange(from, to));
    }
}
