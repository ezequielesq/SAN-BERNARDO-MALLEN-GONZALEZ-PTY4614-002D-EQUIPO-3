package com.smartrdp.backend.movimiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TipoOperacionRepository extends JpaRepository<TipoOperacion, Long> {
    Optional<TipoOperacion> findByCodigo(String codigo);
    List<TipoOperacion> findBySeleccionableTrueOrderByIdAsc();
}
