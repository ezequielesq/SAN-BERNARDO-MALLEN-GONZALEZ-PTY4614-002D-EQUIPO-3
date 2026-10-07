package com.smartrdp.backend.publico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CredencialesRequest(@NotNull Long empleadoId, @NotBlank String password) {}
