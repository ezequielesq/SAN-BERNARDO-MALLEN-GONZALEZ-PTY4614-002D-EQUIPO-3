package com.smartrdp.backend.movimiento.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperacionResponse(
        Long id,
        String tipo,
        String tipoNombre,
        LocalDateTime fecha,
        String usuarioEmail,
        String nota,
        List<Linea> lineas
) {
    public record Linea(Long productoId, String productoNombre, Long loteId, String numeroLote, Integer cantidad) {}
}
