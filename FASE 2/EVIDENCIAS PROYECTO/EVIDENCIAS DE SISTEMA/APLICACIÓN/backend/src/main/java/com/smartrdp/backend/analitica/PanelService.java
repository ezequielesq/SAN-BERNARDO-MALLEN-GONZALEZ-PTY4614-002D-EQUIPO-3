package com.smartrdp.backend.analitica;

import com.smartrdp.backend.analitica.dto.CompraDto;
import com.smartrdp.backend.analitica.dto.CompraProveedorDto;
import com.smartrdp.backend.analitica.dto.FilaPersonaDto;
import com.smartrdp.backend.analitica.dto.HistorialPreciosDto;
import com.smartrdp.backend.analitica.dto.ImporteDto;
import com.smartrdp.backend.analitica.dto.OpcionDto;
import com.smartrdp.backend.analitica.dto.PedidosPorPersonaDto;
import com.smartrdp.backend.analitica.dto.ProductoTopDto;
import com.smartrdp.backend.analitica.dto.ProveedorPrecioDto;
import com.smartrdp.backend.analitica.dto.PuntoConsumoDto;
import com.smartrdp.backend.analitica.dto.ResumenPanelDto;
import com.smartrdp.backend.analitica.dto.SerieConsumoDto;
import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.movimiento.TipoOperacion;
import com.smartrdp.backend.movimiento.VistasInventario;
import com.smartrdp.backend.producto.Producto;
import com.smartrdp.backend.producto.ProductoRepository;
import com.smartrdp.backend.usuario.Rol;
import com.smartrdp.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
    private final UsuarioRepository usuarioRepository;

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

    // ------------------------------------------------------------------ consumo por período

    @Transactional(readOnly = true)
    public SerieConsumoDto consumoPeriodo(FiltroPanel f) {
        List<LineaPanel> lineas = lineas(f, TIPOS_CONSUMO_Y_PERDIDA);
        List<LineaPanel> consumo = lineas.stream().filter(l -> TIPOS_CONSUMO.contains(l.tipo())).toList();
        // Misma regla que el resumen: lo solicitado también cuenta para decidir si las unidades se mezclan.
        String unidad = unidadComun(consumo, solicitado(f, deTipo(consumo, ENTREGA_PEDIDO)));

        Map<LocalDate, long[]> acumulado = new TreeMap<>(); // [consumoValor, consumoCantidad, perdidasValor]
        for (LocalDate inicio = inicioDePeriodo(f.desde(), f.agrupar()); !inicio.isAfter(f.hasta());
             inicio = siguiente(inicio, f.agrupar())) {
            acumulado.put(inicio, new long[3]);
        }
        for (LineaPanel l : lineas) {
            long[] periodo = acumulado.get(inicioDePeriodo(l.fecha().toLocalDate(), f.agrupar()));
            switch (l.tipo()) {
                case ENTREGA_PEDIDO -> { periodo[0] += l.valor(); periodo[1] += l.cantidad(); }
                case DEVOLUCION -> { periodo[0] -= l.valor(); periodo[1] -= l.cantidad(); }
                default -> periodo[2] += l.valor();
            }
        }
        List<PuntoConsumoDto> puntos = acumulado.entrySet().stream()
                .map(e -> new PuntoConsumoDto(e.getKey(), e.getValue()[0],
                        unidad == null ? null : e.getValue()[1], e.getValue()[2]))
                .toList();
        return new SerieConsumoDto(unidad, puntos);
    }

    // ------------------------------------------------------------------ productos más usados

    @Transactional(readOnly = true)
    public List<ProductoTopDto> productosTop(FiltroPanel f, int limite) {
        if (limite < 1 || limite > 50) {
            throw new BusinessException("El límite debe estar entre 1 y 50.", "limite");
        }
        record Neto(String nombre, String unidad, long[] cantidadYValor) {}
        Map<Long, Neto> porProducto = new LinkedHashMap<>();
        for (LineaPanel l : lineas(f, TIPOS_CONSUMO)) {
            int signo = ENTREGA_PEDIDO.equals(l.tipo()) ? 1 : -1;
            long[] acumulado = porProducto
                    .computeIfAbsent(l.productoId(), id -> new Neto(l.productoNombre(), l.unidadNombre(), new long[2]))
                    .cantidadYValor();
            acumulado[0] += signo * l.cantidad();
            acumulado[1] += signo * l.valor();
        }
        return porProducto.entrySet().stream()
                .filter(e -> e.getValue().cantidadYValor()[0] > 0)
                .map(e -> new ProductoTopDto(e.getKey(), e.getValue().nombre(), e.getValue().unidad(),
                        e.getValue().cantidadYValor()[0], e.getValue().cantidadYValor()[1]))
                .sorted(Comparator.comparingLong(ProductoTopDto::valor).reversed()
                        .thenComparing(Comparator.comparingLong(ProductoTopDto::cantidad).reversed())
                        .thenComparing(ProductoTopDto::nombre))
                .limit(limite)
                .toList();
    }

    // ------------------------------------------------------------------ pedidos por persona

    @Transactional(readOnly = true)
    public PedidosPorPersonaDto pedidosPorPersona(FiltroPanel f) {
        List<LineaPanel> lineas = lineas(f, TIPOS_CONSUMO);
        List<LineaPanel> entregas = deTipo(lineas, ENTREGA_PEDIDO);
        List<Solicitado> solicitado = solicitado(f, entregas);
        String unidad = unidadComun(lineas, solicitado);
        boolean hayCantidad = unidad != null;

        Map<Long, Long> personaDeSolicitud = new HashMap<>();
        entregas.forEach(l -> personaDeSolicitud.put(l.solicitudId(), l.solicitanteId()));
        Map<Long, String> nombres = new HashMap<>();
        lineas.forEach(l -> nombres.put(l.solicitanteId(), l.solicitanteNombre()));

        List<FilaPersonaDto> filas = nombres.keySet().stream().map(personaId -> {
            List<LineaPanel> suyasEntregas = entregas.stream().filter(l -> personaId.equals(l.solicitanteId())).toList();
            List<LineaPanel> suyasDevoluciones = deTipo(lineas, DEVOLUCION).stream()
                    .filter(l -> personaId.equals(l.solicitanteId())).toList();
            List<Solicitado> suyoSolicitado = solicitado.stream()
                    .filter(s -> personaId.equals(personaDeSolicitud.get(s.solicitudId()))).toList();
            long solicitadoValor = suyoSolicitado.stream().mapToLong(Solicitado::valor).sum();
            long solicitadoCantidad = suyoSolicitado.stream().mapToLong(Solicitado::cantidad).sum();
            long entregadoValor = valor(suyasEntregas);
            long devueltoValor = valor(suyasDevoluciones);
            long entregadoCantidad = cantidad(suyasEntregas);
            long devueltoCantidad = cantidad(suyasDevoluciones);
            return new FilaPersonaDto(personaId, nombres.get(personaId),
                    suyasEntregas.stream().map(LineaPanel::solicitudId).distinct().count(),
                    importe(solicitadoValor, solicitadoCantidad, hayCantidad),
                    importe(entregadoValor, entregadoCantidad, hayCantidad),
                    importe(devueltoValor, devueltoCantidad, hayCantidad),
                    importe(entregadoValor - devueltoValor, entregadoCantidad - devueltoCantidad, hayCantidad));
        }).sorted(Comparator.comparingLong((FilaPersonaDto fila) -> fila.neto().valor()).reversed()
                .thenComparing(FilaPersonaDto::nombre)).toList();
        return new PedidosPorPersonaDto(unidad, filas);
    }

    @Transactional(readOnly = true)
    public List<OpcionDto> solicitantes() {
        return usuarioRepository.findByRolCodigoInAndActivoTrueOrderByNombreAsc(List.of(Rol.EMPLEADO)).stream()
                .map(u -> new OpcionDto(u.getId(), u.getNombre()))
                .toList();
    }

    // ------------------------------------------------------------------ historial de precios

    @Transactional(readOnly = true)
    public HistorialPreciosDto precios(Long productoId, LocalDate desde, LocalDate hasta, Long proveedorId) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", productoId));
        // Se leen las compras de todos los proveedores: la comparación no depende del filtro.
        FiltroPanel f = FiltroPanel.de(desde, hasta, null, null, List.of(productoId), null, null);
        List<LineaPanel> todas = lineas(f, List.of(TipoOperacion.COMPRA)).stream()
                .filter(l -> l.costo() != null).toList();
        List<ProveedorPrecioDto> porProveedor = preciosPorProveedor(todas);
        List<CompraDto> compras = todas.stream()
                .filter(l -> proveedorId == null || proveedorId.equals(l.proveedorId()))
                .map(l -> new CompraDto(l.fecha().toLocalDate(), l.costo(), l.cantidad(), l.proveedorNombre()))
                .toList();

        if (compras.size() < 2) {
            return new HistorialPreciosDto(productoId, producto.getNombre(), producto.getUnidadMedida().getNombre(),
                    compras, null, null, null, null, null, null, null, porProveedor);
        }
        int ultimo = compras.get(compras.size() - 1).costo();
        int anterior = compras.get(compras.size() - 2).costo();
        long cantidadTotal = compras.stream().mapToLong(CompraDto::cantidad).sum();
        double ponderado = compras.stream().mapToDouble(c -> (double) c.costo() * c.cantidad()).sum() / cantidadTotal;
        return new HistorialPreciosDto(productoId, producto.getNombre(), producto.getUnidadMedida().getNombre(),
                compras, ultimo, anterior,
                anterior == 0 ? null : redondear((ultimo - anterior) * 100.0 / anterior),
                compras.stream().mapToInt(CompraDto::costo).min().orElseThrow(),
                compras.stream().mapToInt(CompraDto::costo).max().orElseThrow(),
                redondear(compras.stream().mapToInt(CompraDto::costo).average().orElseThrow()),
                redondear(ponderado), porProveedor);
    }

    // ------------------------------------------------------------------ compras por proveedor

    /** Gasto en compras de cada proveedor en el rango (a costo de lote), de mayor a menor valor. */
    @Transactional(readOnly = true)
    public List<CompraProveedorDto> comprasPorProveedor(FiltroPanel f) {
        // Los lotes sin proveedor comparten la clave nula: un LinkedHashMap la admite.
        Map<Long, List<LineaPanel>> porProveedor = new LinkedHashMap<>();
        for (LineaPanel l : lineas(f, List.of(TipoOperacion.COMPRA))) {
            porProveedor.computeIfAbsent(l.proveedorId(), id -> new ArrayList<>()).add(l);
        }
        return porProveedor.entrySet().stream()
                .map(e -> new CompraProveedorDto(e.getKey(),
                        e.getKey() == null ? "Sin proveedor" : e.getValue().get(0).proveedorNombre(),
                        valor(e.getValue()), cantidad(e.getValue()), e.getValue().size()))
                .sorted(Comparator.comparingLong(CompraProveedorDto::valor).reversed()
                        .thenComparing(CompraProveedorDto::nombre))
                .toList();
    }

    /** Último costo, promedio ponderado por cantidad y número de compras de cada proveedor, por nombre. */
    private static List<ProveedorPrecioDto> preciosPorProveedor(List<LineaPanel> compras) {
        Map<Long, List<LineaPanel>> porProveedor = compras.stream().filter(l -> l.proveedorId() != null)
                .collect(Collectors.groupingBy(LineaPanel::proveedorId));
        return porProveedor.values().stream().map(grupo -> {
            long cantidadTotal = grupo.stream().mapToLong(LineaPanel::cantidad).sum();
            double ponderado = grupo.stream().mapToDouble(l -> (double) l.costo() * l.cantidad()).sum()
                    / cantidadTotal;
            LineaPanel ultima = grupo.get(grupo.size() - 1); // las líneas vienen por fecha
            return new ProveedorPrecioDto(ultima.proveedorId(), ultima.proveedorNombre(), ultima.costo(),
                    redondear(ponderado), grupo.size());
        }).sorted(Comparator.comparing(ProveedorPrecioDto::nombre)).toList();
    }

    private static double redondear(double valor) {
        return Math.round(valor * 10) / 10.0;
    }

    private static ImporteDto importe(long valor, long cantidad, boolean conCantidad) {
        return new ImporteDto(valor, conCantidad ? cantidad : null);
    }

    private static LocalDate inicioDePeriodo(LocalDate dia, Agrupacion agrupar) {
        return switch (agrupar) {
            case DIA -> dia;
            case SEMANA -> dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> dia.withDayOfMonth(1);
        };
    }

    private static LocalDate siguiente(LocalDate inicio, Agrupacion agrupar) {
        return switch (agrupar) {
            case DIA -> inicio.plusDays(1);
            case SEMANA -> inicio.plusWeeks(1);
            case MES -> inicio.plusMonths(1);
        };
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
                todos ? List.of(-1L) : f.productoIds(), f.solicitanteId(), f.proveedorId());
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
