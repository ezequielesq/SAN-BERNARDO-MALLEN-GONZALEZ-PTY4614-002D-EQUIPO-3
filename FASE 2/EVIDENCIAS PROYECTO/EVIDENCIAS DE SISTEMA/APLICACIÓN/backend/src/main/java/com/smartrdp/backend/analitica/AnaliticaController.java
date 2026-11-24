package com.smartrdp.backend.analitica;

import com.smartrdp.backend.analitica.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/analitica")
@RequiredArgsConstructor
public class AnaliticaController {

    private final AnaliticaService analiticaService;

    @GetMapping("/vencimientos")
    public List<VencimientoDto> vencimientos(
            @RequestParam(defaultValue = "30") int dias,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) List<Long> productoIds) {
        return analiticaService.getVencimientos(dias, categoriaId, productoIds);
    }

    @GetMapping("/vencimientos/rango")
    public List<VencimientoDto> vencimientosPorRango(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) List<Long> productoIds) {
        return analiticaService.getVencimientosPorRango(desde, hasta, categoriaId, productoIds);
    }

    @GetMapping("/exportar-movimientos")
    public ResponseEntity<byte[]> exportar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) List<Long> productoIds,
            @RequestParam(required = false) Long solicitanteId) throws IOException {
        byte[] data = analiticaService.exportarMovimientosExcel(desde, hasta, categoriaId, productoIds, solicitanteId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=movimientos.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }
}
