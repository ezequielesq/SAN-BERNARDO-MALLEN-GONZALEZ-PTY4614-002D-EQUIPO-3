package com.smartrdp.backend.flujos;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Panel de analítica por proveedor: el filtro {@code proveedorId} (el proveedor del lote de cada movimiento), los
 * vencimientos y la exportación con ese filtro, y la comparación de precios de compra entre proveedores. Cada
 * prueba crea sus propios proveedores, categoría y productos: la base se comparte con el resto de las pruebas.
 */
class PanelProveedorFlujoTest extends FlujoBase {

    private static final String PANEL = "/api/analitica/panel/";

    /** Compra de un producto a un proveedor; con {@code dia} la operación se fecha ese día. Devuelve su id. */
    private long comprar(Prod producto, Prov proveedor, int cantidad, int costo, LocalDate vencimiento, LocalDate dia) {
        var linea = objeto("productoId", producto.id(), "cantidad", cantidad, "costoUnitario", costo,
                "proveedorId", proveedor.id(), "fechaVencimiento", vencimiento == null ? null : vencimiento.toString(),
                "numeroLote", "L-" + unico());
        long operacionId = entradaConLineas(linea).esperando(201).json().path("id").asLong();
        if (dia != null) fecharOperacion(operacionId, dia.atTime(10, 0));
        return operacionId;
    }

    private static List<Long> idsDe(JsonNode vencimientos) {
        return vencimientos.findValues("productoId").stream().map(JsonNode::asLong).toList();
    }

