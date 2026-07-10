package org.ticketsouq.apigateway.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ticketsouq.apigateway.dto.*;
import org.ticketsouq.apigateway.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    // ── REGISTRATION ──────────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest req){
        authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // ── AUTHENTICATION ────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return ResponseEntity.ok(authService.refresh(token));
    }

    // ── SESSION MANAGEMENT ────────────────────────────────────────────────────

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal String userId) {
        authService.logoutFromAllDevices(UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }

    // ── EMAIL VERIFICATION ────────────────────────────────────────────────────

    @GetMapping("/email-varification")
    public ResponseEntity<Void> triggerVerificationEmail(@RequestParam String email) {
        authService.triggerVerificationEmail(email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/email-varification")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }

    // ── PASSWORD MANAGEMENT ───────────────────────────────────────────────────

    @GetMapping("/password-forgot")
    public ResponseEntity<Void> triggerPasswordForgot(@RequestParam String email) {
        authService.triggerPasswordReset(email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/password-forgot")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.ok().build();
    }

    // ── PASSWORD CHANGE ───────────────────────────────────────────────────────

    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal String userId, @Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(UUID.fromString(userId), req);
        return ResponseEntity.noContent().build();
    }

    // ── ACCOUNT DEACTIVATION ──────────────────────────────────────────────────

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deactivateAccount(@AuthenticationPrincipal String userId) {
        authService.deactivateAccount(UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }

}
