package com.smartrdp.backend.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {
    boolean existsByRut(String rut);
    boolean existsByRutAndIdNot(String rut, Long id);
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
    Optional<Proveedor> findByRut(String rut);
    List<Proveedor> findByActivoTrueOrderByNombreAsc();
    List<Proveedor> findAllByOrderByNombreAsc();
}
