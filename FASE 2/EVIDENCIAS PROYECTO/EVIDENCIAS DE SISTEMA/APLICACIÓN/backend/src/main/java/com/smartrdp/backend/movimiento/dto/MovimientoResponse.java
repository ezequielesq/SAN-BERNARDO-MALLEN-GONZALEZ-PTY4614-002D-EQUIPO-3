package com.smartrdp.backend.movimiento.dto;

import java.time.LocalDateTime;

/**
 * Una línea del libro. {@code sentido} es +1 (entra) o -1 (sale); {@code tipo} es el código del tipo de
 * operación y {@code tipoNombre} su nombre legible. {@code corregida}: la operación ya tiene una corrección.
 * {@code esCorreccion}: esta operación corrige a otra.
 */
public record MovimientoResponse(
        Long id,
        Long operacionId,
        Long productoId,
        String productoNombre,
        String tipo,
        String tipoNombre,
        int sentido,
        Integer cantidad,
        Long loteId,
        String numeroLote,
        Integer costoUnitario,
        String nota,
        LocalDateTime fecha,
        String usuarioEmail,
        boolean corregida,
        boolean esCorreccion
) {}
