package com.smartrdp.backend.auditoria;

import com.smartrdp.backend.movimiento.VistasInventario;
import com.smartrdp.backend.producto.Producto;
import com.smartrdp.backend.producto.ProductoRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuditoriaService {

    @PersistenceContext
    private EntityManager entityManager;

    private final VistasInventario vistas;
    private final ProductoRepository productoRepository;

    public AuditoriaService(VistasInventario vistas, ProductoRepository productoRepository) {
        this.vistas = vistas;
        this.productoRepository = productoRepository;
    }

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

        // Lo pedido y lo entregado por producto: una sola lectura de la vista para todas las solicitudes del listado.
        Set<Long> solicitudIds = rows.stream()
                .map(row -> ((Solicitud) row[0]).getId())
                .collect(Collectors.toSet());
        Map<Long, List<VistasInventario.EntregaProducto>> entregasPorSolicitud = vistas.solicitadoVsEntregado(solicitudIds)
                .stream().collect(Collectors.groupingBy(VistasInventario.EntregaProducto::solicitudId));
        Set<Long> productoIds = entregasPorSolicitud.values().stream()
                .flatMap(List::stream)
                .map(VistasInventario.EntregaProducto::productoId)
                .collect(Collectors.toSet());
        Map<Long, String> nombres = new HashMap<>();
        productoRepository.findAllById(productoIds).forEach((Producto p) -> nombres.put(p.getId(), p.getNombre()));

        return rows.stream().map(row -> {
            Solicitud solicitud = (Solicitud) row[0];
            SmartRdpRevisionEntity rev = (SmartRdpRevisionEntity) row[1];
            RevisionType tipo = (RevisionType) row[2];
            LocalDateTime fecha = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(rev.getTimestamp()), ZoneId.systemDefault());
            List<RevisionDto.LineaRevisionDto> lineas = entregasPorSolicitud
                    .getOrDefault(solicitud.getId(), List.of()).stream()
                    .map(e -> new RevisionDto.LineaRevisionDto(
                            e.productoId(),
                            nombres.get(e.productoId()),
                            e.cantidadSolicitada(),
                            e.cantidadEntregada(),
                            e.agregado()))
                    .toList();
            return new RevisionDto(
                    solicitud.getId(),
                    fecha,
                    rev.getUsuarioEmail(),
                    tipo.name(),
                    solicitud.getEstado() != null ? solicitud.getEstado().getCodigo() : null,
                    solicitud.tipoCodigo(),
                    lineas
            );
        }).toList();
    }
}
