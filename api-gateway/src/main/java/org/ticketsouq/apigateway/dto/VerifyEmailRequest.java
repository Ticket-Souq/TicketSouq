package org.ticketsouq.apigateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits") String otp
) {}
