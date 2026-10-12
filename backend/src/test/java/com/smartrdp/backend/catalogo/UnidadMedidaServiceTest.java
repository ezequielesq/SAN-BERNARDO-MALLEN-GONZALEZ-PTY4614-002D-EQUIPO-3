
package com.smartrdp.backend.catalogo;

import com.smartrdp.backend.catalogo.dto.CatalogoRequest;
import com.smartrdp.backend.exception.BusinessException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class UnidadMedidaServiceTest {

    @Mock
    private UnidadMedidaRepository unidadMedidaRepository;

    @InjectMocks
    private UnidadMedidaService unidadMedidaService;

    @Test
    void crearUnidad_cuandoNombreDuplicado_debeLanzarExcepcion() {

        CatalogoRequest request = new CatalogoRequest("Kilogramos");

        when(unidadMedidaRepository
                .existsByNombreIgnoreCase("Kilogramos"))
                .thenReturn(true);

        assertThatThrownBy(() -> unidadMedidaService.crear(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe la unidad de medida");

        verify(unidadMedidaRepository, never())
                .save(any(UnidadMedida.class));
    }

    
    @Test
    void renombrarUnidad_cuandoExiste_debeActualizarNombre() {

        Long unidadId = 1L;

        UnidadMedida unidad = new UnidadMedida("Kilogramos");
        CatalogoRequest request = new CatalogoRequest("Gramos");

        when(unidadMedidaRepository.findById(unidadId))
                .thenReturn(Optional.of(unidad));

        when(unidadMedidaRepository
                .existsByNombreIgnoreCaseAndIdNot("Gramos", unidadId))
                .thenReturn(false);
        unidadMedidaService.renombrar(unidadId, request);

        assertThat(unidad.getNombre()).isEqualTo("Gramos");

        verify(unidadMedidaRepository).findById(unidadId);

        verify(unidadMedidaRepository)
                .existsByNombreIgnoreCaseAndIdNot("Gramos", unidadId);
    }

    
    @Test
    void listarUnidades_cuandoSoloActivas_debeExcluirInactivas() {

        UnidadMedida kilogramos = new UnidadMedida("Kilogramos");
        kilogramos.setActivo(true);

        UnidadMedida litros = new UnidadMedida("Litros");
        litros.setActivo(true);

        UnidadMedida gramos = new UnidadMedida("Gramos");
        gramos.setActivo(false);

        when(unidadMedidaRepository.findByActivoTrueOrderByNombreAsc())
                .thenReturn(List.of(kilogramos, litros));

        var resultado = unidadMedidaService.listar(true);

        assertThat(resultado).hasSize(2);

        assertThat(resultado)
                .extracting("nombre")
                .containsExactly("Kilogramos", "Litros");

        assertThat(resultado)
                .allMatch(unidad -> unidad.activo());

        verify(unidadMedidaRepository, times(1))
                .findByActivoTrueOrderByNombreAsc();

        verify(unidadMedidaRepository, never())
                .findAllByOrderByNombreAsc();
    }


}
