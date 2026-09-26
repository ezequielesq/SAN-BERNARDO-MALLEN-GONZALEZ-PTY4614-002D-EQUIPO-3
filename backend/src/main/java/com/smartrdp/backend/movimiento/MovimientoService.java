package com.smartrdp.backend.movimiento;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.movimiento.dto.EntradaRequest;
import com.smartrdp.backend.movimiento.dto.MovimientoResponse;
import com.smartrdp.backend.movimiento.dto.SalidaRequest;
import com.smartrdp.backend.producto.ProductoRepository;
import com.smartrdp.backend.usuario.Usuario;
import com.smartrdp.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MovimientoService {

    private final LoteRepository loteRepository;
    private final MovimientoRepository movimientoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

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
        movimiento.setLote(lote);
        getCurrentUsuario().ifPresent(movimiento::setUsuario);
        return toResponse(movimientoRepository.save(movimiento));
    }

    @Transactional
    public MovimientoResponse registrarSalida(SalidaRequest request) {
        var producto = productoRepository.findById(request.productoId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto", request.productoId()));

        List<Lote> lotesFefo = loteRepository
                .findByProductoIdAndCantidadDisponibleGreaterThanOrderByFechaVencimientoAsc(
                        request.productoId(), 0);

        int stockTotal = lotesFefo.stream().mapToInt(Lote::getCantidadDisponible).sum();
        if (stockTotal < request.cantidad()) {
            throw new BusinessException("Stock insuficiente. Disponible: " + stockTotal
                    + ", solicitado: " + request.cantidad());
        }

        int restante = request.cantidad();
        Lote loteUsado = null;
        for (Lote lote : lotesFefo) {
            if (restante <= 0) break;
            int descontar = Math.min(lote.getCantidadDisponible(), restante);
            lote.setCantidadDisponible(lote.getCantidadDisponible() - descontar);
            restante -= descontar;
            loteUsado = lote;
        }

        var movimiento = new Movimiento();
        movimiento.setProducto(producto);
        movimiento.setTipo(TipoMovimiento.SALIDA);
        movimiento.setCantidad(request.cantidad());
        movimiento.setMotivo(request.motivo());
        movimiento.setLote(loteUsado);
        getCurrentUsuario().ifPresent(movimiento::setUsuario);
        return toResponse(movimientoRepository.save(movimiento));
    }

    public Integer getStockActual(Long productoId) {
        Integer entradas = movimientoRepository.sumEntradasByProductoId(productoId);
        Integer salidas = movimientoRepository.sumSalidasByProductoId(productoId);
        return (entradas == null ? 0 : entradas) - (salidas == null ? 0 : salidas);
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

    public Optional<Lote> getLoteFEFO(Long productoId) {
        return loteRepository
                .findByProductoIdAndCantidadDisponibleGreaterThanOrderByFechaVencimientoAsc(productoId, 0)
                .stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<MovimientoResponse> getAlertasVencimiento(int diasUmbral) {
        LocalDate umbral = LocalDate.now().plusDays(diasUmbral);
        return loteRepository
                .findByFechaVencimientoBeforeAndCantidadDisponibleGreaterThan(umbral, 0)
                .stream()
                .map(l -> {
                    var m = new Movimiento();
                    m.setProducto(l.getProducto());
                    m.setTipo(TipoMovimiento.ENTRADA);
                    m.setCantidad(l.getCantidadDisponible());
                    m.setMotivo("Alerta vencimiento: " + l.getFechaVencimiento());
                    return toResponse(m);
                }).toList();
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
                m.getCreatedAt()
        );
    }
}
