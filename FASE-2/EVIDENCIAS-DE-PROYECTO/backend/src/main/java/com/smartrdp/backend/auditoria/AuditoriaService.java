package com.smartrdp.backend.auditoria;

import com.smartrdp.backend.solicitud.Solicitud;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class AuditoriaService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<RevisionDto> getHistorialSolicitudes(LocalDate desde, LocalDate hasta) {
        long desdeMs = desde.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long hastaMs = hasta.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(Solicitud.class, false, true)
                .add(AuditEntity.revisionProperty("timestamp").ge(desdeMs))
                .add(AuditEntity.revisionProperty("timestamp").le(hastaMs))
                .addOrder(AuditEntity.revisionNumber().desc())
                .getResultList();

        return rows.stream().map(row -> {
            Solicitud solicitud = (Solicitud) row[0];
            SmartRdpRevisionEntity rev = (SmartRdpRevisionEntity) row[1];
            RevisionType tipo = (RevisionType) row[2];
            LocalDateTime fecha = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(rev.getTimestamp()), ZoneId.systemDefault());
            return new RevisionDto(
                    solicitud.getId(),
                    fecha,
                    rev.getUsuarioEmail(),
                    tipo.name(),
                    solicitud.getEstado() != null ? solicitud.getEstado().name() : null,
                    solicitud.getTipo() != null ? solicitud.getTipo().name() : null
            );
        }).toList();
    }
}
