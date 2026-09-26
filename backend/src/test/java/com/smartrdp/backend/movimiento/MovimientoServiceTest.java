package com.smartrdp.backend.movimiento;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.movimiento.dto.SalidaRequest;
import com.smartrdp.backend.producto.Producto;
import com.smartrdp.backend.producto.ProductoRepository;
import com.smartrdp.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovimientoServiceTest {

    @Mock LoteRepository loteRepository;
    @Mock MovimientoRepository movimientoRepository;
    @Mock ProductoRepository productoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @InjectMocks MovimientoService movimientoService;

    @Test
    void registrarSalida_whenStockInsuficiente_thenThrowsBusinessException() {
        var producto = new Producto();
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(loteRepository.findByProductoIdAndCantidadDisponibleGreaterThanOrderByFechaVencimientoAsc(1L, 0))
                .thenReturn(Collections.emptyList());

        var req = new SalidaRequest(1L, 5, "Cocina", null);
        assertThatThrownBy(() -> movimientoService.registrarSalida(req, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Stock insuficiente");
    }

    @Test
    void getStockActual_whenNoMovements_thenReturnsZero() {
        when(movimientoRepository.sumEntradasByProductoId(1L)).thenReturn(null);
        when(movimientoRepository.sumSalidasByProductoId(1L)).thenReturn(null);
        assertThat(movimientoService.getStockActual(1L)).isZero();
    }

    @Test
    void getEstadoStock_whenStockCero_thenRetornaAgotado() {
        var producto = new Producto();
        producto.setStockMinimo(5);
        producto.setStockCritico(2);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(movimientoRepository.sumEntradasByProductoId(1L)).thenReturn(0);
        when(movimientoRepository.sumSalidasByProductoId(1L)).thenReturn(0);
        assertThat(movimientoService.getEstadoStock(1L)).isEqualTo(EstadoStock.AGOTADO);
    }

    @Test
    void getEstadoStock_whenStockNormal_thenRetornaActivo() {
        var producto = new Producto();
        producto.setStockMinimo(5);
        producto.setStockCritico(2);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(movimientoRepository.sumEntradasByProductoId(1L)).thenReturn(20);
        when(movimientoRepository.sumSalidasByProductoId(1L)).thenReturn(3);
        assertThat(movimientoService.getEstadoStock(1L)).isEqualTo(EstadoStock.NORMAL);
    }

    @Test
    void getLoteFEFO_whenLotesExisten_thenRetornaPrimeroEnVencer() {
        var lote1 = new Lote(); lote1.setFechaVencimiento(LocalDate.of(2026, 10, 5));
        var lote2 = new Lote(); lote2.setFechaVencimiento(LocalDate.of(2026, 10, 20));
        when(loteRepository.findByProductoIdAndCantidadDisponibleGreaterThanOrderByFechaVencimientoAsc(1L, 0))
                .thenReturn(List.of(lote1, lote2));
        var result = movimientoService.getLoteFEFO(1L);
        assertThat(result).isPresent();
        assertThat(result.get().getFechaVencimiento()).isEqualTo(LocalDate.of(2026, 10, 5));
    }
}
