package com.smartrdp.backend.analitica;

import com.smartrdp.backend.movimiento.Movimiento;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** Lectura de líneas del libro para el panel. Las correcciones y las operaciones corregidas no cuentan. */
public interface PanelRepository extends Repository<Movimiento, Long> {

    @Query("""
        SELECT new com.smartrdp.backend.analitica.LineaPanel(
            o.id, o.createdAt, t.codigo, s.id, u.id, u.nombre,
            p.id, p.nombre, um.id, um.nombre, m.cantidad, l.costoUnitario, pr.id, pr.nombre)
        FROM Movimiento m
        JOIN m.operacion o JOIN o.tipo t JOIN m.lote l JOIN l.producto p JOIN p.unidadMedida um
        LEFT JOIN o.solicitud s LEFT JOIN s.solicitante u LEFT JOIN l.proveedor pr
        WHERE o.createdAt >= :desde AND o.createdAt < :hasta
        AND t.codigo IN :tipos
        AND (:categoriaId IS NULL OR p.categoria.id = :categoriaId)
        AND (:todosLosProductos = true OR p.id IN :productoIds)
        AND (:solicitanteId IS NULL OR u.id = :solicitanteId)
        AND (:proveedorId IS NULL OR pr.id = :proveedorId)
        AND NOT EXISTS (SELECT 1 FROM Operacion c WHERE c.operacionCorregida = o)
        ORDER BY o.createdAt, m.id
        """)
    List<LineaPanel> lineas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                            @Param("tipos") Collection<String> tipos, @Param("categoriaId") Long categoriaId,
                            @Param("todosLosProductos") boolean todosLosProductos,
                            @Param("productoIds") Collection<Long> productoIds,
                            @Param("solicitanteId") Long solicitanteId,
                            @Param("proveedorId") Long proveedorId);
}
