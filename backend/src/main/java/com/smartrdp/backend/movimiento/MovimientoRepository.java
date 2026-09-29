package com.smartrdp.backend.movimiento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    List<Movimiento> findByProductoIdOrderByCreatedAtDesc(Long productoId);

    List<Movimiento> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime desde, LocalDateTime hasta);

    List<Movimiento> findByProductoIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long productoId, LocalDateTime desde, LocalDateTime hasta);

    @Query("SELECT SUM(m.cantidad) FROM Movimiento m WHERE m.producto.id = :productoId AND m.tipo = 'ENTRADA'")
    Integer sumEntradasByProductoId(Long productoId);

    @Query("SELECT SUM(m.cantidad) FROM Movimiento m WHERE m.producto.id = :productoId AND m.tipo = 'SALIDA'")
    Integer sumSalidasByProductoId(Long productoId);

    List<Movimiento> findByCreatedAtBetweenAndTipo(LocalDateTime desde, LocalDateTime hasta, TipoMovimiento tipo);

    List<Movimiento> findByCreatedAtBetween(LocalDateTime desde, LocalDateTime hasta);

    @Query("""
        SELECT m.producto.id, m.producto.nombre, SUM(m.cantidad)
        FROM Movimiento m
        WHERE m.tipo = 'SALIDA'
        AND m.createdAt BETWEEN :desde AND :hasta
        GROUP BY m.producto.id, m.producto.nombre
        ORDER BY SUM(m.cantidad) DESC
        """)
    List<Object[]> findConsumoEntreFechas(LocalDateTime desde, LocalDateTime hasta);

    @Query("""
        SELECT m.producto.id,
               SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.cantidad ELSE -m.cantidad END)
        FROM Movimiento m
        GROUP BY m.producto.id
        """)
    List<Object[]> sumStockPorProducto();

    @Query("""
        SELECT m.producto.id, m.producto.nombre,
               SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.cantidad ELSE 0 END),
               SUM(CASE WHEN m.tipo = 'ENTRADA' AND m.costoUnitario IS NOT NULL THEN m.costoUnitario * m.cantidad ELSE 0 END),
               SUM(CASE WHEN m.tipo = 'ENTRADA' AND m.costoUnitario IS NOT NULL THEN 1 ELSE 0 END),
               SUM(CASE WHEN m.tipo = 'SALIDA' THEN m.cantidad ELSE 0 END),
               SUM(CASE WHEN m.tipo = 'SALIDA' AND m.costoUnitario IS NOT NULL THEN m.costoUnitario * m.cantidad ELSE 0 END),
               SUM(CASE WHEN m.tipo = 'SALIDA' AND m.costoUnitario IS NOT NULL THEN 1 ELSE 0 END)
        FROM Movimiento m
        WHERE m.createdAt BETWEEN :desde AND :hasta
        AND (:categoriaId IS NULL OR m.producto.categoria.id = :categoriaId)
        GROUP BY m.producto.id, m.producto.nombre
        """)
    List<Object[]> findConsumoPorProducto(LocalDateTime desde, LocalDateTime hasta, Long categoriaId);

    @Query("""
        SELECT m.producto.categoria.id, m.producto.categoria.nombre,
               SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.cantidad ELSE 0 END),
               SUM(CASE WHEN m.tipo = 'ENTRADA' AND m.costoUnitario IS NOT NULL THEN m.costoUnitario * m.cantidad ELSE 0 END),
               SUM(CASE WHEN m.tipo = 'ENTRADA' AND m.costoUnitario IS NOT NULL THEN 1 ELSE 0 END),
               SUM(CASE WHEN m.tipo = 'SALIDA' THEN m.cantidad ELSE 0 END),
               SUM(CASE WHEN m.tipo = 'SALIDA' AND m.costoUnitario IS NOT NULL THEN m.costoUnitario * m.cantidad ELSE 0 END),
               SUM(CASE WHEN m.tipo = 'SALIDA' AND m.costoUnitario IS NOT NULL THEN 1 ELSE 0 END)
        FROM Movimiento m
        WHERE m.createdAt BETWEEN :desde AND :hasta
        AND (:categoriaId IS NULL OR m.producto.categoria.id = :categoriaId)
        GROUP BY m.producto.categoria.id, m.producto.categoria.nombre
        """)
    List<Object[]> findConsumoPorCategoria(LocalDateTime desde, LocalDateTime hasta, Long categoriaId);
}
