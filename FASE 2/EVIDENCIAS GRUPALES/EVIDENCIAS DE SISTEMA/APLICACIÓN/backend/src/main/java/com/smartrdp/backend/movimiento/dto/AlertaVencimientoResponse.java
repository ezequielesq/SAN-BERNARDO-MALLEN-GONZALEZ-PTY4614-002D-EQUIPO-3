package com.smartrdp.backend.movimiento.dto;

import java.time.LocalDate;

public record AlertaVencimientoResponse(
        Long loteId,
        Long productoId,
        String productoNombre,
        String numeroLote,
        Integer cantidadDisponible,
        LocalDate fechaVencimiento,
        boolean vencido
) {}
