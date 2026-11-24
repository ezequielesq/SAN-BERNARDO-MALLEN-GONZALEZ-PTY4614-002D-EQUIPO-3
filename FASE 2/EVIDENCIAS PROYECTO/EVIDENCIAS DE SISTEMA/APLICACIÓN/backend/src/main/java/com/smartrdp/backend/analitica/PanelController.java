package com.smartrdp.backend.analitica;

import com.smartrdp.backend.analitica.dto.OpcionDto;
import com.smartrdp.backend.analitica.dto.PedidosPorPersonaDto;
import com.smartrdp.backend.analitica.dto.ProductoClaveDto;
import com.smartrdp.backend.analitica.dto.ProductoTopDto;
import com.smartrdp.backend.analitica.dto.ProductosClaveRequest;
import com.smartrdp.backend.analitica.dto.ResumenPanelDto;
import com.smartrdp.backend.analitica.dto.SerieConsumoDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final ProductoClaveService productosClave;

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

    @GetMapping("/consumo-periodo")
    public SerieConsumoDto consumoPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "SEMANA") Agrupacion agrupar,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) List<Long> productoIds,
            @RequestParam(required = false) Long solicitanteId) {
        return servicio.consumoPeriodo(FiltroPanel.de(desde, hasta, agrupar, categoriaId, productoIds, solicitanteId));
    }

    @GetMapping("/productos-top")
    public List<ProductoTopDto> productosTop(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "SEMANA") Agrupacion agrupar,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) List<Long> productoIds,
            @RequestParam(required = false) Long solicitanteId,
            @RequestParam(defaultValue = "7") int limite) {
        return servicio.productosTop(
                FiltroPanel.de(desde, hasta, agrupar, categoriaId, productoIds, solicitanteId), limite);
    }

    @GetMapping("/pedidos-por-persona")
    public PedidosPorPersonaDto pedidosPorPersona(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "SEMANA") Agrupacion agrupar,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) List<Long> productoIds,
            @RequestParam(required = false) Long solicitanteId) {
        return servicio.pedidosPorPersona(
                FiltroPanel.de(desde, hasta, agrupar, categoriaId, productoIds, solicitanteId));
    }

    @GetMapping("/solicitantes")
    public List<OpcionDto> solicitantes() {
        return servicio.solicitantes();
    }

    @GetMapping("/productos-clave")
    public List<ProductoClaveDto> productosClave() {
        return productosClave.listar();
    }

    @PutMapping("/productos-clave")
    public List<ProductoClaveDto> guardarProductosClave(@Valid @RequestBody ProductosClaveRequest request) {
        return productosClave.reemplazar(request.productoIds());
    }
}
