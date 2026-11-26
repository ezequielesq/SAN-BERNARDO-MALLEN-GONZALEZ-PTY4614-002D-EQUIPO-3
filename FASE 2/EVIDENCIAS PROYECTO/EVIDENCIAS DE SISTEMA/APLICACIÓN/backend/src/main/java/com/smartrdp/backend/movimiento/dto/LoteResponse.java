package com.smartrdp.backend.movimiento.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un lote con su saldo y su estado derivados (nada de esto se guarda). estado: AGOTADO | VENCIDO | POR_VENCER |
 * VIGENTE. cantidadInicial: lo que entró por operaciones manuales de entrada. valorEnStock: saldo por costo del
 * lote, nulo si el lote no tiene costo. diasParaVencer es negativo si ya venció y nulo si no vence.
 */
public record LoteResponse(Long id, Long productoId, String productoNombre, String categoriaNombre,
        String numeroLote, Long proveedorId, String proveedorNombre, LocalDateTime fechaIngreso,
        LocalDate fechaVencimiento, Integer diasParaVencer, Integer costoUnitario,
        int cantidadInicial, int saldo, Long valorEnStock, String estado) {}
