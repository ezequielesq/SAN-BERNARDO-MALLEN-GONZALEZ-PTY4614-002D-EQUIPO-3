package com.smartrdp.backend.analitica;

import java.time.LocalDateTime;

/**
 * Una línea del libro con lo que el panel necesita: cuándo, de qué tipo, de quién (si nace de una solicitud),
 * qué producto y unidad, cuánto y a qué costo de lote, y a qué proveedor se compró ese lote (nulo en los lotes sin
 * proveedor). Los componentes son objetos (no primitivos) porque la consulta JPQL los construye con una expresión
 * «new».
 */
public record LineaPanel(Long operacionId, LocalDateTime fecha, String tipo, Long solicitudId,
                         Long solicitanteId, String solicitanteNombre, Long productoId, String productoNombre,
                         Long unidadId, String unidadNombre, Integer cantidad, Integer costo,
                         Long proveedorId, String proveedorNombre) {

    /** Cantidad por costo del lote; 0 si el lote no tiene costo (se cuenta aparte como «sin costo»). */
    public long valor() {
        return costo == null ? 0L : (long) costo * cantidad;
    }
}
