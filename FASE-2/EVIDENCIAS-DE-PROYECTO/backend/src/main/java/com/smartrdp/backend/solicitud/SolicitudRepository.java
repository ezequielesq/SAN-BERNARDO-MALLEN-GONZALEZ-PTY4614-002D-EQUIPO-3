package com.smartrdp.backend.solicitud;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Solicitud s WHERE s.id = :id")
    Optional<Solicitud> findByIdForUpdate(@Param("id") Long id);

    List<Solicitud> findBySolicitanteIdOrderByCreatedAtDesc(Long solicitanteId);
    List<Solicitud> findByEstadoOrderByCreatedAtDesc(EstadoSolicitud estado);
    List<Solicitud> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime desde, LocalDateTime hasta);
}
