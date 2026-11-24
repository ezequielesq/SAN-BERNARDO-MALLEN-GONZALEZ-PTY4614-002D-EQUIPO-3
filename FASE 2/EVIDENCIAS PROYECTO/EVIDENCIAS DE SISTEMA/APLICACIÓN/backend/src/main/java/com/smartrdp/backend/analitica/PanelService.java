package com.smartrdp.backend.analitica;

import com.smartrdp.backend.analitica.dto.ResumenPanelDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

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

    private static final List<String> TIPOS_CONSUMO_Y_PERDIDA = List.of(ENTREGA_PEDIDO, DEVOLUCION, MERMA, VENCIMIENTO);

    private final PanelRepository repositorio;

    @Transactional(readOnly = true)
    public ResumenPanelDto resumen(FiltroPanel f) {
        List<LineaPanel> lineas = lineas(f, TIPOS_CONSUMO_Y_PERDIDA);
        List<LineaPanel> entregas = deTipo(lineas, ENTREGA_PEDIDO);
        List<LineaPanel> devoluciones = deTipo(lineas, DEVOLUCION);
        // Las cantidades solo se suman si todos los productos del consumo comparten unidad (regla del servidor).
        List<LineaPanel> consumo = lineas.stream()
                .filter(l -> ENTREGA_PEDIDO.equals(l.tipo()) || DEVOLUCION.equals(l.tipo())).toList();
        String unidadComun = unidadComun(consumo);
        Long cantidadNeta = unidadComun == null ? null : cantidad(entregas) - cantidad(devoluciones);
        return new ResumenPanelDto(
                valor(entregas) - valor(devoluciones), cantidadNeta, unidadComun,
                entregas.stream().map(LineaPanel::solicitudId).distinct().count(),
                entregas.stream().map(LineaPanel::solicitanteId).distinct().count(),
                0, valor(entregas), null, null, 0, 0);
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

    /** Nombre de la unidad si todas las líneas la comparten; nulo si no hay líneas o se mezclan unidades. */
    private static String unidadComun(Collection<LineaPanel> lineas) {
        long unidades = lineas.stream().map(LineaPanel::unidadId).distinct().count();
        return unidades == 1 ? lineas.iterator().next().unidadNombre() : null;
    }

    private static long valor(Collection<LineaPanel> lineas) {
        return lineas.stream().mapToLong(LineaPanel::valor).sum();
    }
}
