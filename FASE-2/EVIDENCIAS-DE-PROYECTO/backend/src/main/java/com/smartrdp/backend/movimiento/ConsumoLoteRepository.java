package com.smartrdp.backend.movimiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsumoLoteRepository extends JpaRepository<ConsumoLote, Long> {

    /** Del último lote consumido al primero (el id crece en orden de consumo). */
    List<ConsumoLote> findByMovimientoIdOrderByIdDesc(Long movimientoId);
}
