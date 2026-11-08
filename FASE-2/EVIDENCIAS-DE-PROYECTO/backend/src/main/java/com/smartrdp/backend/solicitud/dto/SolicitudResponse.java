package com.smartrdp.backend.solicitud.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SolicitudResponse(
        Long id,
        String solicitanteNombre,
        String estado,
        String tipo,
        Long solicitudOrigenId,
        LocalDateTime fecha,
        List<DetalleDto> detalles
) {
    /**
     * Una fila por producto. {@code cantidadSolicitada} es 0 en un agregado; {@code cantidadEntregada} es
     * nula hasta que la solicitud se aprueba. {@code cantidadDevuelta} y {@code devolvible} solo se
     * calculan para el propio empleado (nulos en el resto).
     */
    public record DetalleDto(Long productoId, String productoNombre,
                              Integer cantidadSolicitada, Integer cantidadEntregada,
                              Integer cantidadDevuelta, Integer devolvible, boolean agregado) {}
}
