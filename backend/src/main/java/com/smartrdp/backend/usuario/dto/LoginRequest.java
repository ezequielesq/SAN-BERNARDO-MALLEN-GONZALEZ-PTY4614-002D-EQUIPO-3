package com.smartrdp.backend.usuario.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
