// src/test/java/com/smartrdp/backend/producto/ProductoServiceTest.java
package com.smartrdp.backend.producto;

import com.smartrdp.backend.catalogo.Categoria;
import com.smartrdp.backend.catalogo.CategoriaRepository;
import com.smartrdp.backend.catalogo.UnidadMedida;
import com.smartrdp.backend.catalogo.UnidadMedidaRepository;
import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.producto.dto.ProductoRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock ProductoRepository productoRepository;
    @Mock CategoriaRepository categoriaRepository;
    @Mock UnidadMedidaRepository unidadMedidaRepository;
    @InjectMocks ProductoService productoService;

    @Test
    void crear_whenCodigoPtbDuplicated_thenThrowsBusinessException() {
        when(productoRepository.existsByCodigoPtv("71149")).thenReturn(true);
        var req = new ProductoRequest("71149", "Ketchup", 1L, 1L, false, 0, 0, 0);
        assertThatThrownBy(() -> productoService.crear(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("código 71149");
    }

    @Test
    void crear_whenValid_thenSavesAndReturnsResponse() {
        when(productoRepository.existsByCodigoPtv("71149")).thenReturn(false);
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(new Categoria("ABARROTES")));
        when(unidadMedidaRepository.findById(1L)).thenReturn(Optional.of(new UnidadMedida("KG")));
        var producto = new Producto();
        when(productoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var req = new ProductoRequest("71149", "Ketchup", 1L, 1L, false, 0, 0, 0);
        var result = productoService.crear(req);
        assertThat(result.nombre()).isEqualTo("Ketchup");
    }

    @Test
    void deshabilitar_whenProductoNotFound_thenThrowsResourceNotFoundException() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> productoService.deshabilitar(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deshabilitar_whenProductoExists_thenSetsActivoFalse() {
        var producto = new Producto();
        producto.setActivo(true);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        productoService.deshabilitar(1L);
        assertThat(producto.isActivo()).isFalse();
    }
}
