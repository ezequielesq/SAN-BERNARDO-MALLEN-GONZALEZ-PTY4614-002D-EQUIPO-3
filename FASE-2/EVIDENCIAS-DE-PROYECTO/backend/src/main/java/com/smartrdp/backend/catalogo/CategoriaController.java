package com.smartrdp.backend.catalogo;

import com.smartrdp.backend.catalogo.dto.CatalogoRequest;
import com.smartrdp.backend.catalogo.dto.CategoriaResponse;
import com.smartrdp.backend.catalogo.dto.RequierePtvRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping
    public List<CategoriaResponse> listar(@RequestParam(defaultValue = "true") boolean soloActivas) {
        return categoriaService.listar(soloActivas);
    }

    @PostMapping
    public ResponseEntity<CategoriaResponse> crear(@Valid @RequestBody CatalogoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoriaService.crear(request));
    }

    @PutMapping("/{id}")
    public CategoriaResponse renombrar(@PathVariable Long id, @Valid @RequestBody CatalogoRequest request) {
        return categoriaService.renombrar(id, request);
    }

    @PutMapping("/{id}/requiere-ptv")
    public CategoriaResponse cambiarRequierePtv(@PathVariable Long id,
                                                @Valid @RequestBody RequierePtvRequest request) {
        return categoriaService.cambiarRequierePtv(id, request.requierePtv());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        categoriaService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reactivar")
    public ResponseEntity<Void> reactivar(@PathVariable Long id) {
        categoriaService.reactivar(id);
        return ResponseEntity.noContent().build();
    }
}
