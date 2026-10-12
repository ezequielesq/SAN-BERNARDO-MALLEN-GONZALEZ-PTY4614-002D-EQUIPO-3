package com.smartrdp.backend.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmailAndActivoTrue(String email);
    boolean existsByEmail(String email);
    List<Usuario> findByRolAndActivoTrueOrderByNombreAsc(Rol rol);

    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    boolean existsByNombreIgnoreCaseAndRolAndActivoTrue(String nombre, Rol rol);
    boolean existsByNombreIgnoreCaseAndRolAndActivoTrueAndIdNot(String nombre, Rol rol, Long id);

    List<Usuario> findByRolInOrderByNombreAsc(Collection<Rol> roles);
    List<Usuario> findByRolInAndActivoTrueOrderByNombreAsc(Collection<Rol> roles);
    Optional<Usuario> findByIdAndRolIn(Long id, Collection<Rol> roles);
}
