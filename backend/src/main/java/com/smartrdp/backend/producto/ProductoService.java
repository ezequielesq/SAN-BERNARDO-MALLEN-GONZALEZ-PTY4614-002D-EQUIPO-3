package com.smartrdp.backend.producto;

import com.smartrdp.backend.catalogo.Categoria;
import com.smartrdp.backend.catalogo.CategoriaRepository;
import com.smartrdp.backend.catalogo.UnidadMedida;
import com.smartrdp.backend.catalogo.UnidadMedidaRepository;
import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.producto.dto.ProductoRequest;
import com.smartrdp.backend.producto.dto.ProductoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;

    @Transactional
    public ProductoResponse crear(ProductoRequest request) {
        validarUmbrales(request);
        String codigoPtb = request.codigoPtb().trim();
        if (productoRepository.existsByCodigoPtb(codigoPtb)) {
            throw ptbDuplicado(codigoPtb);
        }
        Producto producto = new Producto();
        aplicar(producto, request, codigoPtb);
        return toResponse(productoRepository.save(producto));
    }

    @Transactional
    public ProductoResponse editar(Long id, ProductoRequest request) {
        Producto producto = findOrThrow(id);
        validarUmbrales(request);
        String codigoPtb = request.codigoPtb().trim();
        if (productoRepository.existsByCodigoPtbAndIdNot(codigoPtb, id)) {
            throw ptbDuplicado(codigoPtb);
        }
        aplicar(producto, request, codigoPtb);
        return toResponse(producto);
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> listar(boolean soloActivos) {
        List<Producto> productos = soloActivos
                ? productoRepository.findByActivoTrue()
                : productoRepository.findAll();
        return productos.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public void deshabilitar(Long id) {
        findOrThrow(id).setActivo(false);
    }

    @Transactional
    public void reactivar(Long id) {
        findOrThrow(id).setActivo(true);
    }

    private void aplicar(Producto producto, ProductoRequest request, String codigoPtb) {
        producto.setCodigoPtb(codigoPtb);
        producto.setNombre(request.nombre().trim());
        producto.setCategoria(resolverCategoria(request.categoriaId(), producto.getCategoria()));
        producto.setUnidadMedida(resolverUnidad(request.unidadMedidaId(), producto.getUnidadMedida()));
        producto.setEsPerecible(request.esPerecible());
        producto.setStockMinimo(request.stockMinimo());
        producto.setStockCritico(request.stockCritico());
    }

    private void validarUmbrales(ProductoRequest request) {
        if (request.stockCritico() > request.stockMinimo()) {
            throw new BusinessException(
                    "El stock crítico debe ser menor o igual al stock mínimo.", "stockCritico");
        }
    }

    private Categoria resolverCategoria(Long id, Categoria actual) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", id));
        boolean esLaActual = actual != null && id.equals(actual.getId());
        if (!categoria.isActivo() && !esLaActual) {
            throw new BusinessException(
                    "La categoría '" + categoria.getNombre() + "' está desactivada.", "categoriaId");
        }
        return categoria;
    }

    private UnidadMedida resolverUnidad(Long id, UnidadMedida actual) {
        UnidadMedida unidad = unidadMedidaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", id));
        boolean esLaActual = actual != null && id.equals(actual.getId());
        if (!unidad.isActivo() && !esLaActual) {
            throw new BusinessException(
                    "La unidad de medida '" + unidad.getNombre() + "' está desactivada.", "unidadMedidaId");
        }
        return unidad;
    }

    private BusinessException ptbDuplicado(String codigoPtb) {
        return new BusinessException("Ya existe un producto con el código " + codigoPtb + ".", "codigoPtb");
    }

    private Producto findOrThrow(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
    }

    private ProductoResponse toResponse(Producto p) {
        return new ProductoResponse(
                p.getId(),
                p.getCodigoPtb(),
                p.getNombre(),
                p.getCategoria().getId(),
                p.getCategoria().getNombre(),
                p.getUnidadMedida().getId(),
                p.getUnidadMedida().getNombre(),
                p.isEsPerecible(),
                p.getStockMinimo(),
                p.getStockCritico(),
                p.isActivo());
    }
}
