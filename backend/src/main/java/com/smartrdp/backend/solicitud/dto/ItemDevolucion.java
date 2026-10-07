package com.smartrdp.backend.solicitud.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ItemDevolucion(@NotNull Long detalleId, @NotNull @Min(1) Integer cantidad) {}
