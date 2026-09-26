package com.smartrdp.backend.solicitud;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.movimiento.MovimientoService;
import com.smartrdp.backend.movimiento.dto.SalidaRequest;
import com.smartrdp.backend.producto.ProductoRepository;
import com.smartrdp.backend.solicitud.dto.*;
import com.smartrdp.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SolicitudService {

    private final SolicitudRepository solicitudRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoService movimientoService;

    @Transactional
    public SolicitudResponse crear(SolicitudRequest request, Long solicitanteId) {
        var solicitud = new Solicitud();
        usuarioRepository.findById(solicitanteId != null ? solicitanteId : 0L)
                .ifPresent(solicitud::setSolicitante);

        for (var item : request.items()) {
            var producto = productoRepository.findById(item.productoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", item.productoId()));
            var detalle = new DetalleSolicitud();
            detalle.setSolicitud(solicitud);
            detalle.setProducto(producto);
            detalle.setCantidadSolicitada(item.cantidad());
            solicitud.getDetalles().add(detalle);
        }
        return toResponse(solicitudRepository.save(solicitud));
    }

    @Transactional
    public SolicitudResponse aprobar(Long id, AprobarSolicitudRequest request, Long bodegueroId) {
        var solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud", id));
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new BusinessException("La solicitud ya fue procesada");
        }

        // Mapa de cantidades entregadas por detalle (HU-16)
        Map<Long, Integer> cantidadesEntregadas = new HashMap<>();
        if (request.items() != null) {
            request.items().forEach(i -> cantidadesEntregadas.put(i.detalleId(), i.cantidadEntregada()));
        }

        for (var detalle : solicitud.getDetalles()) {
            int entregada = cantidadesEntregadas.getOrDefault(
                    detalle.getId(), detalle.getCantidadSolicitada());
            detalle.setCantidadEntregada(entregada);
            if (entregada > 0) {
                movimientoService.registrarSalida(
                        new SalidaRequest(detalle.getProducto().getId(), entregada,
                                "Solicitud aprobada #" + id, bodegueroId),
                        bodegueroId);
            }
        }
        solicitud.setEstado(EstadoSolicitud.APROBADA);
        if (bodegueroId != null) {
            usuarioRepository.findById(bodegueroId).ifPresent(solicitud::setBodeguero);
        }
        return toResponse(solicitudRepository.save(solicitud));
    }

    @Transactional
    public void rechazar(Long id, Long bodegueroId) {
        var solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud", id));
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new BusinessException("La solicitud ya fue procesada");
        }
        solicitud.setEstado(EstadoSolicitud.RECHAZADA);
        if (bodegueroId != null) {
            usuarioRepository.findById(bodegueroId).ifPresent(solicitud::setBodeguero);
        }
    }

    public List<SolicitudResponse> listarPendientes() {
        return solicitudRepository.findByEstadoOrderByCreatedAtDesc(EstadoSolicitud.PENDIENTE)
                .stream().map(this::toResponse).toList();
    }

    public List<SolicitudResponse> listarTodas() {
        return solicitudRepository.findAll().stream().map(this::toResponse).toList();
    }

    private SolicitudResponse toResponse(Solicitud s) {
        var detalles = s.getDetalles().stream()
                .map(d -> new SolicitudResponse.DetalleDto(
                        d.getProducto().getId(),
                        d.getProducto().getNombre(),
                        d.getCantidadSolicitada(),
                        d.getCantidadEntregada()))
                .toList();
        return new SolicitudResponse(
                s.getId(),
                s.getSolicitante() != null ? s.getSolicitante().getNombre() : "Desconocido",
                s.getEstado(),
                s.getCreatedAt(),
                detalles);
    }
}
