package com.smartrdp.backend.producto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    boolean existsByCodigoPtb(String codigoPtb);
    boolean existsByCodigoPtbAndIdNot(String codigoPtb, Long id);
    Optional<Producto> findByCodigoPtb(String codigoPtb);
    List<Producto> findByActivoTrue();
}
