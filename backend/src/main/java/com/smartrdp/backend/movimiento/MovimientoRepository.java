package com.smartrdp.backend.movimiento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    List<Movimiento> findByProductoIdOrderByCreatedAtDesc(Long productoId);

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
}
