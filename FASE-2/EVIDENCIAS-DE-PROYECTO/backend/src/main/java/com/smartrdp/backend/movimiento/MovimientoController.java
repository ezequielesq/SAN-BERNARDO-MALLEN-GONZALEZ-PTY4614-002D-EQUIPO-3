package com.smartrdp.backend.movimiento;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.movimiento.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/movimientos")
@RequiredArgsConstructor
public class MovimientoController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final MovimientoService movimientoService;
    private final EntradaImportacionService entradaImportacionService;

    @PostMapping("/entradas")
    public ResponseEntity<OperacionResponse> entrada(@Valid @RequestBody EntradaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movimientoService.registrarIngreso(request));
    }

    @GetMapping("/entradas/plantilla/xlsx")
    public ResponseEntity<byte[]> plantillaEntradas() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=plantilla-entradas.xlsx")
                .contentType(XLSX)
                .body(entradaImportacionService.plantillaXlsx());
    }

    /** Lee el Excel y devuelve cada fila validada; no guarda nada. */
    @PostMapping("/entradas/importar/leer")
    public List<FilaEntradaValidada> leerEntradas(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        String nombre = archivo.getOriginalFilename();
        if (archivo.isEmpty() || nombre == null || !nombre.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("Sube un archivo Excel con extensión .xlsx.");
        }
        return entradaImportacionService.leer(archivo.getInputStream());
    }

    /** Revalida filas editadas en la vista previa; no guarda nada. */
    @PostMapping("/entradas/importar/validar")
    public List<FilaEntradaValidada> validarEntradas(@Valid @RequestBody ImportacionEntradaRequest request) {
        return entradaImportacionService.validar(request.filas());
    }

    @PostMapping("/entradas/importar")
    public ResponseEntity<OperacionResponse> importarEntradas(@Valid @RequestBody ImportacionEntradaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(entradaImportacionService.importar(request));
    }

    @PostMapping("/salidas")
    public ResponseEntity<OperacionResponse> salida(@Valid @RequestBody SalidaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movimientoService.registrarSalida(request));
    }

    @PostMapping("/operaciones/{id}/correccion")
    public ResponseEntity<OperacionResponse> corregir(@PathVariable Long id,
                                                      @Valid @RequestBody(required = false) CorreccionRequest request) {
        String nota = request != null ? request.nota() : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(movimientoService.corregir(id, nota));
    }

    @GetMapping
    public List<MovimientoResponse> listar(
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return movimientoService.listar(productoId, desde, hasta);
    }

    @GetMapping("/tipos")
    public List<TipoOperacionResponse> tipos(@RequestParam(required = false) Integer sentido) {
        return movimientoService.listarTipos(sentido);
    }

    @GetMapping("/stock")
    public List<StockStatusResponse> listarStock() {
        return movimientoService.listarStock();
    }

    @GetMapping("/productos/{id}/stock")
    public StockStatusResponse stock(@PathVariable Long id) {
        return movimientoService.stockDe(id);
    }

    @GetMapping("/alertas/vencimiento")
    public List<AlertaVencimientoResponse> alertasVencimiento(@RequestParam(defaultValue = "7") int dias) {
        return movimientoService.getAlertasVencimiento(dias);
    }
}
