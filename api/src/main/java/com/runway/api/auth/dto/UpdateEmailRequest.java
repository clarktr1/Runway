package com.runway.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateEmailRequest(@NotBlank String currentPassword, @NotBlank @Email String email) {
}
