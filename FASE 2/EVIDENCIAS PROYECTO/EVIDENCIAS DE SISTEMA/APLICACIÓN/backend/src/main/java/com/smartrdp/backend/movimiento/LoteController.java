package com.smartrdp.backend.movimiento;

import com.smartrdp.backend.movimiento.dto.LoteDetalleResponse;
import com.smartrdp.backend.movimiento.dto.LoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Consulta de lotes y su trazabilidad (solo lectura; los lotes nacen con las entradas). */
@RestController
@RequestMapping("/api/lotes")
@RequiredArgsConstructor
public class LoteController {

    private final LoteConsultaService loteConsultaService;

    @GetMapping
    public List<LoteResponse> listar(
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = LoteConsultaService.DIAS_POR_VENCER_POR_DEFECTO) int diasPorVencer,
            @RequestParam(defaultValue = "false") boolean incluirAgotados) {
        return loteConsultaService.listar(productoId, categoriaId, proveedorId, estado, texto, diasPorVencer,
                incluirAgotados);
    }

    @GetMapping("/{id}")
    public LoteDetalleResponse detalle(
            @PathVariable Long id,
            @RequestParam(defaultValue = LoteConsultaService.DIAS_POR_VENCER_POR_DEFECTO) int diasPorVencer) {
        return loteConsultaService.detalle(id, diasPorVencer);
    }
}
