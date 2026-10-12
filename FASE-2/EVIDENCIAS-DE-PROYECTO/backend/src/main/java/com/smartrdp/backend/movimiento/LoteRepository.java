package com.smartrdp.backend.movimiento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface LoteRepository extends JpaRepository<Lote, Long> {

    // FEFO: lotes con stock disponible, ordenados por fecha de vencimiento más próxima
    List<Lote> findByProductoIdAndCantidadDisponibleGreaterThanOrderByFechaVencimientoAsc(
            Long productoId, Integer cantidadMin);

    List<Lote> findByProductoIdOrderByFechaVencimientoAsc(Long productoId);

    // Para alertas de vencimiento (HU-06)
    List<Lote> findByFechaVencimientoBeforeAndCantidadDisponibleGreaterThan(
            LocalDate fecha, Integer cantidadMin);

    @Query("""
        SELECT l FROM Lote l
        WHERE l.fechaVencimiento < :umbral
        AND l.cantidadDisponible > 0
        AND (:categoriaId IS NULL OR l.producto.categoria.id = :categoriaId)
        """)
    List<Lote> findVencimientosPorUmbral(LocalDate umbral, Long categoriaId);

    @Query("""
        SELECT l FROM Lote l
        WHERE l.fechaVencimiento BETWEEN :desde AND :hasta
        AND l.cantidadDisponible > 0
        AND (:categoriaId IS NULL OR l.producto.categoria.id = :categoriaId)
        """)
    List<Lote> findVencimientosPorRango(LocalDate desde, LocalDate hasta, Long categoriaId);
}
