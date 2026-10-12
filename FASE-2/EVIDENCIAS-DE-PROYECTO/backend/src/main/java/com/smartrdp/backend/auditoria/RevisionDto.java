package com.smartrdp.backend.auditoria;

import java.time.LocalDateTime;

public record RevisionDto(
        Long solicitudId,
        LocalDateTime fecha,
        String usuarioEmail,
        String tipoRevision,
        String estadoSolicitud,
        String tipoSolicitud
) {}
