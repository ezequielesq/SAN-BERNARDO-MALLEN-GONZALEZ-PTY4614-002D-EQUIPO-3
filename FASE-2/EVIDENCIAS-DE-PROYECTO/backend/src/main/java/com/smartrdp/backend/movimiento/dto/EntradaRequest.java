package com.smartrdp.backend.movimiento.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record EntradaRequest(
        @NotNull Long productoId,
        @NotNull @Min(1) Integer cantidad,
        LocalDate fechaVencimiento,
        String numeroLote,
        String motivo
) {}
