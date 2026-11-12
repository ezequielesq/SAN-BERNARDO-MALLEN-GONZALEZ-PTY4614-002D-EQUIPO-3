package com.smartrdp.backend.movimiento.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Ingreso de stock: una operación (tipo seleccionable de sentido +1) con una línea por lote nuevo. */
public record EntradaRequest(
        @NotBlank String tipo,
        @Size(max = 300) String nota,
        @NotEmpty @Size(max = 200) List<@NotNull @Valid Linea> lineas
) {
    public record Linea(
            @NotNull Long productoId,
            @NotNull @Min(1) @Max(1_000_000) Integer cantidad,
            LocalDate fechaVencimiento,
            @Size(max = 50) String numeroLote,
            @Min(0) Integer costoUnitario
    ) {}
}
