package com.smartrdp.backend.movimiento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    /** Líneas del libro entre dos fechas, la más reciente primero; filtros opcionales por producto y categoría. */
    @Query("""
        SELECT m FROM Movimiento m
        JOIN FETCH m.operacion o JOIN FETCH o.tipo JOIN FETCH o.usuario
        JOIN FETCH m.lote l JOIN FETCH l.producto p
        WHERE o.createdAt BETWEEN :desde AND :hasta
        AND (:productoId IS NULL OR p.id = :productoId)
        AND (:categoriaId IS NULL OR p.categoria.id = :categoriaId)
        ORDER BY o.createdAt DESC, m.id DESC
        """)
    List<Movimiento> buscar(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                            @Param("productoId") Long productoId, @Param("categoriaId") Long categoriaId);

    /**
     * Líneas del libro para exportar: rango, categoría, productos, persona (solo operaciones de solicitudes) y
     * proveedor del lote.
     */
    @Query("""
        SELECT m FROM Movimiento m
        JOIN FETCH m.operacion o JOIN FETCH o.tipo JOIN FETCH o.usuario
        JOIN FETCH m.lote l JOIN FETCH l.producto p
        LEFT JOIN o.solicitud s LEFT JOIN s.solicitante u LEFT JOIN l.proveedor pr
        WHERE o.createdAt >= :desde AND o.createdAt < :hasta
        AND (:categoriaId IS NULL OR p.categoria.id = :categoriaId)
        AND (:todosLosProductos = true OR p.id IN :productoIds)
        AND (:solicitanteId IS NULL OR u.id = :solicitanteId)
        AND (:proveedorId IS NULL OR pr.id = :proveedorId)
        ORDER BY o.createdAt DESC, m.id DESC
        """)
    List<Movimiento> buscarParaExportar(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                        @Param("categoriaId") Long categoriaId,
                                        @Param("todosLosProductos") boolean todosLosProductos,
                                        @Param("productoIds") java.util.Collection<Long> productoIds,
                                        @Param("solicitanteId") Long solicitanteId,
                                        @Param("proveedorId") Long proveedorId);
}
