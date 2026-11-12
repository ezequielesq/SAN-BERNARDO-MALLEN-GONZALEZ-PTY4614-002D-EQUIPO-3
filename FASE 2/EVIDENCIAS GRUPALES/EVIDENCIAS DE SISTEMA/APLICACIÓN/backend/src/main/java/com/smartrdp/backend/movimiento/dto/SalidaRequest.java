package com.smartrdp.backend.movimiento.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Salida manual de stock: una operación (tipo seleccionable de sentido -1); los lotes se eligen por FEFO. */
public record SalidaRequest(
        @NotBlank String tipo,
        @Size(max = 300) String nota,
        @NotEmpty @Size(max = 200) List<@NotNull @Valid Linea> lineas
) {
    public record Linea(@NotNull Long productoId, @NotNull @Min(1) @Max(1_000_000) Integer cantidad) {}
}
