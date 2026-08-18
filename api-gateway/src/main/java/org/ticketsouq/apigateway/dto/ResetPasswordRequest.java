package org.ticketsouq.apigateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits") String otp,
        @NotBlank @Size(min = 8) String newPassword
) {}
