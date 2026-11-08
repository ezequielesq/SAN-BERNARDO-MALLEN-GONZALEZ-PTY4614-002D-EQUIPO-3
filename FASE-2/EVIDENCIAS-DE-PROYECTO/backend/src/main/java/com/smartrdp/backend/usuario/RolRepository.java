package com.smartrdp.backend.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Long> {
    Optional<Rol> findByCodigo(String codigo);
}
