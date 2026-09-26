package com.smartrdp.backend.movimiento;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
