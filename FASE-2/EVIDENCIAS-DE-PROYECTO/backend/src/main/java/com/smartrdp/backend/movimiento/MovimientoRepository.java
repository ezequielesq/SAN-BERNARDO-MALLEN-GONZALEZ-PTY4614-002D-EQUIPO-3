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

    @Query("""
        SELECT p.id, p.nombre, p.categoria.nombre, SUM(m.cantidad)
        FROM Movimiento m JOIN m.operacion o JOIN o.tipo t JOIN m.lote l JOIN l.producto p
        WHERE t.sentido = -1
        AND o.createdAt BETWEEN :desde AND :hasta
        AND (:categoriaId IS NULL OR p.categoria.id = :categoriaId)
        AND t.codigo NOT IN ('CORRECCION_AUMENTA', 'CORRECCION_DISMINUYE')
        AND NOT EXISTS (SELECT 1 FROM Operacion c WHERE c.operacionCorregida = o)
        GROUP BY p.id, p.nombre, p.categoria.nombre
        ORDER BY SUM(m.cantidad) DESC
        """)
    List<Object[]> findConsumoEntreFechas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                          @Param("categoriaId") Long categoriaId);

    @Query("""
        SELECT p.id, p.nombre,
               SUM(CASE WHEN t.sentido = 1 THEN m.cantidad ELSE 0 END),
               SUM(CASE WHEN t.sentido = 1 AND l.costoUnitario IS NOT NULL THEN CAST(l.costoUnitario AS Long) * m.cantidad ELSE 0 END),
               SUM(CASE WHEN t.sentido = 1 AND l.costoUnitario IS NOT NULL THEN 1 ELSE 0 END),
               SUM(CASE WHEN t.sentido = -1 THEN m.cantidad ELSE 0 END),
               SUM(CASE WHEN t.sentido = -1 AND l.costoUnitario IS NOT NULL THEN CAST(l.costoUnitario AS Long) * m.cantidad ELSE 0 END),
               SUM(CASE WHEN t.sentido = -1 AND l.costoUnitario IS NOT NULL THEN 1 ELSE 0 END)
        FROM Movimiento m JOIN m.operacion o JOIN o.tipo t JOIN m.lote l JOIN l.producto p
        WHERE o.createdAt BETWEEN :desde AND :hasta
        AND (:categoriaId IS NULL OR p.categoria.id = :categoriaId)
        AND t.codigo NOT IN ('CORRECCION_AUMENTA', 'CORRECCION_DISMINUYE')
        AND NOT EXISTS (SELECT 1 FROM Operacion c WHERE c.operacionCorregida = o)
        GROUP BY p.id, p.nombre
        """)
    List<Object[]> findConsumoPorProducto(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                          @Param("categoriaId") Long categoriaId);

    @Query("""
        SELECT p.categoria.id, p.categoria.nombre,
               SUM(CASE WHEN t.sentido = 1 THEN m.cantidad ELSE 0 END),
               SUM(CASE WHEN t.sentido = 1 AND l.costoUnitario IS NOT NULL THEN CAST(l.costoUnitario AS Long) * m.cantidad ELSE 0 END),
               SUM(CASE WHEN t.sentido = 1 AND l.costoUnitario IS NOT NULL THEN 1 ELSE 0 END),
               SUM(CASE WHEN t.sentido = -1 THEN m.cantidad ELSE 0 END),
               SUM(CASE WHEN t.sentido = -1 AND l.costoUnitario IS NOT NULL THEN CAST(l.costoUnitario AS Long) * m.cantidad ELSE 0 END),
               SUM(CASE WHEN t.sentido = -1 AND l.costoUnitario IS NOT NULL THEN 1 ELSE 0 END)
        FROM Movimiento m JOIN m.operacion o JOIN o.tipo t JOIN m.lote l JOIN l.producto p
        WHERE o.createdAt BETWEEN :desde AND :hasta
        AND (:categoriaId IS NULL OR p.categoria.id = :categoriaId)
        AND t.codigo NOT IN ('CORRECCION_AUMENTA', 'CORRECCION_DISMINUYE')
        AND NOT EXISTS (SELECT 1 FROM Operacion c WHERE c.operacionCorregida = o)
        GROUP BY p.categoria.id, p.categoria.nombre
        """)
    List<Object[]> findConsumoPorCategoria(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                           @Param("categoriaId") Long categoriaId);
}
