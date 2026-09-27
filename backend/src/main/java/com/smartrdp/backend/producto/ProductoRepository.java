package com.smartrdp.backend.producto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    boolean existsByCodigoPtv(String codigoPtv);
    boolean existsByCodigoPtvAndIdNot(String codigoPtv, Long id);
    Optional<Producto> findByCodigoPtv(String codigoPtv);
    List<Producto> findByActivoTrue();
}
