package com.smartrdp.backend.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, Long> {
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
    List<UnidadMedida> findByActivoTrueOrderByNombreAsc();
    List<UnidadMedida> findAllByOrderByNombreAsc();
}
