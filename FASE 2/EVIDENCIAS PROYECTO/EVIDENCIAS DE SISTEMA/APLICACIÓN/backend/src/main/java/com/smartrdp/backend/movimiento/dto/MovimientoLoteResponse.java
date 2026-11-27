package com.smartrdp.backend.movimiento.dto;

import java.time.LocalDateTime;

/**
 * Una línea de la trazabilidad de un lote. sentido: +1 entrada, -1 salida. saldoDespues: saldo acumulado del lote
 * tras este movimiento. solicitudId y solicitanteNombre solo existen si la operación nació de un pedido o una
 * devolución.
 */
public record MovimientoLoteResponse(Long movimientoId, Long operacionId, LocalDateTime fecha, String tipo,
        String tipoNombre, int sentido, int cantidad, int saldoDespues, String usuarioEmail, String nota,
        Long solicitudId, String solicitanteNombre) {}
