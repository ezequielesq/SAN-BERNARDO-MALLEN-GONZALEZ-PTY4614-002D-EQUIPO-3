package com.smartrdp.backend.analitica;

import com.smartrdp.backend.analitica.dto.ResumenPanelDto;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Bloques del panel de analítica. Todos aceptan el mismo filtro; el acceso (ADMIN y BODEGUERO) lo da SecurityConfig. */
@RestController
@RequestMapping("/api/analitica/panel")
@RequiredArgsConstructor
public class PanelController {

    private final PanelService servicio;

    @GetMapping("/resumen")
    public ResumenPanelDto resumen(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "SEMANA") Agrupacion agrupar,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) List<Long> productoIds,
            @RequestParam(required = false) Long solicitanteId) {
        return servicio.resumen(FiltroPanel.de(desde, hasta, agrupar, categoriaId, productoIds, solicitanteId));
    }
}
