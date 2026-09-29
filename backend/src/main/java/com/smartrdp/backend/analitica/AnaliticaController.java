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

    @GetMapping("/mas-usados")
    public List<ProductoMasUsadoDto> masUsados(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long categoriaId) {
        return analiticaService.getProductosMasUsados(desde, hasta, categoriaId);
    }

    @GetMapping("/vencimientos")
    public List<VencimientoDto> vencimientos(@RequestParam(defaultValue = "30") int dias) {
        return analiticaService.getVencimientos(dias);
    }

    @GetMapping("/exportar-movimientos")
    public ResponseEntity<byte[]> exportar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) throws IOException {
        byte[] data = analiticaService.exportarMovimientosExcel(desde, hasta);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=movimientos.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @GetMapping("/consumo")
    public List<ConsumoDto> consumo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam AgruparPor agruparPor,
            @RequestParam(required = false) Long categoriaId) {
        return analiticaService.getConsumo(desde, hasta, agruparPor, categoriaId);
    }
}
