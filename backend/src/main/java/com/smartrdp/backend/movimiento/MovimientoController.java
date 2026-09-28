package com.smartrdp.backend.movimiento;

import com.smartrdp.backend.movimiento.dto.EntradaRequest;
import com.smartrdp.backend.movimiento.dto.MovimientoResponse;
import com.smartrdp.backend.movimiento.dto.SalidaRequest;
import com.smartrdp.backend.movimiento.dto.StockStatusResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/movimientos")
@RequiredArgsConstructor
public class MovimientoController {

    private final MovimientoService movimientoService;

    @PostMapping("/entradas")
    public ResponseEntity<MovimientoResponse> entrada(
            @Valid @RequestBody EntradaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(movimientoService.registrarEntrada(request));
    }

    @PostMapping("/salidas")
    public ResponseEntity<MovimientoResponse> salida(
            @Valid @RequestBody SalidaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(movimientoService.registrarSalida(request));
    }

    @GetMapping
    public List<MovimientoResponse> listar(
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return movimientoService.listar(productoId, desde, hasta);
    }

    @GetMapping("/productos/{id}/stock")
    public StockStatusResponse stock(@PathVariable Long id) {
        int stockActual = movimientoService.getStockActual(id);
        EstadoStock estado = movimientoService.getEstadoStock(id);
        var loteFefo = movimientoService.getLoteFEFO(id);
        return new StockStatusResponse(id, null, stockActual, estado,
                loteFefo.map(Lote::getFechaVencimiento).orElse(null));
    }

    @GetMapping("/alertas/vencimiento")
    public List<MovimientoResponse> alertasVencimiento(
            @RequestParam(defaultValue = "7") int dias) {
        return movimientoService.getAlertasVencimiento(dias);
    }
}
