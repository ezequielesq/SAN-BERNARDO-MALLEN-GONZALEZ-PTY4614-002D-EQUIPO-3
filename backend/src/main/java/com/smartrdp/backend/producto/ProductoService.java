package com.smartrdp.backend.producto;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.producto.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;

    @Transactional
    public ProductoResponse crear(ProductoRequest request) {
        if (productoRepository.existsByCodigoPtb(request.codigoPtb())) {
            throw new BusinessException("El código PTB ya existe: " + request.codigoPtb());
        }
        var producto = new Producto();
        producto.setCodigoPtb(request.codigoPtb());
        producto.setNombre(request.nombre());
        producto.setCategoria(request.categoria());
        producto.setUnidadMedida(request.unidadMedida());
        producto.setEsPerecible(request.esPerecible());
        return toResponse(productoRepository.save(producto));
    }

    public List<ProductoResponse> listar(boolean soloActivos) {
        List<Producto> productos = soloActivos
                ? productoRepository.findByActivoTrue()
                : productoRepository.findAll();
        return productos.stream().map(this::toResponse).toList();
    }

    public ProductoResponse obtener(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public void deshabilitar(Long id) {
        Producto producto = findOrThrow(id);
        producto.setActivo(false);
    }

    private Producto findOrThrow(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
    }

    private ProductoResponse toResponse(Producto p) {
        return new ProductoResponse(
                p.getId(), p.getCodigoPtb(), p.getNombre(),
                p.getCategoria(), p.getUnidadMedida(), p.isEsPerecible(),
                p.getStockMinimo(), p.getStockCritico(), p.isActivo());
    }
}
