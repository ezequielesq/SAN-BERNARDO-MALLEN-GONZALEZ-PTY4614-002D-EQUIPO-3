package com.smartrdp.backend.solicitud;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DetalleSolicitudRepository extends JpaRepository<DetalleSolicitud, Long> {

    @Query("""
        SELECT COALESCE(SUM(d.cantidadEntregada), 0L)
        FROM DetalleSolicitud d
        WHERE d.detalleOrigen.id = :origenId AND d.solicitud.estado = 'APROBADA'
        """)
    Long sumDevueltoAprobado(Long origenId);

    @Query("""
        SELECT COALESCE(SUM(d.cantidadSolicitada), 0L)
        FROM DetalleSolicitud d
        WHERE d.detalleOrigen.id = :origenId AND d.solicitud.estado = 'PENDIENTE'
        """)
    Long sumDevueltoPendiente(Long origenId);
}
