package com.smartrdp.backend.flujos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LotesFlujoTest extends FlujoBase {

    private List<JsonNode> lotes(String consulta) {
        var r = get("/api/lotes" + consulta, bodeguero().token()).esperando(200);
        List<JsonNode> lista = new ArrayList<>();
        r.json().forEach(lista::add);
        return lista;
    }

    private static List<String> campo(List<JsonNode> lotes, String nombre) {
        return lotes.stream().map(l -> l.path(nombre).asText()).toList();
    }

    @Test
    @DisplayName("Lotes: estado derivado y orden FEFO (el que vence antes va primero)")
    void estadosYOrdenFefo() {
        var p = crearProducto(true);
        entradaConLineas(
                lineaEntrada(p.id(), 4, hoy().plusDays(60), "VIGENTE"),
                lineaEntrada(p.id(), 4, hoy().minusDays(2), "VENCIDO"),
                lineaEntrada(p.id(), 4, hoy().plusDays(3), "PORVENCER")).esperando(201);

        var lista = lotes("?productoId=" + p.id());

        assertThat(campo(lista, "numeroLote")).containsExactly("VENCIDO", "PORVENCER", "VIGENTE");
        assertThat(campo(lista, "estado")).containsExactly("VENCIDO", "POR_VENCER", "VIGENTE");
        assertThat(lista.get(1).path("diasParaVencer").asInt()).isEqualTo(3);
        assertThat(lista.get(0).path("cantidadInicial").asInt()).isEqualTo(4);
        assertThat(lista.get(0).path("saldo").asInt()).isEqualTo(4);
        assertThat(lista.get(0).path("valorEnStock").asLong()).isEqualTo(4000L); // costo 1000 de la entrada auxiliar
    }

    @Test
    @DisplayName("Lotes: un lote sin vencimiento es vigente, no tiene días y va al final")
    void sinVencimiento() {
        var p = crearProducto(false);
        entrada(p.id(), 5, null, "SINFECHA").esperando(201);

        var lote = lotes("?productoId=" + p.id()).get(0);

        assertThat(lote.path("estado").asText()).isEqualTo("VIGENTE");
        assertThat(lote.path("diasParaVencer").isNull()).isTrue();
    }

    @Test
    @DisplayName("Lotes: el umbral de «por vencer» es editable y el día exacto cuenta")
    void umbralEditable() {
        var p = crearProducto(true);
        entrada(p.id(), 5, hoy().plusDays(7), "BORDE").esperando(201);

        assertThat(lotes("?productoId=" + p.id() + "&diasPorVencer=7").get(0).path("estado").asText())
                .isEqualTo("POR_VENCER");
        assertThat(lotes("?productoId=" + p.id() + "&diasPorVencer=6").get(0).path("estado").asText())
                .isEqualTo("VIGENTE");
    }

    @Test
    @DisplayName("Lotes: los agotados se ocultan salvo que se pidan")
    void agotados() {
        var p = crearProducto(false);
        entrada(p.id(), 5, null, "L1").esperando(201);
        salida(p.id(), 5).esperando(201);

        assertThat(lotes("?productoId=" + p.id())).isEmpty();
        var con = lotes("?productoId=" + p.id() + "&incluirAgotados=true");
        assertThat(campo(con, "estado")).containsExactly("AGOTADO");
        assertThat(lotes("?productoId=" + p.id() + "&estado=AGOTADO")).hasSize(1);
    }

    @Test
    @DisplayName("Lotes: se filtra por proveedor, categoría y texto del número de lote")
    void filtros() {
        Prov a = crearProveedor();
        Prov b = crearProveedor();
        var categoria = crearCategoriaPropia(false);
        var p = crearProductoEn(categoria.id(), false);
        var lineaA = lineaEntrada(p.id(), 3, null, "ALFA-" + unico());
        lineaA.put("proveedorId", a.id());
        var lineaB = lineaEntrada(p.id(), 3, null, "BETA-" + unico());
        lineaB.put("proveedorId", b.id());
        entradaConLineas(lineaA, lineaB).esperando(201);

        var delA = lotes("?categoriaId=" + categoria.id() + "&proveedorId=" + a.id());
        assertThat(delA).hasSize(1);
        assertThat(delA.get(0).path("proveedorNombre").asText()).isEqualTo(a.nombre());
        assertThat(lotes("?categoriaId=" + categoria.id() + "&texto=beta")).hasSize(1);
        assertThat(lotes("?categoriaId=" + categoria.id())).hasSize(2);
    }

    @Test
    @DisplayName("Lotes: el detalle muestra la trazabilidad con el saldo acumulado y coincide con el stock")
    void detalleConTrazabilidad() {
        var p = crearProducto(false);
        entrada(p.id(), 10, null, "TRAZA").esperando(201);
        salida(p.id(), 4).esperando(201);
        long loteId = lotes("?productoId=" + p.id()).get(0).path("id").asLong();

        var detalle = get("/api/lotes/" + loteId, bodeguero().token()).esperando(200).json();

        assertThat(detalle.path("lote").path("saldo").asInt()).isEqualTo(6);
        assertThat(detalle.path("lote").path("saldo").asInt()).isEqualTo(stockDe(p.id()));
        var movimientos = detalle.path("trazabilidad");
        assertThat(movimientos).hasSize(2);
        assertThat(movimientos.get(0).path("tipo").asText()).isEqualTo("COMPRA");
        assertThat(movimientos.get(0).path("sentido").asInt()).isEqualTo(1);
        assertThat(movimientos.get(0).path("saldoDespues").asInt()).isEqualTo(10);
        assertThat(movimientos.get(1).path("tipo").asText()).isEqualTo("CONSUMO_INTERNO");
        assertThat(movimientos.get(1).path("saldoDespues").asInt()).isEqualTo(6);
        assertThat(movimientos.get(1).path("usuarioEmail").asText()).isEqualTo(bodeguero().email());
    }

    @Test
    @DisplayName("Lotes: la trazabilidad incluye el pedido, quién lo pidió y la devolución")
    void trazabilidadConPedidoYDevolucion() {
        var bodeguero = crearBodeguero();
        var empleado = crearEmpleado();
        var p = crearProducto(false);
        entrada(p.id(), 10, null, "PEDIDO").esperando(201);
        long pedido = pedirOk(empleado, p.id(), 6);
        aprobar(bodeguero, pedido).esperando(200);
        long devolucion = devolver(empleado, pedido, item(p.id(), 2)).esperando(201).json().path("id").asLong();
        aprobar(bodeguero, devolucion).esperando(200);
        long loteId = lotes("?productoId=" + p.id()).get(0).path("id").asLong();

        var movimientos = get("/api/lotes/" + loteId, bodeguero().token()).esperando(200).json().path("trazabilidad");

        assertThat(movimientos).hasSize(3);
        var entrega = movimientos.get(1);
        assertThat(entrega.path("tipo").asText()).isEqualTo("ENTREGA_PEDIDO");
        assertThat(entrega.path("solicitudId").asLong()).isEqualTo(pedido);
        assertThat(entrega.path("solicitanteNombre").asText()).isEqualTo(empleado.nombre());
        assertThat(entrega.path("saldoDespues").asInt()).isEqualTo(4);
        assertThat(movimientos.get(2).path("tipo").asText()).isEqualTo("DEVOLUCION");
        assertThat(movimientos.get(2).path("saldoDespues").asInt()).isEqualTo(6);
    }

    @Test
    @DisplayName("Lotes: permisos y errores")
    void permisosYErrores() {
        assertThat(get("/api/lotes", tokenAdmin()).estado()).isEqualTo(200);
        assertThat(get("/api/lotes", null).estado()).isEqualTo(401);
        get("/api/lotes/999999999", bodeguero().token()).esperando(404);
        assertThat(get("/api/lotes?estado=INVENTADO", bodeguero().token()).esperando(422).campo()).isEqualTo("estado");
        assertThat(get("/api/lotes?diasPorVencer=-1", bodeguero().token()).estado()).isEqualTo(422);
    }
}
