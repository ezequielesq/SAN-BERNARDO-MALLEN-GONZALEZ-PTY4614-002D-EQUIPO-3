package com.smartrdp.backend.movimiento;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.movimiento.dto.*;
import com.smartrdp.backend.producto.Producto;
import com.smartrdp.backend.producto.ProductoRepository;
import com.smartrdp.backend.solicitud.Solicitud;
import com.smartrdp.backend.usuario.Usuario;
import com.smartrdp.backend.usuario.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Escribe y lee el libro de operaciones. Regla de oro: el stock no se guarda; toda operación que toca
 * lotes existentes (salida, entrega, devolución, corrección) bloquea primero TODOS esos lotes en una sola
 * consulta FOR UPDATE en orden global de id, y recién después lee saldos e inserta movimientos.
 * Los triggers de la base son la red de seguridad (saldo negativo, coherencia, inmutabilidad).
 */
@Service
@RequiredArgsConstructor
public class MovimientoService {

    private final LoteRepository loteRepository;
    private final MovimientoRepository movimientoRepository;
    private final OperacionRepository operacionRepository;
    private final TipoOperacionRepository tipoOperacionRepository;
    private final ProductoRepository productoRepository;
    private final VistasInventario vistas;
    private final UsuarioActual usuarioActual;

    // ------------------------------------------------------------------ escritura manual

    @Transactional
    public OperacionResponse registrarIngreso(EntradaRequest request) {
        TipoOperacion tipo = tipoManual(request.tipo(), 1);
        Operacion operacion = nuevaOperacion(tipo, usuarioActual.obligatorio(), request.nota());
        for (var linea : request.lineas()) {
            Producto producto = productoOperable(linea.productoId());
            if (producto.isEsPerecible() && linea.fechaVencimiento() == null) {
                throw new BusinessException("'" + producto.getNombre()
                        + "' es perecible: indica su fecha de vencimiento.", "fechaVencimiento");
            }
            Lote lote = new Lote();
            lote.setProducto(producto);
            lote.setNumeroLote(linea.numeroLote());
            lote.setFechaVencimiento(linea.fechaVencimiento());
            // El costo del producto es solo una referencia: el lote guarda el costo con que entró.
            lote.setCostoUnitario(linea.costoUnitario() != null ? linea.costoUnitario() : producto.getCostoUnitario());
            loteRepository.save(lote);
            operacion.agregarMovimiento(lote, linea.cantidad());
        }
        operacionRepository.saveAndFlush(operacion);
        return aRespuesta(operacion);
    }

    @Transactional
    public OperacionResponse registrarSalida(SalidaRequest request) {
        TipoOperacion tipo = tipoManual(request.tipo(), -1);
        Operacion operacion = nuevaOperacion(tipo, usuarioActual.obligatorio(), request.nota());
        Map<Long, Integer> porProducto = new TreeMap<>();
        request.lineas().forEach(l -> porProducto.merge(l.productoId(), l.cantidad(), Integer::sum));
        descontarFefo(operacion, porProducto);
        operacionRepository.saveAndFlush(operacion);
        return aRespuesta(operacion);
    }

