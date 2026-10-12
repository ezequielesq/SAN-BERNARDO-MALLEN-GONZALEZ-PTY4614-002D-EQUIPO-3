package com.smartrdp.backend.movimiento.dto;

import com.smartrdp.backend.movimiento.EstadoStock;

import java.time.LocalDate;

public record StockStatusResponse(
        Long productoId,
        String productoNombre,
        Integer stockActual,
        EstadoStock estado,
        LocalDate fechaVencimientoProximo,
        Integer costoUnitario,
        Integer valorTotal
) {}