    /** Nombres de producto de las filas de datos del Excel exportado (columna «Producto»). */
    private List<String> productosDelExcel(String url) throws Exception {
        Descarga descarga = descargar(url, tokenAdmin()).esperando(200);
        assertThat(descarga.bytes()).isNotEmpty();
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(descarga.bytes()))) {
            var hoja = libro.getSheetAt(0);
            List<String> productos = new ArrayList<>();
            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                productos.add(hoja.getRow(i).getCell(2).getStringCellValue());
            }
            return productos;
        }
    }

    @Test
    @DisplayName("HU-140 Precios por proveedor: último y promedio ponderado de cada uno; el filtro recorta las compras pero no la comparación")
    void preciosPorProveedor() {
        Prod producto = crearProducto(false);
        Prov a = crearProveedor();
        Prov b = crearProveedor();
        comprar(producto, a, 10, 1_000, null, LocalDate.of(2026, 3, 2));
        comprar(producto, b, 5, 1_200, null, LocalDate.of(2026, 4, 6));
        comprar(producto, a, 10, 900, null, LocalDate.of(2026, 5, 4));
        String base = PANEL + "precios/" + producto.id() + "?desde=2026-03-01&hasta=2026-06-30";

        JsonNode todos = get(base, bodeguero().token()).esperando(200).json();

        assertThat(todos.path("compras")).hasSize(3);
        assertThat(todos.path("compras").findValues("proveedorNombre").stream().map(JsonNode::asText).toList())
                .containsExactly(a.nombre(), b.nombre(), a.nombre());
        JsonNode porProveedor = todos.path("porProveedor");
        assertThat(porProveedor).hasSize(2);
        JsonNode deA = porProveedor.get(0).path("proveedorId").asLong() == a.id() ? porProveedor.get(0) : porProveedor.get(1);
        JsonNode deB = porProveedor.get(0).path("proveedorId").asLong() == b.id() ? porProveedor.get(0) : porProveedor.get(1);
        assertThat(deA.path("nombre").asText()).isEqualTo(a.nombre());
        assertThat(deA.path("compras").asInt()).isEqualTo(2);
        assertThat(deA.path("ultimo").asInt()).isEqualTo(900);
        assertThat(deA.path("promedioPonderado").asDouble()).isEqualTo(950.0); // (1000·10 + 900·10) / 20
        assertThat(deB.path("compras").asInt()).isEqualTo(1);
        assertThat(deB.path("ultimo").asInt()).isEqualTo(1_200);
        assertThat(deB.path("promedioPonderado").asDouble()).isEqualTo(1_200.0);

        JsonNode soloA = get(base + "&proveedorId=" + a.id(), bodeguero().token()).esperando(200).json();
        assertThat(soloA.path("compras").findValues("costo").stream().map(JsonNode::asInt).toList())
                .containsExactly(1_000, 900);
        assertThat(soloA.path("compras").findValues("proveedorNombre").stream().map(JsonNode::asText).distinct().toList())
                .containsExactly(a.nombre());
        assertThat(soloA.path("ultimo").asInt()).isEqualTo(900);
        assertThat(soloA.path("porProveedor")).hasSize(2);
    }

    @Test
    @DisplayName("HU-100 Resumen filtrado por proveedor: el consumo se atribuye al proveedor del lote del que salió (FEFO)")
    void resumenFiltradoPorProveedor() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod producto = crearProductoEn(categoria.id(), true);
        Prov a = crearProveedor();
        Prov b = crearProveedor();
        comprar(producto, a, 10, 1_500, hoy().plusDays(10), null); // vence antes: FEFO consume de aquí
        comprar(producto, b, 10, 2_000, hoy().plusDays(40), null);
        entregar(bodeguero, empleado, producto.id(), 3, hoy());
        String base = "&categoriaId=" + categoria.id();

        JsonNode deA = resumen(bodeguero, base + "&proveedorId=" + a.id());
        assertThat(deA.path("consumoNetoValor").asLong()).isEqualTo(3 * 1_500L);
        assertThat(deA.path("pedidos").asLong()).isEqualTo(1);
        JsonNode deB = resumen(bodeguero, base + "&proveedorId=" + b.id());
        assertThat(deB.path("consumoNetoValor").asLong()).isZero();
        assertThat(deB.path("pedidos").asLong()).isZero();
        assertThat(resumen(bodeguero, base).path("consumoNetoValor").asLong()).isEqualTo(3 * 1_500L);
    }

    @Test
    @DisplayName("HU-130 Vencimientos por proveedor: tanto con umbral de días como por rango")
    void vencimientosPorProveedor() {
        Catalogo categoria = crearCategoriaPropia(false);
        Prod producto = crearProductoEn(categoria.id(), true);
        Prov a = crearProveedor();
        Prov b = crearProveedor();
        comprar(producto, a, 4, 1_000, hoy().plusDays(5), null);
        comprar(producto, b, 6, 1_000, hoy().plusDays(8), null);
        String categoriaFiltro = "&categoriaId=" + categoria.id();

        JsonNode deA = get("/api/analitica/vencimientos?dias=90&proveedorId=" + a.id() + categoriaFiltro,
                bodeguero().token()).esperando(200).json();
        assertThat(deA).hasSize(1);
        assertThat(idsDe(deA)).containsExactly(producto.id());
        assertThat(deA.get(0).path("cantidadEnRiesgo").asInt()).isEqualTo(4);

        JsonNode deB = get("/api/analitica/vencimientos/rango?desde=" + hoy() + "&hasta=" + hoy().plusDays(30)
                + "&proveedorId=" + b.id() + categoriaFiltro, bodeguero().token()).esperando(200).json();
        assertThat(deB).hasSize(1);
        assertThat(idsDe(deB)).containsExactly(producto.id());
        assertThat(deB.get(0).path("cantidadEnRiesgo").asInt()).isEqualTo(6);

        // Sin el filtro aparecen ambos lotes.
        assertThat(get("/api/analitica/vencimientos?dias=90" + categoriaFiltro, bodeguero().token()).esperando(200).json())
                .hasSize(2);
    }

    @Test
    @DisplayName("HU-120 Exportar a Excel respeta el filtro por proveedor")
    void exportarConProveedor() throws Exception {
        Catalogo categoria = crearCategoriaPropia(false);
        Prod deA = crearProductoEn(categoria.id(), false);
        Prod deB = crearProductoEn(categoria.id(), false);
        Prov a = crearProveedor();
        Prov b = crearProveedor();
        comprar(deA, a, 5, 1_000, null, null);
        comprar(deB, b, 5, 1_000, null, null);
        String base = "/api/analitica/exportar-movimientos?desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(1)
                + "&categoriaId=" + categoria.id();

        assertThat(productosDelExcel(base)).containsExactlyInAnyOrder(deA.nombre(), deB.nombre());
        assertThat(productosDelExcel(base + "&proveedorId=" + a.id())).containsExactly(deA.nombre());
        assertThat(productosDelExcel(base + "&proveedorId=" + b.id())).containsExactly(deB.nombre());
    }

    /** Consulta el resumen con un rango amplio (ayer a mañana) más los filtros extra indicados. */
    private JsonNode resumen(Cuenta quien, String filtrosExtra) {
        return get(PANEL + "resumen?desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(1) + filtrosExtra,
                quien.token()).esperando(200).json();
    }
}