    /** Contrarresta una operación manual con otra de sentido contrario y las mismas líneas (espejo exacto). */
    @Transactional
    public OperacionResponse corregir(Long operacionId, String nota) {
        Operacion original = operacionRepository.buscarConDetalle(operacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Operación", operacionId));
        if (!original.getTipo().isSeleccionable()) {
            throw new BusinessException("Solo se corrigen operaciones registradas a mano; "
                    + "las de solicitudes se deshacen con una devolución.");
        }
        if (operacionRepository.existsByOperacionCorregidaId(operacionId)) {
            throw new BusinessException("Esta operación ya fue corregida.");
        }
        List<Movimiento> lineas = new ArrayList<>(original.getMovimientos());
        List<Long> loteIds = lineas.stream().map(m -> m.getLote().getId()).sorted().toList();
        loteRepository.bloquearPorIds(loteIds);

        boolean quitaStock = original.getTipo().esEntrada();
        if (quitaStock) {
            Map<Long, Integer> saldos = vistas.saldosPorLote(loteIds);
            for (Movimiento m : lineas) {
                if (saldos.getOrDefault(m.getLote().getId(), 0) < m.getCantidad()) {
                    throw new BusinessException("No se puede corregir: del lote "
                            + etiquetaLote(m.getLote()) + " ya se usó parte de lo ingresado.");
                }
            }
        }
        TipoOperacion tipo = tipoPorCodigo(quitaStock
                ? TipoOperacion.CORRECCION_DISMINUYE : TipoOperacion.CORRECCION_AUMENTA);
        Operacion correccion = nuevaOperacion(tipo, usuarioActual.obligatorio(), nota);
        correccion.setOperacionCorregida(original);
        lineas.forEach(m -> correccion.agregarMovimiento(m.getLote(), m.getCantidad()));
        operacionRepository.saveAndFlush(correccion);
        return aRespuesta(correccion);
    }

    // ------------------------------------------------------------------ escritura desde solicitudes

    /** Entrega de pedido: descuenta por FEFO lo entregado de cada producto (las cantidades 0 se omiten). */
    @Transactional
    public Operacion entregarPedido(Solicitud solicitud, Usuario bodeguero, Map<Long, Integer> entregas) {
        Operacion operacion = nuevaOperacion(tipoPorCodigo(TipoOperacion.ENTREGA_PEDIDO), bodeguero,
                "Solicitud #" + solicitud.getId() + " de " + solicitud.getSolicitante().getNombre());
        operacion.setSolicitud(solicitud);
        Map<Long, Integer> aEntregar = new TreeMap<>();
        entregas.forEach((productoId, cantidad) -> {
            if (cantidad != null && cantidad > 0) aEntregar.put(productoId, cantidad);
        });
        descontarFefo(operacion, aEntregar);
        if (operacion.getMovimientos().isEmpty()) {
            throw new BusinessException("No hay nada que entregar: rechaza la solicitud en lugar de aprobarla.");
        }
        return operacionRepository.saveAndFlush(operacion);
    }

    /** Devolución: repone en los lotes del pedido de origen, empezando por el último lote consumido. */
    @Transactional
    public Operacion recibirDevolucion(Solicitud devolucion, Usuario bodeguero, Map<Long, Integer> aceptadas) {
        long pedidoId = devolucion.getSolicitudOrigen().getId();
        Operacion operacion = nuevaOperacion(tipoPorCodigo(TipoOperacion.DEVOLUCION), bodeguero,
                "Devolución del pedido #" + pedidoId + " de " + devolucion.getSolicitante().getNombre());
        operacion.setSolicitud(devolucion);
        // 1) Reparto sobre devolvible_lote (estable: el llamador ya bloqueó el pedido de origen, que
        //    serializa las devoluciones de ese pedido). 2) Bloqueo de TODOS los lotes destino en orden
        //    global de id, igual que las salidas. 3) Recién entonces se agregan los movimientos.
        Map<Long, Integer> reparto = new LinkedHashMap<>(); // loteId -> cantidad, en orden de reparto
        for (var e : new TreeMap<>(aceptadas).entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            int restante = e.getValue();
            for (var lote : vistas.devolviblePorLote(pedidoId, e.getKey())) {
                if (restante <= 0) break;
                int reponer = Math.min(lote.devolvible(), restante);
                reparto.merge(lote.loteId(), reponer, Integer::sum);
                restante -= reponer;
            }
            if (restante > 0) {
                String nombre = productoRepository.findById(e.getKey()).map(Producto::getNombre).orElse("el producto");
                throw new BusinessException("La cantidad a devolver supera lo entregado de '" + nombre + "'.");
            }
        }
        if (!reparto.isEmpty()) {
            Map<Long, Lote> bloqueados = loteRepository.bloquearPorIds(new TreeSet<>(reparto.keySet())).stream()
                    .collect(Collectors.toMap(Lote::getId, Function.identity()));
            reparto.forEach((loteId, cantidad) -> operacion.agregarMovimiento(bloqueados.get(loteId), cantidad));
        }
        if (operacion.getMovimientos().isEmpty()) {
            throw new BusinessException("No hay nada que recibir: rechaza la solicitud en lugar de aprobarla.");
        }
        return operacionRepository.saveAndFlush(operacion);
    }

    // ------------------------------------------------------------------ lectura

    @Transactional(readOnly = true)
    public List<MovimientoResponse> listar(Long productoId, LocalDate desde, LocalDate hasta) {
        LocalDate baseParaDesde = hasta != null ? hasta : LocalDate.now();
        LocalDateTime desdeDt = (desde != null ? desde : baseParaDesde.minusDays(30)).atStartOfDay();
        LocalDateTime hastaDt = (hasta != null ? hasta : LocalDate.now()).atTime(23, 59, 59);
        return aRespuestas(movimientoRepository.buscar(desdeDt, hastaDt, productoId, null));
    }

    @Transactional(readOnly = true)
    public List<TipoOperacionResponse> listarTipos(Integer sentido) {
        return tipoOperacionRepository.findBySeleccionableTrueOrderByIdAsc().stream()
                .filter(t -> sentido == null || t.getSentido() == sentido)
                .map(t -> new TipoOperacionResponse(t.getId(), t.getCodigo(), t.getNombre(), t.getSentido()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockStatusResponse> listarStock() {
        Map<Long, VistasInventario.StockProducto> stocks = vistas.stocks().stream()
                .collect(Collectors.toMap(VistasInventario.StockProducto::productoId, Function.identity()));
        Map<Long, LocalDate> proximos = vistas.proximosVencimientos();
        return productoRepository.findByActivoTrue().stream()
                // Un producto creado entre ambas lecturas no está aún en el mapa: se muestra sin stock.
                .map(p -> aStockResponse(p, stocks.getOrDefault(p.getId(),
                                new VistasInventario.StockProducto(p.getId(), 0, null, EstadoStock.AGOTADO.name())),
                        proximos.get(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public StockStatusResponse stockDe(Long productoId) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", productoId));
        var stock = vistas.stock(productoId).orElseThrow(() -> new ResourceNotFoundException("Producto", productoId));
        return aStockResponse(producto, stock, vistas.proximoVencimiento(productoId).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<AlertaVencimientoResponse> getAlertasVencimiento(int diasUmbral) {
        LocalDate hoy = LocalDate.now();
        return vistas.lotesConVencimiento(null, hoy.plusDays(diasUmbral), null).stream()
                .map(l -> new AlertaVencimientoResponse(l.loteId(), l.productoId(), l.productoNombre(),
                        l.numeroLote(), l.saldo(), l.fechaVencimiento(), l.fechaVencimiento().isBefore(hoy)))
                .toList();
    }

    // ------------------------------------------------------------------ internos

    /**
     * Descuenta por FEFO las cantidades indicadas (productoId -> cantidad). Primero valida los productos,
     * luego bloquea en UNA consulta todos sus lotes en orden global de id (el mismo orden que usan
     * correcciones y devoluciones: sin interbloqueos) y recién después lee los saldos y reparte.
     */
    private void descontarFefo(Operacion operacion, Map<Long, Integer> cantidades) {
        if (cantidades.isEmpty()) return;
        Map<Long, Producto> productos = new TreeMap<>();
        cantidades.keySet().forEach(id -> productos.put(id, productoOperable(id)));
        List<Lote> bloqueados = loteRepository.bloquearDeProductos(productos.keySet());
        Map<Long, Integer> saldos = vistas.saldosPorLote(bloqueados.stream().map(Lote::getId).toList());
        Map<Long, List<Lote>> lotesPorProducto = bloqueados.stream()
                .collect(Collectors.groupingBy(l -> l.getProducto().getId()));
        for (var e : cantidades.entrySet()) {
            descontarFefo(operacion, productos.get(e.getKey()), e.getValue(),
                    lotesPorProducto.getOrDefault(e.getKey(), List.of()), saldos);
        }
    }

    private void descontarFefo(Operacion operacion, Producto producto, int cantidad,
                               List<Lote> lotes, Map<Long, Integer> saldos) {
        List<Lote> fefo = lotes.stream()
                .filter(l -> saldos.getOrDefault(l.getId(), 0) > 0)
                .sorted(Comparator.comparing(Lote::getFechaVencimiento,
                                Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                        .thenComparing(Lote::getId))
                .toList();
        int disponible = fefo.stream().mapToInt(l -> saldos.get(l.getId())).sum();
        if (disponible < cantidad) {
            throw new BusinessException("Stock insuficiente de '" + producto.getNombre()
                    + "'. Disponible: " + disponible + ", solicitado: " + cantidad);
        }
        int restante = cantidad;
        for (Lote lote : fefo) {
            if (restante <= 0) break;
            int tomar = Math.min(saldos.get(lote.getId()), restante);
            operacion.agregarMovimiento(lote, tomar);
            restante -= tomar;
        }
    }

    private Operacion nuevaOperacion(TipoOperacion tipo, Usuario usuario, String nota) {
        Operacion operacion = new Operacion();
        operacion.setTipo(tipo);
        operacion.setUsuario(usuario);
        operacion.setNota(nota == null || nota.isBlank() ? null : nota.trim());
        return operacion;
    }

    private TipoOperacion tipoPorCodigo(String codigo) {
        return tipoOperacionRepository.findByCodigo(codigo)
                .orElseThrow(() -> new IllegalStateException("Falta el tipo de operación " + codigo + " en el catálogo."));
    }

    /** Tipo que el bodeguero puede elegir a mano y que va en el sentido pedido (+1 ingreso, -1 salida). */
    private TipoOperacion tipoManual(String codigo, int sentido) {
        TipoOperacion tipo = tipoOperacionRepository.findByCodigo(codigo)
                .filter(TipoOperacion::isSeleccionable)
                .orElseThrow(() -> new BusinessException("Tipo de operación no válido: " + codigo, "tipo"));
        if (tipo.getSentido() != sentido) {
            throw new BusinessException("El tipo '" + tipo.getNombre() + "' no corresponde a "
                    + (sentido > 0 ? "un ingreso" : "una salida") + ".", "tipo");
        }
        return tipo;
    }

    private Producto productoOperable(Long productoId) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", productoId));
        if (!producto.isActivo()) {
            throw new BusinessException("El producto '" + producto.getNombre() + "' está deshabilitado.");
        }
        return producto;
    }

    private String etiquetaLote(Lote lote) {
        return lote.getNumeroLote() != null ? lote.getNumeroLote() : "#" + lote.getId();
    }

    private StockStatusResponse aStockResponse(Producto p, VistasInventario.StockProducto s, LocalDate proximo) {
        return new StockStatusResponse(p.getId(), p.getNombre(), s.stock(), EstadoStock.valueOf(s.estado()),
                proximo, p.getCostoUnitario(), s.valor());
    }

    private OperacionResponse aRespuesta(Operacion o) {
        var lineas = o.getMovimientos().stream()
                .map(m -> new OperacionResponse.Linea(m.getLote().getProducto().getId(),
                        m.getLote().getProducto().getNombre(), m.getLote().getId(),
                        m.getLote().getNumeroLote(), m.getCantidad()))
                .toList();
        return new OperacionResponse(o.getId(), o.getTipo().getCodigo(), o.getTipo().getNombre(),
                o.getCreatedAt(), o.getUsuario().getEmail(), o.getNota(), lineas);
    }

    private List<MovimientoResponse> aRespuestas(List<Movimiento> movimientos) {
        Set<Long> operaciones = movimientos.stream().map(m -> m.getOperacion().getId()).collect(Collectors.toSet());
        Set<Long> corregidas = operaciones.isEmpty()
                ? Set.of() : new HashSet<>(operacionRepository.idsCorregidas(operaciones));
        return movimientos.stream().map(m -> {
            Operacion o = m.getOperacion();
            return new MovimientoResponse(
                    m.getId(), o.getId(),
                    m.getLote().getProducto().getId(), m.getLote().getProducto().getNombre(),
                    o.getTipo().getCodigo(), o.getTipo().getNombre(), o.getTipo().getSentido(),
                    m.getCantidad(), m.getLote().getId(), m.getLote().getNumeroLote(),
                    m.getLote().getCostoUnitario(), o.getNota(), o.getCreatedAt(), o.getUsuario().getEmail(),
                    corregidas.contains(o.getId()), o.getOperacionCorregida() != null);
        }).toList();
    }
}
