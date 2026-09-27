package com.smartrdp.backend.producto;

import com.smartrdp.backend.producto.dto.ProductoRequest;
import com.smartrdp.backend.producto.dto.ProductoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    public List<ProductoResponse> listar(@RequestParam(defaultValue = "true") boolean soloActivos) {
        return productoService.listar(soloActivos);
    }

    @GetMapping("/{id}")
    public ProductoResponse obtener(@PathVariable Long id) {
        return productoService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productoService.crear(request));
    }

    @PutMapping("/{id}")
    public ProductoResponse editar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return productoService.editar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deshabilitar(@PathVariable Long id) {
        productoService.deshabilitar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reactivar")
    public ResponseEntity<Void> reactivar(@PathVariable Long id) {
        productoService.reactivar(id);
        return ResponseEntity.noContent().build();
    }
}
