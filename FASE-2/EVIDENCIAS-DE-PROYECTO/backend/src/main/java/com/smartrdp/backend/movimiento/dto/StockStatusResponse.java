package com.smartrdp.backend.movimiento.dto;

import com.smartrdp.backend.movimiento.EstadoStock;

import java.time.LocalDate;

/** costoUnitario: costo de referencia del producto. valorTotal: stock valorizado con el costo de cada lote. */
public record StockStatusResponse(
        Long productoId,
        String productoNombre,
        Integer stockActual,
        EstadoStock estado,
        LocalDate fechaVencimientoProximo,
        Integer costoUnitario,
        Long valorTotal
) {}
