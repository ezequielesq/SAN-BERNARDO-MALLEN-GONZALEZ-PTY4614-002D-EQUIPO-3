package com.smartrdp.backend.producto;

import com.smartrdp.backend.producto.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deshabilitar(@PathVariable Long id) {
        productoService.deshabilitar(id);
        return ResponseEntity.noContent().build();
    }
}
