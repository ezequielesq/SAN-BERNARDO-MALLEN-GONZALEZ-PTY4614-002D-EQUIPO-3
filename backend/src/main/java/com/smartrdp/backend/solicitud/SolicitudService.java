package com.smartrdp.backend.solicitud;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.movimiento.MovimientoService;
import com.smartrdp.backend.movimiento.dto.SalidaRequest;
import com.smartrdp.backend.producto.ProductoRepository;
import com.smartrdp.backend.solicitud.dto.*;
import com.smartrdp.backend.usuario.Usuario;
import com.smartrdp.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SolicitudService {

    private final SolicitudRepository solicitudRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoService movimientoService;

    @Transactional
    public SolicitudResponse crear(SolicitudRequest request) {
        var solicitud = new Solicitud();
        getCurrentUsuario().ifPresent(solicitud::setSolicitante);

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
    public SolicitudResponse aprobar(Long id, AprobarSolicitudRequest request) {
        var solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud", id));
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new BusinessException("La solicitud ya fue procesada");
        }

        Map<Long, Integer> cantidadesEntregadas = new HashMap<>();
        if (request.items() != null) {
            request.items().stream()
                    .filter(i -> i.cantidadEntregada() != null)
                    .forEach(i -> cantidadesEntregadas.put(i.detalleId(), i.cantidadEntregada()));
        }

        for (var detalle : solicitud.getDetalles()) {
            int entregada = cantidadesEntregadas.getOrDefault(
                    detalle.getId(), detalle.getCantidadSolicitada());
            detalle.setCantidadEntregada(entregada);
            if (entregada > 0) {
                movimientoService.registrarSalida(
                        new SalidaRequest(detalle.getProducto().getId(), entregada,
                                "Solicitud aprobada #" + id));
            }
        }
        solicitud.setEstado(EstadoSolicitud.APROBADA);
        getCurrentUsuario().ifPresent(solicitud::setBodeguero);
        return toResponse(solicitudRepository.save(solicitud));
    }

    @Transactional
    public void rechazar(Long id) {
        var solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud", id));
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new BusinessException("La solicitud ya fue procesada");
        }
        solicitud.setEstado(EstadoSolicitud.RECHAZADA);
        getCurrentUsuario().ifPresent(solicitud::setBodeguero);
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponse> listarPendientes() {
        return solicitudRepository.findByEstadoOrderByCreatedAtDesc(EstadoSolicitud.PENDIENTE)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponse> listarTodas() {
        return solicitudRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponse> listarPorFecha(LocalDate desde, LocalDate hasta) {
        LocalDateTime desdeTime = desde.atStartOfDay();
        LocalDateTime hastaTime = hasta.atTime(23, 59, 59);
        return solicitudRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(desdeTime, hastaTime)
                .stream().map(this::toResponse).toList();
    }

    private Optional<Usuario> getCurrentUsuario() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        return usuarioRepository.findByEmailAndActivoTrue(auth.getName());
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
