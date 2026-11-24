package com.smartrdp.backend.analitica;

import com.smartrdp.backend.analitica.dto.ResumenPanelDto;
import com.smartrdp.backend.movimiento.VistasInventario;
import com.smartrdp.backend.producto.Producto;
import com.smartrdp.backend.producto.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.smartrdp.backend.movimiento.TipoOperacion.DEVOLUCION;
import static com.smartrdp.backend.movimiento.TipoOperacion.ENTREGA_PEDIDO;
import static com.smartrdp.backend.movimiento.TipoOperacion.MERMA;
import static com.smartrdp.backend.movimiento.TipoOperacion.VENCIMIENTO;

/**
 * Cálculos del panel sobre el libro. Consumo = entregas de pedidos menos devoluciones; las pérdidas (merma y
 * vencimiento) van aparte; el consumo interno y los ajustes no entran. La agrupación por período y las reglas de
 * unidades se resuelven aquí, en Java, para no depender de funciones de la base.
 */
@Service
@RequiredArgsConstructor
public class PanelService {

    private static final List<String> TIPOS_CONSUMO = List.of(ENTREGA_PEDIDO, DEVOLUCION);
    private static final List<String> TIPOS_CONSUMO_Y_PERDIDA = List.of(ENTREGA_PEDIDO, DEVOLUCION, MERMA, VENCIMIENTO);

    private final PanelRepository repositorio;
    private final VistasInventario vistas;
    private final ProductoRepository productoRepository;

    /** Una línea de lo solicitado, ya filtrada por categoría y productos del filtro. */
    private record Solicitado(long solicitudId, long productoId, long cantidad, long valor, long unidadId,
                              String unidad) {}

    @Transactional(readOnly = true)
    public ResumenPanelDto resumen(FiltroPanel f) {
        List<LineaPanel> lineas = lineas(f, TIPOS_CONSUMO_Y_PERDIDA);
        List<LineaPanel> entregas = deTipo(lineas, ENTREGA_PEDIDO);
        List<LineaPanel> devoluciones = deTipo(lineas, DEVOLUCION);
        List<LineaPanel> perdidas = lineas.stream().filter(l -> !TIPOS_CONSUMO.contains(l.tipo())).toList();
        // Las cantidades solo se suman si todos los productos (consumo y solicitado) comparten unidad.
        List<Solicitado> solicitado = solicitado(f, entregas);
        String unidad = unidadComun(Stream.concat(entregas.stream(), devoluciones.stream()).toList(), solicitado);
        boolean hayCantidad = unidad != null;
        return new ResumenPanelDto(
                valor(entregas) - valor(devoluciones),
                hayCantidad ? cantidad(entregas) - cantidad(devoluciones) : null, unidad,
                entregas.stream().map(LineaPanel::solicitudId).distinct().count(),
                entregas.stream().map(LineaPanel::solicitanteId).distinct().count(),
                solicitado.stream().mapToLong(Solicitado::valor).sum(), valor(entregas),
                hayCantidad ? solicitado.stream().mapToLong(Solicitado::cantidad).sum() : null,
                hayCantidad ? cantidad(entregas) : null,
                valor(perdidas), lineas.stream().filter(l -> l.costo() == null).count());
    }

    // ------------------------------------------------------------------ solicitado

    /**
     * Lo pedido en los pedidos con entrega en el rango. Se valoriza con el costo de referencia del producto
     * (el lote todavía no se conoce): por eso «entregado vs. solicitado» es referencial en modo valor.
     */
    private List<Solicitado> solicitado(FiltroPanel f, List<LineaPanel> entregas) {
        Set<Long> solicitudes = entregas.stream().map(LineaPanel::solicitudId).collect(Collectors.toSet());
        List<VistasInventario.EntregaProducto> filas = vistas.solicitadoVsEntregado(solicitudes).stream()
                .filter(e -> e.cantidadSolicitada() != null).toList();
        Map<Long, Producto> productos = productoRepository
                .findAllById(filas.stream().map(VistasInventario.EntregaProducto::productoId)
                        .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Producto::getId, Function.identity()));
        List<Solicitado> resultado = new ArrayList<>();
        for (var fila : filas) {
            Producto p = productos.get(fila.productoId());
            if (!coincide(p, f)) continue;
            long costo = p.getCostoUnitario() == null ? 0 : p.getCostoUnitario();
            resultado.add(new Solicitado(fila.solicitudId(), p.getId(), fila.cantidadSolicitada(),
                    fila.cantidadSolicitada() * costo, p.getUnidadMedida().getId(), p.getUnidadMedida().getNombre()));
        }
        return resultado;
    }

    private static boolean coincide(Producto p, FiltroPanel f) {
        return (f.categoriaId() == null || p.getCategoria().getId().equals(f.categoriaId()))
                && (f.productoIds().isEmpty() || f.productoIds().contains(p.getId()));
    }

    // ------------------------------------------------------------------ utilidades

    private List<LineaPanel> lineas(FiltroPanel f, Collection<String> tipos) {
        boolean todos = f.productoIds().isEmpty();
        return repositorio.lineas(f.desdeHora(), f.hastaExclusivo(), tipos, f.categoriaId(), todos,
                todos ? List.of(-1L) : f.productoIds(), f.solicitanteId());
    }

    private static List<LineaPanel> deTipo(Collection<LineaPanel> lineas, String tipo) {
        return lineas.stream().filter(l -> tipo.equals(l.tipo())).toList();
    }

    private static long cantidad(Collection<LineaPanel> lineas) {
        return lineas.stream().mapToLong(LineaPanel::cantidad).sum();
    }

    /** Nombre de la unidad si lo consumido y lo solicitado la comparten; nulo si no hay datos o se mezclan. */
    private static String unidadComun(Collection<LineaPanel> lineas, Collection<Solicitado> solicitado) {
        Map<Long, String> unidades = new HashMap<>();
        lineas.forEach(l -> unidades.put(l.unidadId(), l.unidadNombre()));
        solicitado.forEach(x -> unidades.put(x.unidadId(), x.unidad()));
        return unidades.size() == 1 ? unidades.values().iterator().next() : null;
    }

    private static long valor(Collection<LineaPanel> lineas) {
        return lineas.stream().mapToLong(LineaPanel::valor).sum();
    }
}
