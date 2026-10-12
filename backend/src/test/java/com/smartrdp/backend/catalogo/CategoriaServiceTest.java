
package com.smartrdp.backend.catalogo;

import com.smartrdp.backend.catalogo.dto.CatalogoRequest;
import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private CategoriaService categoriaService;

    @Test
    void crearCategoria_cuandoNombreDuplicado_debeLanzarExcepcion() {

        CatalogoRequest request = new CatalogoRequest("Alimentos");

        when(categoriaRepository.existsByNombreIgnoreCase("Alimentos"))
                .thenReturn(true);

        assertThatThrownBy(() -> categoriaService.crear(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe la categoría");

        verify(categoriaRepository, never()).save(any(Categoria.class));
    }
    
    @Test
    void desactivarCategoria_cuandoExiste_debeCambiarEstadoAInactivo() {

        Long categoriaId = 1L;

        Categoria categoria = new Categoria("Alimentos");
        categoria.setActivo(true);

        when(categoriaRepository.findById(categoriaId))
                .thenReturn(Optional.of(categoria));


        assertThat(categoria.isActivo()).isTrue();


        categoriaService.desactivar(categoriaId);

        assertThat(categoria.isActivo()).isFalse();

        verify(categoriaRepository, times(1))
                .findById(categoriaId);

        verify(categoriaRepository, never())
                .delete(any(Categoria.class));

        verify(categoriaRepository, never())
                .deleteById(anyLong());
                
    }
    
    @Test
    void reactivarCategoria_cuandoEstaInactiva_debeActivarla() {

        Long categoriaId = 2L;

        Categoria categoria = new Categoria("Bebidas");
        categoria.setActivo(false);

        when(categoriaRepository.findById(categoriaId))
                .thenReturn(Optional.of(categoria));
        assertThat(categoria.isActivo()).isFalse();

        categoriaService.reactivar(categoriaId);

        assertThat(categoria.isActivo()).isTrue();

        verify(categoriaRepository, times(1))
                .findById(categoriaId);

        verify(categoriaRepository, never())
                .delete(any(Categoria.class));
    }

    
    @Test
    void desactivarCategoria_cuandoNoExiste_debeLanzarExcepcion() {

        Long categoriaId = 999L;

        when(categoriaRepository.findById(categoriaId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                categoriaService.desactivar(categoriaId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(categoriaRepository, times(1))
                .findById(categoriaId);

        verify(categoriaRepository, never())
                .save(any(Categoria.class));

        verify(categoriaRepository, never())
                .delete(any(Categoria.class));

        verify(categoriaRepository, never())
                .deleteById(anyLong());
    }

}
