package com.smartrdp.backend.solicitud;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.movimiento.Movimiento;
import com.smartrdp.backend.movimiento.MovimientoService;
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
    private final DetalleSolicitudRepository detalleSolicitudRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoService movimientoService;

    @Transactional
    public SolicitudResponse crearPedido(Long solicitanteId, List<SolicitudRequest.Item> items) {
        var solicitud = new Solicitud();
        solicitud.setTipo(TipoSolicitud.PEDIDO);
        solicitud.setSolicitante(usuarioRepository.getReferenceById(solicitanteId));

        for (var item : items) {
            var producto = productoRepository.findById(item.productoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", item.productoId()));
            if (!producto.isActivo()) {
                throw new BusinessException("El producto '" + producto.getNombre() + "' está deshabilitado.");
            }
            var detalle = new DetalleSolicitud();
            detalle.setSolicitud(solicitud);
            detalle.setProducto(producto);
            detalle.setCantidadSolicitada(item.cantidad());
            solicitud.getDetalles().add(detalle);
        }
        return toResponse(solicitudRepository.save(solicitud), false);
    }

    @Transactional
    public SolicitudResponse crearDevolucion(Long solicitanteId, Long solicitudOrigenId,
                                             List<ItemDevolucion> items) {
        var origen = solicitudRepository.findById(solicitudOrigenId)
                .orElseThrow(() -> new BusinessException("La solicitud indicada no existe o no es tuya."));
        if (origen.getSolicitante() == null || !origen.getSolicitante().getId().equals(solicitanteId)) {
            throw new BusinessException("La solicitud indicada no existe o no es tuya.");
        }
        if (origen.getTipo() != TipoSolicitud.PEDIDO || origen.getEstado() != EstadoSolicitud.APROBADA) {
            throw new BusinessException("Solo se pueden devolver productos de pedidos aprobados.");
        }

        var devolucion = new Solicitud();
        devolucion.setTipo(TipoSolicitud.DEVOLUCION);
        devolucion.setSolicitante(usuarioRepository.getReferenceById(solicitanteId));
        devolucion.setSolicitudOrigen(origen);

        Set<Long> vistos = new HashSet<>();
        for (var item : items) {
            if (!vistos.add(item.detalleId())) {
                throw new BusinessException("Un producto está repetido en la devolución.");
            }
            var detalleOrigen = origen.getDetalles().stream()
                    .filter(d -> d.getId().equals(item.detalleId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Un producto indicado no pertenece a esa solicitud."));
            int devolvible = devolvible(detalleOrigen);
            if (item.cantidad() > devolvible) {
                throw new BusinessException("Solo puedes devolver hasta " + devolvible + " de '"
                        + detalleOrigen.getProducto().getNombre() + "'.");
            }
            var detalle = new DetalleSolicitud();
            detalle.setSolicitud(devolucion);
            detalle.setProducto(detalleOrigen.getProducto());
            detalle.setCantidadSolicitada(item.cantidad());
            detalle.setDetalleOrigen(detalleOrigen);
            devolucion.getDetalles().add(detalle);
        }
        return toResponse(solicitudRepository.save(devolucion), false);
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponse> listarDeUsuario(Long solicitanteId) {
        return solicitudRepository.findBySolicitanteIdOrderByCreatedAtDesc(solicitanteId)
                .stream().map(s -> toResponse(s, true)).toList();
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

        String solicitante = solicitud.getSolicitante() != null
                ? solicitud.getSolicitante().getNombre() : "Desconocido";
        boolean esPedido = solicitud.getTipo() == TipoSolicitud.PEDIDO;

        for (var detalle : solicitud.getDetalles()) {
            int entregada = cantidadesEntregadas.getOrDefault(
                    detalle.getId(), detalle.getCantidadSolicitada());
            if (entregada < 0) {
                throw new BusinessException("La cantidad no puede ser negativa.");
            }
            if (!esPedido && entregada > detalle.getCantidadSolicitada()) {
                throw new BusinessException("No puedes aceptar más de lo que se quiere devolver de '"
                        + detalle.getProducto().getNombre() + "'.");
            }
            detalle.setCantidadEntregada(entregada);
            if (entregada > 0) {
                Movimiento movimiento = esPedido
                        ? movimientoService.registrarSolicitado(detalle.getProducto().getId(), entregada,
                                "Solicitud #" + id + " de " + solicitante)
                        : movimientoService.registrarDevolucion(
                                detalle.getDetalleOrigen().getMovimiento(), entregada,
                                "Devolución del pedido #" + solicitud.getSolicitudOrigen().getId()
                                        + " de " + solicitante);
                detalle.setMovimiento(movimiento);
            }
        }
        solicitud.setEstado(EstadoSolicitud.APROBADA);
        getCurrentUsuario().ifPresent(solicitud::setBodeguero);
        return toResponse(solicitudRepository.save(solicitud), false);
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
                .stream().map(s -> toResponse(s, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponse> listar(LocalDate desde, LocalDate hasta) {
        LocalDateTime desdeDt = (desde != null ? desde : LocalDate.now().minusDays(30)).atStartOfDay();
        LocalDateTime hastaDt = (hasta != null ? hasta : LocalDate.now()).atTime(23, 59, 59);
        return solicitudRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(desdeDt, hastaDt)
                .stream().map(s -> toResponse(s, false)).toList();
    }

    /** Lo entregado de un detalle menos lo ya devuelto (aprobado) y lo pendiente de devolver. */
    private int devolvible(DetalleSolicitud d) {
        if (d.getMovimiento() == null || d.getCantidadEntregada() == null) return 0;
        long usado = detalleSolicitudRepository.sumDevueltoAprobado(d.getId())
                + detalleSolicitudRepository.sumDevueltoPendiente(d.getId());
        return (int) Math.max(0, d.getCantidadEntregada() - usado);
    }

    private Optional<Usuario> getCurrentUsuario() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        return usuarioRepository.findByEmailAndActivoTrue(auth.getName());
    }

    private SolicitudResponse toResponse(Solicitud s, boolean conDevolvible) {
        boolean calcular = conDevolvible && s.getTipo() == TipoSolicitud.PEDIDO;
        var detalles = s.getDetalles().stream()
                .map(d -> new SolicitudResponse.DetalleDto(
                        d.getId(),
                        d.getProducto().getId(),
                        d.getProducto().getNombre(),
                        d.getCantidadSolicitada(),
                        d.getCantidadEntregada(),
                        calcular ? detalleSolicitudRepository.sumDevueltoAprobado(d.getId()).intValue() : null,
                        calcular ? (s.getEstado() == EstadoSolicitud.APROBADA ? devolvible(d) : 0) : null))
                .toList();
        return new SolicitudResponse(
                s.getId(),
                s.getSolicitante() != null ? s.getSolicitante().getNombre() : "Desconocido",
                s.getEstado(),
                s.getTipo(),
                s.getSolicitudOrigen() != null ? s.getSolicitudOrigen().getId() : null,
                s.getCreatedAt(),
                detalles);
    }
}
