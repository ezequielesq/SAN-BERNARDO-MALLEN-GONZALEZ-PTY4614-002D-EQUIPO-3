package com.smartrdp.backend.catalogo;

import com.smartrdp.backend.catalogo.dto.CatalogoRequest;
import com.smartrdp.backend.catalogo.dto.CatalogoResponse;
import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UnidadMedidaService {

    private final UnidadMedidaRepository unidadMedidaRepository;

    @Transactional(readOnly = true)
    public List<CatalogoResponse> listar(boolean soloActivas) {
        List<UnidadMedida> unidades = soloActivas
                ? unidadMedidaRepository.findByActivoTrueOrderByNombreAsc()
                : unidadMedidaRepository.findAllByOrderByNombreAsc();
        return unidades.stream().map(CatalogoResponse::from).toList();
    }

    @Transactional
    public CatalogoResponse crear(CatalogoRequest request) {
        String nombre = request.nombre().trim();
        if (unidadMedidaRepository.existsByNombreIgnoreCase(nombre)) {
            throw duplicada(nombre);
        }
        return CatalogoResponse.from(unidadMedidaRepository.save(new UnidadMedida(nombre)));
    }

    @Transactional
    public CatalogoResponse renombrar(Long id, CatalogoRequest request) {
        String nombre = request.nombre().trim();
        UnidadMedida unidad = buscar(id);
        if (unidadMedidaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw duplicada(nombre);
        }
        unidad.setNombre(nombre);
        return CatalogoResponse.from(unidad);
    }

    @Transactional
    public void desactivar(Long id) {
        buscar(id).setActivo(false);
    }

    @Transactional
    public void reactivar(Long id) {
        buscar(id).setActivo(true);
    }

    private UnidadMedida buscar(Long id) {
        return unidadMedidaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", id));
    }

    private BusinessException duplicada(String nombre) {
        return new BusinessException("Ya existe la unidad de medida '" + nombre + "'.", "nombre");
    }
}
