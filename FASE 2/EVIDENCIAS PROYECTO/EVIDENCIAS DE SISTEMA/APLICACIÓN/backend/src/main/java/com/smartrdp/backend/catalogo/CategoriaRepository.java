package com.smartrdp.backend.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
    List<Categoria> findByActivoTrueOrderByNombreAsc();
    List<Categoria> findAllByOrderByNombreAsc();
}
