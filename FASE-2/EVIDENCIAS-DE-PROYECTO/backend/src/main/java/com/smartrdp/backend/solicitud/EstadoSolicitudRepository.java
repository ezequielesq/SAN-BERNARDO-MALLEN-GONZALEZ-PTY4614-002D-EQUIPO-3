package com.smartrdp.backend.solicitud;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstadoSolicitudRepository extends JpaRepository<EstadoSolicitud, Long> {
    Optional<EstadoSolicitud> findByCodigo(String codigo);
}
