// src/test/java/com/smartrdp/backend/producto/ProductoServiceTest.java
package com.smartrdp.backend.producto;

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
    @InjectMocks ProductoService productoService;

    @Test
    void crear_whenCodigoPtbDuplicated_thenThrowsBusinessException() {
        when(productoRepository.existsByCodigoPtb("71149")).thenReturn(true);
        var req = new ProductoRequest("Ketchup", "ABARROTES", "KG", false, "71149");
        assertThatThrownBy(() -> productoService.crear(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("código PTB ya existe");
    }

    @Test
    void crear_whenValid_thenSavesAndReturnsResponse() {
        when(productoRepository.existsByCodigoPtb("71149")).thenReturn(false);
        var producto = new Producto();
        producto.setNombre("Ketchup");
        when(productoRepository.save(any())).thenReturn(producto);
        var req = new ProductoRequest("Ketchup", "ABARROTES", "KG", false, "71149");
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
