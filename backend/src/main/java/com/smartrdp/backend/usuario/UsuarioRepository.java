package com.smartrdp.backend.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmailAndActivoTrue(String email);
    boolean existsByEmail(String email);
    List<Usuario> findByRolAndActivoTrueOrderByNombreAsc(Rol rol);
}
