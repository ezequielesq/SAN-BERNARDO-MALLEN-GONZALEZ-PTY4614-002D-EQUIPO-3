package com.smartrdp.backend.movimiento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OperacionRepository extends JpaRepository<Operacion, Long> {

    boolean existsByOperacionCorregidaId(Long operacionId);

    @Query("SELECT o FROM Operacion o JOIN FETCH o.tipo JOIN FETCH o.usuario WHERE o.id = :id")
    Optional<Operacion> buscarConDetalle(@Param("id") Long id);

    /** De las operaciones dadas, los ids de las que ya tienen una corrección. */
    @Query("SELECT o.operacionCorregida.id FROM Operacion o WHERE o.operacionCorregida.id IN :ids")
    List<Long> idsCorregidas(@Param("ids") Collection<Long> ids);
}
