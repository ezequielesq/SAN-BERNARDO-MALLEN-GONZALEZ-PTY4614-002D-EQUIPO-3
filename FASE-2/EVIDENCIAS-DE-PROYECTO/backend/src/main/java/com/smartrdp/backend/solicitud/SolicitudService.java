package com.smartrdp.backend.solicitud;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.movimiento.MovimientoService;
import com.smartrdp.backend.movimiento.VistasInventario;
import com.smartrdp.backend.movimiento.VistasInventario.DevolvibleProducto;
import com.smartrdp.backend.movimiento.VistasInventario.EntregaProducto;
import com.smartrdp.backend.producto.Producto;
import com.smartrdp.backend.producto.ProductoRepository;
import com.smartrdp.backend.solicitud.dto.*;
import com.smartrdp.backend.usuario.Usuario;
import com.smartrdp.backend.usuario.UsuarioActual;
import com.smartrdp.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SolicitudService {

    private final SolicitudRepository solicitudRepository;
    private final EstadoSolicitudRepository estadoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioActual usuarioActual;
    private final MovimientoService movimientoService;
    private final VistasInventario vistas;

    @Transactional
    public SolicitudResponse crearPedido(Long solicitanteId, List<SolicitudRequest.Item> items) {
        var solicitud = nuevaPendiente(solicitanteId);
        // Un producto repetido se suma: la solicitud tiene una sola línea por producto.
        Map<Long, Integer> pedidos = new LinkedHashMap<>();
        items.forEach(i -> pedidos.merge(i.productoId(), i.cantidad(), Integer::sum));
        for (var pedido : pedidos.entrySet()) {
            var producto = productoRepository.findById(pedido.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", pedido.getKey()));
            if (!producto.isActivo()) {
                throw new BusinessException("El producto '" + producto.getNombre() + "' está deshabilitado.");
            }
            agregarDetalle(solicitud, producto, pedido.getValue());
        }
        return aRespuestas(List.of(solicitudRepository.saveAndFlush(solicitud)), false).get(0);
    }

    @Transactional
    public SolicitudResponse crearDevolucion(Long solicitanteId, Long solicitudOrigenId,
                                             List<ItemDevolucion> items) {
        var origen = solicitudRepository.findById(solicitudOrigenId)
                .orElseThrow(() -> new BusinessException("La solicitud indicada no existe o no es tuya."));
        if (!origen.getSolicitante().getId().equals(solicitanteId)) {
            throw new BusinessException("La solicitud indicada no existe o no es tuya.");
        }
        if (origen.esDevolucion() || !EstadoSolicitud.APROBADA.equals(origen.getEstado().getCodigo())) {
            throw new BusinessException("Solo se pueden devolver productos de pedidos aprobados.");
        }

        Map<Long, DevolvibleProducto> devolvibles = vistas.devolviblePorProducto(List.of(origen.getId())).stream()
                .collect(Collectors.toMap(DevolvibleProducto::productoId, Function.identity()));
        Map<Long, Integer> pendientes = vistas.pendienteDeDevolver(origen.getId());

        var devolucion = nuevaPendiente(solicitanteId);
        devolucion.setSolicitudOrigen(origen);

        Set<Long> vistos = new HashSet<>();
        for (var item : items) {
            if (!vistos.add(item.productoId())) {
                throw new BusinessException("Un producto está repetido en la devolución.");
            }
            var entregado = devolvibles.get(item.productoId());
            if (entregado == null) {
                throw new BusinessException("Un producto indicado no pertenece a esa solicitud.");
            }
            Producto producto = productoRepository.findById(item.productoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", item.productoId()));
            int libre = Math.max(0, entregado.devolvible() - pendientes.getOrDefault(item.productoId(), 0));
            if (item.cantidad() > libre) {
                throw new BusinessException("Solo puedes devolver hasta " + libre + " de '"
                        + producto.getNombre() + "'.");
            }
            agregarDetalle(devolucion, producto, item.cantidad());
        }
        return aRespuestas(List.of(solicitudRepository.saveAndFlush(devolucion)), false).get(0);
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponse> listarDeUsuario(Long solicitanteId) {
        return aRespuestas(solicitudRepository.findBySolicitanteIdOrderByCreatedAtDesc(solicitanteId), true);
    }

    /**
     * Aprueba una solicitud pendiente en UNA transacción: bloquea la solicitud (y el pedido de origen si es
     * devolución), escribe la operación del libro y recién entonces la marca aprobada. Si algo falla, no
     * queda nada a medias.
     */
    @Transactional
    public SolicitudResponse aprobar(Long id, AprobarSolicitudRequest request) {
        // Orden de bloqueo fijo: primero la solicitud, luego (si es devolución) su pedido de origen.
        var solicitud = solicitudRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud", id));
        verificarPendiente(solicitud);
        if (solicitud.esDevolucion()) {
            solicitudRepository.findByIdForUpdate(solicitud.getSolicitudOrigen().getId());
        }
        Usuario bodeguero = usuarioActual.obligatorio();
        Map<Long, Integer> cantidades = cantidadesAResolver(solicitud, request);

        if (solicitud.esDevolucion()) {
            movimientoService.recibirDevolucion(solicitud, bodeguero, cantidades);
        } else {
            movimientoService.entregarPedido(solicitud, bodeguero, cantidades);
        }
        solicitud.setEstado(estado(EstadoSolicitud.APROBADA));
        solicitud.setBodeguero(bodeguero);
        return aRespuestas(List.of(solicitudRepository.saveAndFlush(solicitud)), false).get(0);
    }

    @Transactional
    public void rechazar(Long id) {
        var solicitud = solicitudRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud", id));
        verificarPendiente(solicitud);
        solicitud.setEstado(estado(EstadoSolicitud.RECHAZADA));
        solicitud.setBodeguero(usuarioActual.obligatorio());
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponse> listarPendientes() {
        return aRespuestas(solicitudRepository.findByEstadoCodigoOrderByCreatedAtDesc(EstadoSolicitud.PENDIENTE),
                false);
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponse> listar(LocalDate desde, LocalDate hasta) {
        LocalDateTime desdeDt = (desde != null ? desde : LocalDate.now().minusDays(30)).atStartOfDay();
        LocalDateTime hastaDt = (hasta != null ? hasta : LocalDate.now()).atTime(23, 59, 59);
        return aRespuestas(solicitudRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(desdeDt, hastaDt), false);
    }

    // ------------------------------------------------------------------ internos

    private Solicitud nuevaPendiente(Long solicitanteId) {
        var solicitud = new Solicitud();
        solicitud.setEstado(estado(EstadoSolicitud.PENDIENTE));
        solicitud.setSolicitante(usuarioRepository.getReferenceById(solicitanteId));
        return solicitud;
    }

    private void agregarDetalle(Solicitud solicitud, Producto producto, int cantidad) {
        var detalle = new DetalleSolicitud();
        detalle.setSolicitud(solicitud);
        detalle.setProducto(producto);
        detalle.setCantidadSolicitada(cantidad);
        solicitud.getDetalles().add(detalle);
    }

    private EstadoSolicitud estado(String codigo) {
        return estadoRepository.findByCodigo(codigo)
                .orElseThrow(() -> new IllegalStateException("Falta el estado " + codigo + " en el catálogo."));
    }

    private void verificarPendiente(Solicitud solicitud) {
        if (!EstadoSolicitud.PENDIENTE.equals(solicitud.getEstado().getCodigo())) {
            throw new BusinessException("La solicitud ya fue procesada");
        }
    }

    /**
     * Cantidad a entregar/aceptar por producto: lo indicado, o lo solicitado si no se menciona. Los productos
     * indicados que no están en la solicitud son agregados (solo en pedidos, mínimo 1).
     */
    private Map<Long, Integer> cantidadesAResolver(Solicitud solicitud, AprobarSolicitudRequest request) {
        boolean esPedido = !solicitud.esDevolucion();
        Map<Long, Integer> indicadas = new LinkedHashMap<>();
        if (request != null && request.items() != null) {
            for (var item : request.items()) {
                if (item == null || item.productoId() == null || item.cantidad() == null) {
                    throw new BusinessException("Cada producto necesita su cantidad.");
                }
                if (item.cantidad() < 0) {
                    throw new BusinessException("La cantidad no puede ser negativa.");
                }
                if (indicadas.put(item.productoId(), item.cantidad()) != null) {
                    throw new BusinessException("Un producto está repetido.");
                }
            }
        }

        Map<Long, Integer> resultado = new LinkedHashMap<>();
        Set<Long> enSolicitud = new HashSet<>();
        for (var detalle : solicitud.getDetalles()) {
            Long productoId = detalle.getProducto().getId();
            enSolicitud.add(productoId);
            int cantidad = indicadas.getOrDefault(productoId, detalle.getCantidadSolicitada());
            if (cantidad > detalle.getCantidadSolicitada()) {
                throw new BusinessException(esPedido
                        ? "No puedes entregar más de lo solicitado de '" + detalle.getProducto().getNombre() + "'."
                        : "No puedes aceptar más de lo que se quiere devolver de '"
                                + detalle.getProducto().getNombre() + "'.");
            }
            resultado.put(productoId, cantidad);
        }
        for (var indicada : indicadas.entrySet()) {
            if (enSolicitud.contains(indicada.getKey())) continue;
            if (!esPedido) {
                throw new BusinessException("Solo se pueden agregar productos a un pedido.");
            }
            if (indicada.getValue() < 1) {
                throw new BusinessException("Cada producto agregado necesita una cantidad de al menos 1.");
            }
            resultado.put(indicada.getKey(), indicada.getValue());
        }
        return resultado;
    }

    /**
     * Arma las respuestas con pocas consultas: lo entregado sale de la vista solicitado_vs_entregado y lo
     * devolvible de devolvible_lote. Con {@code conDevolvible} (lista del propio empleado) se calcula además lo
     * ya devuelto y lo que aún puede devolver (descontando devoluciones pendientes).
     */
    private List<SolicitudResponse> aRespuestas(List<Solicitud> solicitudes, boolean conDevolvible) {
        if (solicitudes.isEmpty()) return List.of();
        List<Long> ids = solicitudes.stream().map(Solicitud::getId).toList();
        Map<Long, List<EntregaProducto>> filas = vistas.solicitadoVsEntregado(ids).stream()
                .collect(Collectors.groupingBy(EntregaProducto::solicitudId));

        List<Long> pedidosAprobados = conDevolvible
                ? solicitudes.stream()
                        .filter(s -> !s.esDevolucion() && EstadoSolicitud.APROBADA.equals(s.getEstado().getCodigo()))
                        .map(Solicitud::getId).toList()
                : List.of();
        Map<Long, Map<Long, DevolvibleProducto>> devolvibles = vistas.devolviblePorProducto(pedidosAprobados).stream()
                .collect(Collectors.groupingBy(DevolvibleProducto::pedidoId,
                        Collectors.toMap(DevolvibleProducto::productoId, Function.identity())));

        Set<Long> productosAgregados = filas.values().stream().flatMap(List::stream)
                .filter(EntregaProducto::agregado).map(EntregaProducto::productoId).collect(Collectors.toSet());
        Map<Long, String> nombresAgregados = productoRepository.findAllById(productosAgregados).stream()
                .collect(Collectors.toMap(Producto::getId, Producto::getNombre));

        List<SolicitudResponse> respuestas = new ArrayList<>();
        for (Solicitud s : solicitudes) {
            Map<Long, EntregaProducto> porProducto = filas.getOrDefault(s.getId(), List.of()).stream()
                    .collect(Collectors.toMap(EntregaProducto::productoId, Function.identity()));
            boolean calcular = conDevolvible && !s.esDevolucion();
            boolean aprobada = EstadoSolicitud.APROBADA.equals(s.getEstado().getCodigo());
            // Una consulta por pedido aprobado del propio empleado (lista corta): lo pendiente de devolver.
            Map<Long, Integer> pendientes = calcular && aprobada ? vistas.pendienteDeDevolver(s.getId()) : Map.of();
            Map<Long, DevolvibleProducto> deEste = devolvibles.getOrDefault(s.getId(), Map.of());

            List<SolicitudResponse.DetalleDto> detalles = new ArrayList<>();
            for (DetalleSolicitud d : s.getDetalles()) {
                Long productoId = d.getProducto().getId();
                var fila = porProducto.get(productoId);
                detalles.add(detalle(productoId, d.getProducto().getNombre(), d.getCantidadSolicitada(),
                        fila != null ? fila.cantidadEntregada() : null, false,
                        calcular, aprobada, deEste.get(productoId), pendientes.getOrDefault(productoId, 0)));
            }
            porProducto.values().stream().filter(EntregaProducto::agregado)
                    .sorted(Comparator.comparingLong(EntregaProducto::productoId))
                    .forEach(f -> detalles.add(detalle(f.productoId(), nombresAgregados.get(f.productoId()), 0,
                            f.cantidadEntregada(), true,
                            calcular, aprobada, deEste.get(f.productoId()), pendientes.getOrDefault(f.productoId(), 0))));

            respuestas.add(new SolicitudResponse(
                    s.getId(), s.getSolicitante().getNombre(), s.getEstado().getCodigo(), s.tipoCodigo(),
                    s.getSolicitudOrigen() != null ? s.getSolicitudOrigen().getId() : null,
                    s.getCreatedAt(), detalles));
        }
        return respuestas;
    }

    private SolicitudResponse.DetalleDto detalle(Long productoId, String nombre, int solicitada, Integer entregada,
                                                 boolean agregado, boolean calcular, boolean aprobada,
                                                 DevolvibleProducto devolvible, int pendiente) {
        Integer devuelto = calcular ? (devolvible != null ? devolvible.devuelto() : 0) : null;
        Integer puede = calcular ? (aprobada && devolvible != null
                ? Math.max(0, devolvible.devolvible() - pendiente) : 0) : null;
        return new SolicitudResponse.DetalleDto(productoId, nombre, solicitada, entregada, devuelto, puede, agregado);
    }
}
