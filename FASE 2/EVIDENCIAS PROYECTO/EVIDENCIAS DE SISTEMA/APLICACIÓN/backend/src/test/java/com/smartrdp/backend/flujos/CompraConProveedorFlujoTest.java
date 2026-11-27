package com.smartrdp.backend.flujos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static com.smartrdp.backend.flujos.ExcelPruebas.fila;
import static org.assertj.core.api.Assertions.assertThat;

class CompraConProveedorFlujoTest extends FlujoBase {

    private static final String ENTRADAS_LEER = "/api/movimientos/entradas/importar/leer";
    private static final String ENTRADAS_VALIDAR = "/api/movimientos/entradas/importar/validar";
    private static final String ENTRADAS_IMPORTAR = "/api/movimientos/entradas/importar";
    private static final String PLANTILLA = "/api/movimientos/entradas/plantilla/xlsx";

    private Respuesta comprar(long productoId, Long proveedorId) {
        var linea = objeto("productoId", productoId, "cantidad", 5, "costoUnitario", 1000);
        if (proveedorId != null) linea.put("proveedorId", proveedorId);
        return post("/api/movimientos/entradas", bodeguero().token(),
                objeto("tipo", "COMPRA", "lineas", List.of(linea)));
    }

    @Test
    @DisplayName("HU-220 Una compra sin proveedor se rechaza y el stock no cambia")
    void compraSinProveedor() {
        var producto = crearProducto(false);

        var r = comprar(producto.id(), null).esperando(422);

        assertThat(r.campo()).isEqualTo("proveedorId");
        assertThat(r.mensaje()).contains("proveedor");
        assertThat(stockDe(producto.id())).isZero();
    }

    @Test
    @DisplayName("HU-220 La compra con proveedor lo deja grabado en el lote")
    void compraConProveedorQuedaEnElLote() {
        var producto = crearProducto(false);
        Prov proveedor = crearProveedor();

        var op = comprar(producto.id(), proveedor.id()).esperando(201).json();

        long loteId = op.path("lineas").get(0).path("loteId").asLong();
        Long grabado = jdbc.queryForObject("SELECT proveedor_id FROM lotes WHERE id = ?", Long.class, loteId);
        assertThat(grabado).isEqualTo(proveedor.id());
        assertThat(stockDe(producto.id())).isEqualTo(5);
    }

    @Test
    @DisplayName("HU-220 Un proveedor inexistente o deshabilitado se rechaza")
    void proveedorInexistenteODeshabilitado() {
        var producto = crearProducto(false);
        assertThat(comprar(producto.id(), 999_999_999L).esperando(422).campo()).isEqualTo("proveedorId");

        Prov deshabilitado = crearProveedor();
        delete("/api/proveedores/" + deshabilitado.id(), bodeguero().token()).esperando(204);
        var r = comprar(producto.id(), deshabilitado.id()).esperando(422);
        assertThat(r.campo()).isEqualTo("proveedorId");
        assertThat(r.mensaje()).contains("deshabilitado");
        assertThat(stockDe(producto.id())).isZero();
    }

    @Test
    @DisplayName("HU-220 El inventario inicial y el ajuste de entrada no exigen proveedor")
    void otrosTiposNoLoExigen() {
        var producto = crearProducto(false);

        entradaDeTipo("INVENTARIO_INICIAL", producto.id(), 10, null).esperando(201);
        entradaDeTipo("AJUSTE_ENTRADA", producto.id(), 2, null).esperando(201);

        assertThat(stockDe(producto.id())).isEqualTo(12);
    }

    // ================================================================== Excel

    /** Excel de entradas armado sobre la plantilla real; las filas son (código, lote, vencimiento, cantidad, costo, RUT). */
    private byte[] entradasXlsx(List<List<Object>> filas) {
        byte[] plantilla = descargar(PLANTILLA, bodeguero().token()).esperando(200).bytes();
        return ExcelPruebas.llenarPlantilla(plantilla, "Entradas", filas);
    }

    private JsonNode leer(byte[] archivo) {
        return subirArchivo(ENTRADAS_LEER, bodeguero().token(), "archivo", "entradas.xlsx", archivo)
                .esperando(200).json();
    }

    private static String errorDe(JsonNode filaValidada, String campo) {
        return filaValidada.path("errores").path(campo).asText();
    }

