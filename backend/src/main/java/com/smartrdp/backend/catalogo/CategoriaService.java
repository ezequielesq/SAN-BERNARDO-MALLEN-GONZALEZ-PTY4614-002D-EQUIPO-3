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
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public List<CatalogoResponse> listar(boolean soloActivas) {
        List<Categoria> categorias = soloActivas
                ? categoriaRepository.findByActivoTrueOrderByNombreAsc()
                : categoriaRepository.findAllByOrderByNombreAsc();
        return categorias.stream().map(CatalogoResponse::from).toList();
    }

    @Transactional
    public CatalogoResponse crear(CatalogoRequest request) {
        String nombre = request.nombre().trim();
        if (categoriaRepository.existsByNombreIgnoreCase(nombre)) {
            throw duplicada(nombre);
        }
        return CatalogoResponse.from(categoriaRepository.save(new Categoria(nombre)));
    }

    @Transactional
    public CatalogoResponse renombrar(Long id, CatalogoRequest request) {
        String nombre = request.nombre().trim();
        Categoria categoria = buscar(id);
        if (categoriaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw duplicada(nombre);
        }
        categoria.setNombre(nombre);
        return CatalogoResponse.from(categoria);
    }

    @Transactional
    public void desactivar(Long id) {
        buscar(id).setActivo(false);
    }

    @Transactional
    public void reactivar(Long id) {
        buscar(id).setActivo(true);
    }

    private Categoria buscar(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", id));
    }

    private BusinessException duplicada(String nombre) {
        return new BusinessException("Ya existe la categoría '" + nombre + "'.", "nombre");
    }
}
