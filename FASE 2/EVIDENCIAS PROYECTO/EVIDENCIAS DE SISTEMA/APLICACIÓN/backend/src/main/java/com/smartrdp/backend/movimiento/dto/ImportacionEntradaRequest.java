package com.smartrdp.backend.movimiento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Carga de entradas desde Excel: una sola operación (tipo y nota) con un lote nuevo por fila. */
public record ImportacionEntradaRequest(
        @NotBlank String tipo,
        @Size(max = 300) String nota,
        @NotEmpty @Size(max = 200) List<@NotNull FilaEntrada> filas
) {}
