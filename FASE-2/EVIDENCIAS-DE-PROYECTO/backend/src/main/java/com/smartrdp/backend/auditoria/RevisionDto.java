package com.smartrdp.backend.auditoria;

import java.time.LocalDateTime;
import java.util.List;

public record RevisionDto(
        Long solicitudId,
        LocalDateTime fecha,
        String usuarioEmail,
        String tipoRevision,
        String estadoSolicitud,
        String tipoSolicitud,
        List<LineaRevisionDto> lineas
) {

    /** Lo pedido y lo entregado de un producto de la solicitud (nulos como en la vista solicitado_vs_entregado). */
    public record LineaRevisionDto(
            Long productoId,
            String productoNombre,
            Integer cantidadSolicitada,
            Integer cantidadEntregada,
            boolean agregado
    ) {}
}
