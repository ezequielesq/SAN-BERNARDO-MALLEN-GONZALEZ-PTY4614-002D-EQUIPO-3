package com.smartrdp.backend.analitica;

import com.smartrdp.backend.analitica.dto.ProductoClaveDto;
import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.producto.Producto;
import com.smartrdp.backend.producto.ProductoRepository;
import com.smartrdp.backend.usuario.Usuario;
import com.smartrdp.backend.usuario.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Productos clave del usuario autenticado: se leen y se guardan como conjunto completo. */
@Service
@RequiredArgsConstructor
public class ProductoClaveService {

    private final ProductoClaveRepository repositorio;
    private final ProductoRepository productoRepository;
    private final UsuarioActual usuarioActual;

    @Transactional(readOnly = true)
    public List<ProductoClaveDto> listar() {
        return repositorio.activosDe(usuarioActual.obligatorio().getId()).stream()
                .map(pc -> new ProductoClaveDto(pc.getProducto().getId(), pc.getProducto().getNombre()))
                .toList();
    }

    @Transactional
    public List<ProductoClaveDto> reemplazar(List<Long> productoIds) {
        List<Long> ids = productoIds.stream().distinct().toList();
        if (ids.size() > FiltroPanel.MAXIMO_PRODUCTOS) {
            throw new BusinessException("Elige como máximo " + FiltroPanel.MAXIMO_PRODUCTOS
                    + " productos clave.", "productoIds");
        }
        List<Producto> productos = productoRepository.findAllById(ids);
        if (productos.size() != ids.size()) {
            throw new BusinessException("Alguno de los productos elegidos ya no existe.", "productoIds");
        }
        Usuario usuario = usuarioActual.obligatorio();
        repositorio.borrarDe(usuario.getId());
        repositorio.saveAll(productos.stream().map(p -> {
            ProductoClave clave = new ProductoClave();
            clave.setUsuario(usuario);
            clave.setProducto(p);
            return clave;
        }).toList());
        repositorio.flush();
        return listar();
    }
}
