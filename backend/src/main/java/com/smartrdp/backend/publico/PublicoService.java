package com.smartrdp.backend.publico;

import com.smartrdp.backend.producto.Producto;
import com.smartrdp.backend.producto.ProductoRepository;
import com.smartrdp.backend.publico.dto.EmpleadoDto;
import com.smartrdp.backend.publico.dto.ProductoPublicoDto;
import com.smartrdp.backend.usuario.Rol;
import com.smartrdp.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicoService {

    private final UsuarioRepository usuarioRepository;
    private final ProductoRepository productoRepository;

    @Transactional(readOnly = true)
    public List<EmpleadoDto> listarEmpleados() {
        return usuarioRepository.findByRolAndActivoTrueOrderByNombreAsc(Rol.EMPLEADO).stream()
                .map(u -> new EmpleadoDto(u.getId(), u.getNombre()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductoPublicoDto> listarProductos() {
        return productoRepository.findByActivoTrue().stream()
                .sorted(Comparator.comparing(Producto::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(p -> new ProductoPublicoDto(p.getId(), p.getNombre(), p.getUnidadMedida().getNombre()))
                .toList();
    }
}
