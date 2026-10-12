package com.smartrdp.backend.catalogo;

import com.smartrdp.backend.catalogo.dto.CatalogoRequest;
import com.smartrdp.backend.catalogo.dto.CatalogoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/unidades-medida")
@RequiredArgsConstructor
public class UnidadMedidaController {

    private final UnidadMedidaService unidadMedidaService;

    @GetMapping
    public List<CatalogoResponse> listar(@RequestParam(defaultValue = "true") boolean soloActivas) {
        return unidadMedidaService.listar(soloActivas);
    }

    @PostMapping
    public ResponseEntity<CatalogoResponse> crear(@Valid @RequestBody CatalogoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(unidadMedidaService.crear(request));
    }

    @PutMapping("/{id}")
    public CatalogoResponse renombrar(@PathVariable Long id, @Valid @RequestBody CatalogoRequest request) {
        return unidadMedidaService.renombrar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        unidadMedidaService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reactivar")
    public ResponseEntity<Void> reactivar(@PathVariable Long id) {
        unidadMedidaService.reactivar(id);
        return ResponseEntity.noContent().build();
    }
}
