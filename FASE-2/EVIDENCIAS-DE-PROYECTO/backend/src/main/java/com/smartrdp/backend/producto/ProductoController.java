package com.smartrdp.backend.producto;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.producto.dto.FilaValidada;
import com.smartrdp.backend.producto.dto.ImportacionRequest;
import com.smartrdp.backend.producto.dto.ProductoRequest;
import com.smartrdp.backend.producto.dto.ProductoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ProductoService productoService;
    private final ProductoImportacionService importacionService;

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

    @GetMapping("/exportar/xlsx")
    public ResponseEntity<byte[]> exportarXlsx(@RequestParam(defaultValue = "true") boolean soloActivos) {
        byte[] archivo = productoService.exportarXlsx(soloActivos);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=productos.xlsx")
                .contentType(XLSX)
                .body(archivo);
    }

    @GetMapping("/exportar/pdf")
    public ResponseEntity<byte[]> exportarPdf(@RequestParam(defaultValue = "true") boolean soloActivos) {
        byte[] archivo = productoService.exportarPdf(soloActivos);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=productos.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(archivo);
    }

    @GetMapping("/plantilla/xlsx")
    public ResponseEntity<byte[]> plantilla() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=plantilla-productos.xlsx")
                .contentType(XLSX)
                .body(importacionService.plantillaXlsx());
    }

    /** Lee el Excel y devuelve cada fila validada; no guarda nada. */
    @PostMapping("/importar/leer")
    public List<FilaValidada> leerImportacion(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        String nombre = archivo.getOriginalFilename();
        if (archivo.isEmpty() || nombre == null || !nombre.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("Sube un archivo Excel con extensión .xlsx.");
        }
        return importacionService.leer(archivo.getInputStream());
    }

    /** Revalida filas editadas en la vista previa; no guarda nada. */
    @PostMapping("/importar/validar")
    public List<FilaValidada> validarImportacion(@Valid @RequestBody ImportacionRequest request) {
        return importacionService.validar(request.filas());
    }

    @PostMapping("/importar")
    public ResponseEntity<List<ProductoResponse>> importar(@Valid @RequestBody ImportacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importacionService.importar(request.filas()));
    }
}