    @Test
    @DisplayName("HU-220 Un Excel con dos proveedores se importa en una operación y cada lote guarda el suyo")
    void excelConDosProveedoresSeImportaEnUnaOperacion() {
        var uno = crearProducto(false);
        var dos = crearProducto(false);
        Prov a = crearProveedor();
        Prov b = crearProveedor();

        var leidas = leer(entradasXlsx(List.of(
                fila(uno.codigo(), "L-A", null, 3, 500, a.rut()),
                fila(dos.codigo(), "L-B", null, 4, 600, b.rut()))));

        assertThat(leidas).hasSize(2);
        assertThat(leidas.get(0).path("errores")).isEmpty();
        assertThat(leidas.get(1).path("errores")).isEmpty();
        assertThat(leidas.get(0).path("datos").path("proveedorNombre").asText()).isEqualTo(a.nombre());
        assertThat(leidas.get(0).path("datos").path("proveedorId").asLong()).isEqualTo(a.id());
        assertThat(leidas.get(1).path("datos").path("proveedorNombre").asText()).isEqualTo(b.nombre());

        List<JsonNode> filas = new ArrayList<>();
        leidas.forEach(f -> filas.add(f.path("datos")));
        var operacion = post(ENTRADAS_IMPORTAR, bodeguero().token(), objeto("tipo", "COMPRA", "filas", filas))
                .esperando(201).json();

        assertThat(operacion.path("lineas")).hasSize(2);
        for (int i = 0; i < 2; i++) {
            long loteId = operacion.path("lineas").get(i).path("loteId").asLong();
            Long grabado = jdbc.queryForObject("SELECT proveedor_id FROM lotes WHERE id = ?", Long.class, loteId);
            long productoDelLote = jdbc.queryForObject("SELECT producto_id FROM lotes WHERE id = ?", Long.class, loteId);
            assertThat(grabado).isEqualTo(productoDelLote == uno.id() ? a.id() : b.id());
        }
        assertThat(stockDe(uno.id())).isEqualTo(3);
        assertThat(stockDe(dos.id())).isEqualTo(4);
    }

    @Test
    @DisplayName("HU-220 El Excel valida el proveedor de cada fila: dígito, existencia, estado y obligatoriedad en compras")
    void excelValidaElProveedor() {
        var producto = crearProducto(false);
        Prov inactivo = crearProveedor();
        delete("/api/proveedores/" + inactivo.id(), bodeguero().token()).esperando(204);
        String digitoMalo = rutUnico();
        digitoMalo = digitoMalo.substring(0, digitoMalo.length() - 1) + (digitoMalo.endsWith("0") ? "1" : "0");
        String noRegistrado = rutUnico();

        var filas = List.of(
                fila(producto.codigo(), "L-1", null, 1, 100, digitoMalo),
                fila(producto.codigo(), "L-2", null, 1, 100, noRegistrado),
                fila(producto.codigo(), "L-3", null, 1, 100, inactivo.rut()),
                fila(producto.codigo(), "L-4", null, 1, 100, null));
        var leidas = leer(entradasXlsx(filas));
        assertThat(errorDe(leidas.get(0), "proveedorRut")).contains("dígito verificador");
        assertThat(errorDe(leidas.get(1), "proveedorRut")).contains("No existe un proveedor con ese RUT");
        assertThat(errorDe(leidas.get(2), "proveedorRut")).contains("deshabilitado");
        // Leer no conoce el tipo: la celda vacía no es un error todavía.
        assertThat(leidas.get(3).path("errores")).isEmpty();

        List<JsonNode> datos = new ArrayList<>();
        leidas.forEach(f -> datos.add(f.path("datos")));
        var comoCompra = post(ENTRADAS_VALIDAR, bodeguero().token(), objeto("tipo", "COMPRA", "filas", datos))
                .esperando(200).json();
        assertThat(errorDe(comoCompra.get(3), "proveedorRut")).contains("necesita el proveedor");

        var comoInicial = post(ENTRADAS_VALIDAR, bodeguero().token(),
                objeto("tipo", "INVENTARIO_INICIAL", "filas", List.of(datos.get(3)))).esperando(200).json();
        assertThat(comoInicial.get(0).path("errores")).isEmpty();
    }

    @Test
    @DisplayName("HU-220 La plantilla de entradas trae la columna Proveedor (RUT) al final")
    void plantillaTraeLaColumnaProveedor() {
        var plantilla = descargar(PLANTILLA, bodeguero().token()).esperando(200);

        assertThat(ExcelPruebas.leerHoja(plantilla.bytes(), "Entradas").get(0)).containsExactly(
                "Código", "N° lote", "Fecha vencimiento", "Cantidad", "Costo unitario", "Proveedor (RUT)");
    }
}
