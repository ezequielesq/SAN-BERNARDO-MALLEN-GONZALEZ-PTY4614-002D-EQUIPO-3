package com.smartrdp.backend.movimiento;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.movimiento.dto.AlertaVencimientoResponse;
import com.smartrdp.backend.movimiento.dto.EntradaRequest;
import com.smartrdp.backend.movimiento.dto.MovimientoResponse;
import com.smartrdp.backend.movimiento.dto.SalidaRequest;
import com.smartrdp.backend.movimiento.dto.StockStatusResponse;
import com.smartrdp.backend.producto.Producto;
import com.smartrdp.backend.producto.ProductoRepository;
import com.smartrdp.backend.usuario.Usuario;
import com.smartrdp.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovimientoService {

    private final LoteRepository loteRepository;
    private final MovimientoRepository movimientoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConsumoLoteRepository consumoLoteRepository;

    @Transactional
    public MovimientoResponse registrarEntrada(EntradaRequest request) {
        var producto = productoRepository.findById(request.productoId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto", request.productoId()));

        var lote = new Lote();
        lote.setProducto(producto);
        lote.setCantidadOriginal(request.cantidad());
        lote.setCantidadDisponible(request.cantidad());
        lote.setFechaVencimiento(request.fechaVencimiento());
        lote.setNumeroLote(request.numeroLote());
        loteRepository.save(lote);

        var movimiento = new Movimiento();
        movimiento.setProducto(producto);
        movimiento.setTipo(TipoMovimiento.ENTRADA);
        movimiento.setCantidad(request.cantidad());
        movimiento.setMotivo(request.motivo());
        movimiento.setCostoUnitario(producto.getCostoUnitario());
        movimiento.setLote(lote);
        getCurrentUsuario().ifPresent(movimiento::setUsuario);
        return toResponse(movimientoRepository.save(movimiento));
    }

    @Transactional
    public MovimientoResponse registrarSalida(SalidaRequest request) {
        var producto = productoRepository.findById(request.productoId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto", request.productoId()));
        return toResponse(descontarFefo(producto, request.cantidad(), request.motivo(), TipoMovimiento.SALIDA));
    }

    /** Igual que una salida, pero con tipo SOLICITADO (pedido aprobado de un empleado). */
    @Transactional
    public Movimiento registrarSolicitado(Long productoId, int cantidad, String motivo) {
        var producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", productoId));
        return descontarFefo(producto, cantidad, motivo, TipoMovimiento.SOLICITADO);
    }

    /**
     * Repone la cantidad devuelta en los mismos lotes de los que salió el movimiento origen,
     * empezando por el último lote consumido, y registra un movimiento DEVOLUCION.
     */
    @Transactional
    public Movimiento registrarDevolucion(Movimiento movimientoOrigen, int cantidad, String motivo) {
        List<ConsumoLote> consumos =
                consumoLoteRepository.findByMovimientoIdOrderByIdDesc(movimientoOrigen.getId());
        if (consumos.isEmpty()) {
            throw new BusinessException("Este producto no puede devolverse: no tiene registro de lotes.");
        }

        int restante = cantidad;
        Lote ultimoLote = null;
        for (ConsumoLote consumo : consumos) {
            if (restante <= 0) break;
            int pendiente = consumo.getCantidad() - consumo.getCantidadDevuelta();
            if (pendiente <= 0) continue;
            int reponer = Math.min(pendiente, restante);
            Lote lote = consumo.getLote();
            lote.setCantidadDisponible(lote.getCantidadDisponible() + reponer);
            consumo.setCantidadDevuelta(consumo.getCantidadDevuelta() + reponer);
            restante -= reponer;
            ultimoLote = lote;
        }
        if (restante > 0) {
            throw new BusinessException("La cantidad a devolver supera lo entregado de este producto.");
        }

        var movimiento = new Movimiento();
        movimiento.setProducto(movimientoOrigen.getProducto());
        movimiento.setTipo(TipoMovimiento.DEVOLUCION);
        movimiento.setCantidad(cantidad);
        movimiento.setMotivo(motivo);
        movimiento.setCostoUnitario(movimientoOrigen.getCostoUnitario());
        movimiento.setLote(ultimoLote);
        getCurrentUsuario().ifPresent(movimiento::setUsuario);
        return movimientoRepository.save(movimiento);
    }

    private Movimiento descontarFefo(Producto producto, int cantidad, String motivo, TipoMovimiento tipo) {
        List<Lote> lotesFefo = loteRepository
                .findByProductoIdAndCantidadDisponibleGreaterThanOrderByFechaVencimientoAsc(
                        producto.getId(), 0);

        int stockTotal = lotesFefo.stream().mapToInt(Lote::getCantidadDisponible).sum();
        if (stockTotal < cantidad) {
            throw new BusinessException("Stock insuficiente. Disponible: " + stockTotal
                    + ", solicitado: " + cantidad);
        }

        int restante = cantidad;
        Lote loteUsado = null;
        List<ConsumoLote> consumos = new ArrayList<>();
        for (Lote lote : lotesFefo) {
            if (restante <= 0) break;
            int descontar = Math.min(lote.getCantidadDisponible(), restante);
            lote.setCantidadDisponible(lote.getCantidadDisponible() - descontar);
            restante -= descontar;
            loteUsado = lote;

            var consumo = new ConsumoLote();
            consumo.setLote(lote);
            consumo.setCantidad(descontar);
            consumos.add(consumo);
        }

        var movimiento = new Movimiento();
        movimiento.setProducto(producto);
        movimiento.setTipo(tipo);
        movimiento.setCantidad(cantidad);
        movimiento.setMotivo(motivo);
        movimiento.setCostoUnitario(producto.getCostoUnitario());
        movimiento.setLote(loteUsado);
        getCurrentUsuario().ifPresent(movimiento::setUsuario);
        Movimiento guardado = movimientoRepository.save(movimiento);

        for (ConsumoLote consumo : consumos) {
            consumo.setMovimiento(guardado);
            consumoLoteRepository.save(consumo);
        }
        return guardado;
    }

    @Transactional(readOnly = true)
    public List<MovimientoResponse> listar(Long productoId, LocalDate desde, LocalDate hasta) {
        LocalDate baseParaDesde = hasta != null ? hasta : LocalDate.now();
        LocalDateTime desdeDt = (desde != null ? desde : baseParaDesde.minusDays(30)).atStartOfDay();
        LocalDateTime hastaDt = (hasta != null ? hasta : LocalDate.now()).atTime(23, 59, 59);
        List<Movimiento> movimientos = productoId != null
                ? movimientoRepository.findByProductoIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        productoId, desdeDt, hastaDt)
                : movimientoRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(desdeDt, hastaDt);
        return movimientos.stream().map(this::toResponse).toList();
    }

    public Integer getStockActual(Long productoId) {
        Long stock = movimientoRepository.sumStockByProductoId(productoId);
        return stock == null ? 0 : stock.intValue();
    }

    public EstadoStock getEstadoStock(Long productoId) {
        var producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", productoId));
        int stock = getStockActual(productoId);
        if (stock <= 0) return EstadoStock.AGOTADO;
        if (stock <= producto.getStockCritico()) return EstadoStock.CRITICO;
        if (stock <= producto.getStockMinimo()) return EstadoStock.BAJO;
        return EstadoStock.NORMAL;
    }

    @Transactional(readOnly = true)
    public List<StockStatusResponse> listarStock() {
        Map<Long, Integer> stockPorProducto = movimientoRepository.sumStockPorProducto().stream()
                .collect(Collectors.toMap(
                        fila -> (Long) fila[0],
                        fila -> ((Number) fila[1]).intValue()));

        return productoRepository.findByActivoTrue().stream()
                .map(producto -> {
                    int stock = stockPorProducto.getOrDefault(producto.getId(), 0);
                    EstadoStock estado;
                    if (stock <= 0) estado = EstadoStock.AGOTADO;
                    else if (stock <= producto.getStockCritico()) estado = EstadoStock.CRITICO;
                    else if (stock <= producto.getStockMinimo()) estado = EstadoStock.BAJO;
                    else estado = EstadoStock.NORMAL;

                    LocalDate proximoVencimiento = loteRepository
                            .findByProductoIdAndCantidadDisponibleGreaterThanOrderByFechaVencimientoAsc(
                                    producto.getId(), 0)
                            .stream().findFirst().map(Lote::getFechaVencimiento).orElse(null);

                    Integer costoUnitario = producto.getCostoUnitario();
                    Integer valorTotal = costoUnitario != null ? stock * costoUnitario : null;

                    return new StockStatusResponse(
                            producto.getId(), producto.getNombre(), stock, estado, proximoVencimiento,
                            costoUnitario, valorTotal);
                })
                .toList();
    }

    public Optional<Lote> getLoteFEFO(Long productoId) {
        return loteRepository
                .findByProductoIdAndCantidadDisponibleGreaterThanOrderByFechaVencimientoAsc(productoId, 0)
                .stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<AlertaVencimientoResponse> getAlertasVencimiento(int diasUmbral) {
        LocalDate hoy = LocalDate.now();
        LocalDate umbral = hoy.plusDays(diasUmbral);
        return loteRepository
                .findByFechaVencimientoBeforeAndCantidadDisponibleGreaterThan(umbral, 0)
                .stream()
                .map(l -> new AlertaVencimientoResponse(
                        l.getId(),
                        l.getProducto().getId(),
                        l.getProducto().getNombre(),
                        l.getNumeroLote(),
                        l.getCantidadDisponible(),
                        l.getFechaVencimiento(),
                        l.getFechaVencimiento().isBefore(hoy)))
                .toList();
    }

    private Optional<Usuario> getCurrentUsuario() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        return usuarioRepository.findByEmailAndActivoTrue(auth.getName());
    }

    private MovimientoResponse toResponse(Movimiento m) {
        return new MovimientoResponse(
                m.getId(),
                m.getProducto().getId(),
                m.getProducto().getNombre(),
                m.getTipo(),
                m.getCantidad(),
                m.getMotivo(),
                m.getCostoUnitario(),
                m.getCreatedAt(),
                m.getUsuario() != null ? m.getUsuario().getEmail() : null
        );
    }
}
