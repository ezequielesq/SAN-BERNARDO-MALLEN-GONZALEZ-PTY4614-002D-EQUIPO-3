package com.smartrdp.backend.catalogo;

import com.smartrdp.backend.catalogo.dto.ProveedorRequest;
import com.smartrdp.backend.catalogo.dto.ProveedorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/proveedores")
@RequiredArgsConstructor
public class ProveedorController {

    private final ProveedorService proveedorService;

    @GetMapping
    public List<ProveedorResponse> listar(@RequestParam(defaultValue = "true") boolean soloActivos) {
        return proveedorService.listar(soloActivos);
    }

    @PostMapping
    public ResponseEntity<ProveedorResponse> crear(@Valid @RequestBody ProveedorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(proveedorService.crear(request));
    }

    @PutMapping("/{id}")
    public ProveedorResponse editar(@PathVariable Long id, @Valid @RequestBody ProveedorRequest request) {
        return proveedorService.editar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        proveedorService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reactivar")
    public ResponseEntity<Void> reactivar(@PathVariable Long id) {
        proveedorService.reactivar(id);
        return ResponseEntity.noContent().build();
    }
}
