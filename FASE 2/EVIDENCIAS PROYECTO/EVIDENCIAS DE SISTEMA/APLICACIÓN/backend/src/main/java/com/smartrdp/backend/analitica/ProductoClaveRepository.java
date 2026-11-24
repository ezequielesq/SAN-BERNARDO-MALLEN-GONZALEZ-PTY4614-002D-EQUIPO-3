package com.smartrdp.backend.analitica;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductoClaveRepository extends JpaRepository<ProductoClave, Long> {

    /** Productos clave activos del usuario, por nombre. */
    @Query("""
        SELECT pc FROM ProductoClave pc JOIN FETCH pc.producto p
        WHERE pc.usuario.id = :usuarioId AND p.activo = true
        ORDER BY p.nombre
        """)
    List<ProductoClave> activosDe(@Param("usuarioId") Long usuarioId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ProductoClave pc WHERE pc.usuario.id = :usuarioId")
    void borrarDe(@Param("usuarioId") Long usuarioId);
}
