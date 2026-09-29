package com.smartrdp.backend.analitica;

import com.smartrdp.backend.analitica.dto.ConsumoDto;
import com.smartrdp.backend.movimiento.LoteRepository;
import com.smartrdp.backend.movimiento.MovimientoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnaliticaServiceTest {

    @Mock MovimientoRepository movimientoRepository;
    @Mock LoteRepository loteRepository;
    @InjectMocks AnaliticaService analiticaService;

    @Test
    void getProductosMasUsados_whenNoData_thenReturnsEmptyList() {
        when(movimientoRepository.findConsumoEntreFechas(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        var result = analiticaService.getProductosMasUsados(LocalDate.now().minusMonths(1), LocalDate.now(), null);
        assertThat(result).isEmpty();
    }

    @Test
    void getVencimientos_whenNoLotes_thenReturnsEmptyList() {
        when(loteRepository.findByFechaVencimientoBeforeAndCantidadDisponibleGreaterThan(any(), anyInt()))
                .thenReturn(Collections.emptyList());
        var result = analiticaService.getVencimientos(30);
        assertThat(result).isEmpty();
    }

    @Test
    void getConsumo_agrupadoPorProducto_whenAlgunosMovimientosSinCosto_thenValorEsSumaParcial() {
        Object[] fila = {1L, "Aceite", 100, 50000, 2, 40, 0, 0};
        when(movimientoRepository.findConsumoPorProducto(any(), any(), any()))
                .thenReturn(Arrays.asList(new Object[][] {fila}));

        var result = analiticaService.getConsumo(
                LocalDate.now().minusDays(7), LocalDate.now(), AgruparPor.PRODUCTO, null);

        assertThat(result).hasSize(1);
        ConsumoDto dto = result.get(0);
        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.nombre()).isEqualTo("Aceite");
        assertThat(dto.entradaCantidad()).isEqualTo(100);
        assertThat(dto.entradaValor()).isEqualTo(50000);
        assertThat(dto.salidaCantidad()).isEqualTo(40);
        assertThat(dto.salidaValor()).isNull();
    }

    @Test
    void getConsumo_agrupadoPorCategoria_whenSinMovimientos_thenListaVacia() {
        when(movimientoRepository.findConsumoPorCategoria(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        var result = analiticaService.getConsumo(
                LocalDate.now().minusDays(7), LocalDate.now(), AgruparPor.CATEGORIA, 3L);

        assertThat(result).isEmpty();
        verify(movimientoRepository).findConsumoPorCategoria(any(), any(), eq(3L));
    }
}
