package com.smartrdp.backend.analitica;

import com.smartrdp.backend.movimiento.LoteRepository;
import com.smartrdp.backend.movimiento.MovimientoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
        when(movimientoRepository.findConsumoEntreFechas(any(), any()))
                .thenReturn(Collections.emptyList());
        var result = analiticaService.getProductosMasUsados(LocalDate.now().minusMonths(1), LocalDate.now());
        assertThat(result).isEmpty();
    }

    @Test
    void getVencimientos_whenNoLotes_thenReturnsEmptyList() {
        when(loteRepository.findByFechaVencimientoBeforeAndCantidadDisponibleGreaterThan(any(), anyInt()))
                .thenReturn(Collections.emptyList());
        var result = analiticaService.getVencimientos(30);
        assertThat(result).isEmpty();
    }
}
