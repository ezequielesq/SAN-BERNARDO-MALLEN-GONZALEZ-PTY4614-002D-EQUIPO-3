package com.smartrdp.backend.solicitud.dto;

import com.smartrdp.backend.solicitud.EstadoSolicitud;

import java.time.LocalDateTime;
import java.util.List;

public record SolicitudResponse(
        Long id,
        String solicitanteNombre,
        EstadoSolicitud estado,
        LocalDateTime fecha,
        List<DetalleDto> detalles
) {
    public record DetalleDto(Long productoId, String productoNombre,
                              Integer cantidadSolicitada, Integer cantidadEntregada) {}
}
