package com.runway.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, message = "must be at least 8 characters long") String newPassword) {
}
