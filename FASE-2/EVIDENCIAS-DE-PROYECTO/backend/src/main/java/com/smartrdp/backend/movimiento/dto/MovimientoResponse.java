package com.smartrdp.backend.movimiento.dto;

import com.smartrdp.backend.movimiento.TipoMovimiento;

import java.time.LocalDateTime;

public record MovimientoResponse(
        Long id,
        Long productoId,
        String productoNombre,
        TipoMovimiento tipo,
        Integer cantidad,
        String motivo,
        Integer costoUnitario,
        LocalDateTime fecha,
        String usuarioEmail
) {}
