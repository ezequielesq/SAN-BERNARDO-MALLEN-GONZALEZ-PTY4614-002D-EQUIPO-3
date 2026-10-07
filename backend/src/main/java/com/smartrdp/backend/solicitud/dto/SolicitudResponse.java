package com.smartrdp.backend.solicitud.dto;

import com.smartrdp.backend.solicitud.EstadoSolicitud;
import com.smartrdp.backend.solicitud.TipoSolicitud;

import java.time.LocalDateTime;
import java.util.List;

public record SolicitudResponse(
        Long id,
        String solicitanteNombre,
        EstadoSolicitud estado,
        TipoSolicitud tipo,
        Long solicitudOrigenId,
        LocalDateTime fecha,
        List<DetalleDto> detalles
) {
    /** `cantidadDevuelta` y `devolvible` solo se calculan para el propio empleado (null en el resto). */
    public record DetalleDto(Long detalleId, Long productoId, String productoNombre,
                              Integer cantidadSolicitada, Integer cantidadEntregada,
                              Integer cantidadDevuelta, Integer devolvible) {}
}
