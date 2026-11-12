package com.smartrdp.backend.evento;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    /** Orden de la agenda: por día, los de todo el día (sin hora) primero, y por id para que sea estable. */
    @EntityGraph(attributePaths = "creadoPor")
    @Query("""
            SELECT e FROM Evento e
            WHERE e.fecha BETWEEN :desde AND :hasta
            ORDER BY e.fecha ASC, e.hora ASC NULLS FIRST, e.id ASC
            """)
    List<Evento> listarEntre(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
