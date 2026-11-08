package com.smartrdp.backend.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmailAndActivoTrue(String email);
    boolean existsByEmail(String email);
    List<Usuario> findByRolCodigoAndActivoTrueOrderByNombreAsc(String codigoRol);

    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    boolean existsByNombreIgnoreCaseAndRolCodigoAndActivoTrue(String nombre, String codigoRol);
    boolean existsByNombreIgnoreCaseAndRolCodigoAndActivoTrueAndIdNot(String nombre, String codigoRol, Long id);

    List<Usuario> findByRolCodigoInOrderByNombreAsc(Collection<String> codigosRol);
    List<Usuario> findByRolCodigoInAndActivoTrueOrderByNombreAsc(Collection<String> codigosRol);
    Optional<Usuario> findByIdAndRolCodigoIn(Long id, Collection<String> codigosRol);
}
