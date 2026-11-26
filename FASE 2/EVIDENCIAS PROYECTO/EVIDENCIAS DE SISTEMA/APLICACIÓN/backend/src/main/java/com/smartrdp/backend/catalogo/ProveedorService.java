package com.smartrdp.backend.catalogo;

import com.smartrdp.backend.catalogo.dto.ProveedorRequest;
import com.smartrdp.backend.catalogo.dto.ProveedorResponse;
import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProveedorService {

    private final ProveedorRepository repositorio;

    @Transactional(readOnly = true)
    public List<ProveedorResponse> listar(boolean soloActivos) {
        List<Proveedor> proveedores = soloActivos
                ? repositorio.findByActivoTrueOrderByNombreAsc()
                : repositorio.findAllByOrderByNombreAsc();
        return proveedores.stream().map(ProveedorResponse::from).toList();
    }

    @Transactional
    public ProveedorResponse crear(ProveedorRequest request) {
        String rut = Rut.normalizar(request.rut());
        String nombre = request.nombre().trim();
        if (repositorio.existsByRut(rut)) throw rutRepetido(rut);
        if (repositorio.existsByNombreIgnoreCase(nombre)) throw nombreRepetido(nombre);
        return ProveedorResponse.from(repositorio.save(new Proveedor(rut, nombre)));
    }

    @Transactional
    public ProveedorResponse editar(Long id, ProveedorRequest request) {
        Proveedor proveedor = buscar(id);
        String rut = Rut.normalizar(request.rut());
        String nombre = request.nombre().trim();
        if (repositorio.existsByRutAndIdNot(rut, id)) throw rutRepetido(rut);
        if (repositorio.existsByNombreIgnoreCaseAndIdNot(nombre, id)) throw nombreRepetido(nombre);
        proveedor.setRut(rut);
        proveedor.setNombre(nombre);
        return ProveedorResponse.from(proveedor);
    }

    @Transactional
    public void desactivar(Long id) {
        buscar(id).setActivo(false);
    }

    @Transactional
    public void reactivar(Long id) {
        buscar(id).setActivo(true);
    }

    private Proveedor buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));
    }

    private BusinessException rutRepetido(String rut) {
        return new BusinessException("Ya existe un proveedor con el RUT " + rut + ".", "rut");
    }

    private BusinessException nombreRepetido(String nombre) {
        return new BusinessException("Ya existe el proveedor '" + nombre + "'.", "nombre");
    }
}
