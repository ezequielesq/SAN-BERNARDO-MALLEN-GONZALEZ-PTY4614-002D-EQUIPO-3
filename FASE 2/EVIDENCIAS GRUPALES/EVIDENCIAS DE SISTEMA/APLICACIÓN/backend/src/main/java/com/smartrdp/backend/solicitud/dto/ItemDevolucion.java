package com.smartrdp.backend.solicitud.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ItemDevolucion(@NotNull Long productoId, @NotNull @Min(1) Integer cantidad) {}
