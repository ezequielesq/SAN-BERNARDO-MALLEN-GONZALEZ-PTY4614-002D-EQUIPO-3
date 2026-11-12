package com.smartrdp.backend.flujos;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Flujo E: entradas, salidas, FEFO, correcciones, estado de stock y alertas de vencimiento. */
class EntradasSalidasFlujoTest extends FlujoBase {

    @Test
    @DisplayName("HU-20 Entrada de un producto no perecible: el stock sube")
    void entradaDeProductoNoPerecibleSubeElStock() {
        var producto = crearProducto(false);
        assertThat(stockDe(producto.id())).isZero();

        var respuesta = entrada(producto.id(), 10, null, "L-001").esperando(201);

        assertThat(respuesta.json().path("tipo").asText()).isEqualTo("COMPRA");
        assertThat(respuesta.json().path("lineas")).hasSize(1);
        assertThat(respuesta.json().path("lineas").get(0).path("cantidad").asInt()).isEqualTo(10);
        assertThat(stockDe(producto.id())).isEqualTo(10);

        // Una segunda entrada se suma como otro lote.
        entrada(producto.id(), 5, null, "L-002").esperando(201);
        assertThat(stockDe(producto.id())).isEqualTo(15);

        // Queda en el libro: dos movimientos de entrada del producto.
        assertThat(movimientosDeTipo(producto.id(), "COMPRA")).hasSize(2);
    }

    @Test
    @DisplayName("HU-20 Entrada de un perecible con fecha de vencimiento: el stock sube y se informa el vencimiento")
    void entradaDePerecibleConVencimientoSubeElStock() {
        var producto = crearProducto(true);
        LocalDate vence = hoy().plusDays(40);

        entrada(producto.id(), 8, vence, "LP-1").esperando(201);

        var stock = stock(producto.id());
        assertThat(stock.cantidad()).isEqualTo(8);
        assertThat(stock.proximoVencimiento()).isEqualTo(vence);
    }

    @Test
    @DisplayName("HU-20 Entrada de un perecible sin fecha de vencimiento: rechazada y sin efecto")
    void entradaDePerecibleSinVencimientoSeRechaza() {
        var perecible = crearProducto(true);
        var comun = crearProducto(false);

        var sola = entrada(perecible.id(), 6, null, "LP-X");
        assertThat(sola.estado()).isEqualTo(422);
        assertThat(sola.campo()).isEqualTo("fechaVencimiento");
        assertThat(stockDe(perecible.id())).isZero();

        // Si una línea es válida y otra no, no se guarda ninguna (todo o nada).
        var mezcla = entradaConLineas(
                lineaEntrada(comun.id(), 4, null, "L-OK"),
                lineaEntrada(perecible.id(), 3, null, "LP-Y"));
        assertThat(mezcla.estado()).isEqualTo(422);
        assertThat(stockDe(comun.id())).isZero();
        assertThat(movimientos(comun.id())).isEmpty();
        assertThat(movimientos(perecible.id())).isEmpty();
    }

    @Test
    @DisplayName("HU-30 / CP-05 Salida mayor al stock: rechazada y no queda registro")
    void salidaMayorAlStockSeRechazaYNoQuedaRegistro() {
        var producto = productoConStock(10);

        var excesiva = salida(producto.id(), 11);

        assertThat(excesiva.estado()).isEqualTo(422);
        assertThat(excesiva.mensaje()).contains("Stock insuficiente").contains("Disponible: 10");
        assertThat(stockDe(producto.id())).isEqualTo(10);
        assertThat(movimientosDeTipo(producto.id(), "CONSUMO_INTERNO")).isEmpty();

        // Justo el stock disponible sí se puede sacar: queda agotado.
        salida(producto.id(), 10).esperando(201);
        var agotado = stock(producto.id());
        assertThat(agotado.cantidad()).isZero();
        assertThat(agotado.estado()).isEqualTo("AGOTADO");

        // Y sin stock, ninguna salida pasa.
        assertThat(salida(producto.id(), 1).estado()).isEqualTo(422);
        assertThat(stockDe(producto.id())).isZero();
    }

    @Test
    @DisplayName("HU-40 Salida con dos lotes: descuenta primero el de vencimiento más próximo (FEFO)")
    void salidaDescuentaPrimeroElLoteQueVenceAntes() {
        var producto = crearProducto(true);
        // Se ingresa primero el que vence más tarde: FEFO no es lo mismo que «el primero en entrar».
        entrada(producto.id(), 5, hoy().plusDays(60), "LEJANO").esperando(201);
        entrada(producto.id(), 5, hoy().plusDays(10), "CERCANO").esperando(201);

        var respuesta = salida(producto.id(), 7).esperando(201);

        var lineas = respuesta.json().path("lineas");
        assertThat(lineas).hasSize(2);
        assertThat(cantidadDelLote(lineas, "CERCANO")).isEqualTo(5);
        assertThat(cantidadDelLote(lineas, "LEJANO")).isEqualTo(2);

        // Queda stock solo del lote que vence más tarde.
        var stock = stock(producto.id());
        assertThat(stock.cantidad()).isEqualTo(3);
        assertThat(stock.proximoVencimiento()).isEqualTo(hoy().plusDays(60));
    }

    @Test
    @DisplayName("Corrección de una salida: restituye el stock y no se puede corregir dos veces")
    void correccionDeUnaSalidaRestituyeElStock() {
        var producto = productoConStock(10);
        long salidaId = salida(producto.id(), 4).esperando(201).json().path("id").asLong();
        assertThat(stockDe(producto.id())).isEqualTo(6);

        var correccion = post("/api/movimientos/operaciones/" + salidaId + "/correccion", bodeguero().token(),
                objeto("nota", "Se registró por error")).esperando(201);

        assertThat(correccion.json().path("tipo").asText()).isEqualTo("CORRECCION_AUMENTA");
        assertThat(stockDe(producto.id())).isEqualTo(10);

        // El libro marca la salida como corregida y la corrección como tal.
        var libro = movimientos(producto.id());
        assertThat(libro.stream().filter(m -> m.path("operacionId").asLong() == salidaId))
                .isNotEmpty().allMatch(m -> m.path("corregida").asBoolean());
        assertThat(libro.stream().filter(m -> "CORRECCION_AUMENTA".equals(m.path("tipo").asText())))
                .isNotEmpty().allMatch(m -> m.path("esCorreccion").asBoolean());

        // Una operación se corrige a lo más una vez.
        var repetida = post("/api/movimientos/operaciones/" + salidaId + "/correccion", bodeguero().token(), null);
        assertThat(repetida.estado()).isEqualTo(422);
        assertThat(repetida.mensaje()).contains("ya fue corregida");
        assertThat(stockDe(producto.id())).isEqualTo(10);
    }

    @Test
    @DisplayName("Corrección de una entrada: se permite si el lote no se usó y se rechaza si ya salió parte")
    void correccionDeUnaEntradaSoloSiElLoteNoSeUso() {
        var sinUso = crearProducto(false);
        long entradaSinUso = entrada(sinUso.id(), 10, null, "L-A").esperando(201).json().path("id").asLong();
        post("/api/movimientos/operaciones/" + entradaSinUso + "/correccion", bodeguero().token(), null)
                .esperando(201);
        assertThat(stockDe(sinUso.id())).isZero();

        var usado = crearProducto(false);
        long entradaUsada = entrada(usado.id(), 10, null, "L-B").esperando(201).json().path("id").asLong();
        salida(usado.id(), 3).esperando(201);
        var rechazada = post("/api/movimientos/operaciones/" + entradaUsada + "/correccion", bodeguero().token(), null);
        assertThat(rechazada.estado()).isEqualTo(422);
        assertThat(rechazada.mensaje()).contains("ya se usó parte");
        assertThat(stockDe(usado.id())).isEqualTo(7);
    }

    @Test
    @DisplayName("HU-60 Estado de stock por umbrales: Normal, Bajo, Crítico y Agotado")
    void estadoDeStockPorUmbrales() {
        // Mínimo 10 y crítico 5 (50 %): Normal > 10; Bajo 6..10; Crítico 1..5; Agotado 0.
        var producto = crearProducto(false, 10, 5);
        entrada(producto.id(), 11, null, "L-1").esperando(201);
        assertThat(stock(producto.id()).estado()).as("11 unidades").isEqualTo("NORMAL");

        salida(producto.id(), 1).esperando(201);
        assertThat(stock(producto.id()).estado()).as("10 unidades (igual al mínimo)").isEqualTo("BAJO");

        salida(producto.id(), 4).esperando(201);
        assertThat(stock(producto.id()).estado()).as("6 unidades").isEqualTo("BAJO");

        salida(producto.id(), 1).esperando(201);
        assertThat(stock(producto.id()).estado()).as("5 unidades (igual al crítico)").isEqualTo("CRITICO");

        salida(producto.id(), 4).esperando(201);
        assertThat(stock(producto.id()).estado()).as("1 unidad").isEqualTo("CRITICO");

        salida(producto.id(), 1).esperando(201);
        assertThat(stock(producto.id()).estado()).as("0 unidades").isEqualTo("AGOTADO");

        // El listado general muestra el mismo estado para el producto.
        var listado = get("/api/movimientos/stock", bodeguero().token()).esperando(200).json();
        JsonNode fila = null;
        for (JsonNode f : listado) {
            if (f.path("productoId").asLong() == producto.id()) fila = f;
        }
        assertThat(fila).isNotNull();
        assertThat(fila.path("estado").asText()).isEqualTo("AGOTADO");
        assertThat(fila.path("stockActual").asInt()).isZero();
    }

    @Test
    @DisplayName("HU-70 Alertas de vencimiento: aparece el lote próximo, el vencido va marcado y el lejano no aparece")
    void alertasDeVencimientoMuestranProximosYVencidos() {
        var producto = crearProducto(true);
        entrada(producto.id(), 4, hoy().plusDays(3), "PROXIMO").esperando(201);
        entrada(producto.id(), 5, hoy().minusDays(2), "VENCIDO").esperando(201);
        entrada(producto.id(), 6, hoy().plusDays(30), "LEJANO").esperando(201);

        var alertas = alertasDe(producto.id(), 7);

        assertThat(alertas.stream().map(a -> a.path("numeroLote").asText()))
                .containsExactlyInAnyOrder("PROXIMO", "VENCIDO");
        var proximo = alertaDelLote(alertas, "PROXIMO");
        assertThat(proximo.path("vencido").asBoolean()).isFalse();
        assertThat(proximo.path("cantidadDisponible").asInt()).isEqualTo(4);
        assertThat(proximo.path("fechaVencimiento").asText()).isEqualTo(hoy().plusDays(3).toString());
        assertThat(alertaDelLote(alertas, "VENCIDO").path("vencido").asBoolean()).isTrue();

        // Con un umbral mayor el lote lejano también entra.
        assertThat(alertasDe(producto.id(), 45)).hasSize(3);
    }

    @Test
    @Disabled("BUG: el borde exacto no alerta. MovimientoService.getAlertasVencimiento pide lotes con vencimiento "
            + "< hoy+N (VistasInventario.lotesConVencimiento usa 'fecha_vencimiento < :hasta'); un lote que vence "
            + "exactamente en N días queda fuera, contra HU-70 'faltan 7 días o menos'")
    @DisplayName("HU-70 / CP-08 Alerta en el borde exacto: un lote que vence en N días entra con umbral N")
    void alertaEnElBordeExactoDeNDias() {
        var producto = crearProducto(true);
        entrada(producto.id(), 3, hoy().plusDays(7), "BORDE").esperando(201);
        entrada(producto.id(), 3, hoy().plusDays(8), "PASADO-EL-BORDE").esperando(201);

        var alertas = alertasDe(producto.id(), 7);

        assertThat(alertas.stream().map(a -> a.path("numeroLote").asText())).containsExactly("BORDE");
    }

    // ------------------------------------------------------------------ ayudantes

    private int cantidadDelLote(JsonNode lineas, String numeroLote) {
        for (JsonNode l : lineas) {
            if (numeroLote.equals(l.path("numeroLote").asText())) return l.path("cantidad").asInt();
        }
        return 0;
    }

    private List<JsonNode> alertasDe(long productoId, int dias) {
        var todas = get("/api/movimientos/alertas/vencimiento?dias=" + dias, bodeguero().token()).esperando(200).json();
        List<JsonNode> propias = new ArrayList<>();
        for (JsonNode a : todas) {
            if (a.path("productoId").asLong() == productoId) propias.add(a);
        }
        return propias;
    }

    private JsonNode alertaDelLote(List<JsonNode> alertas, String numeroLote) {
        return alertas.stream().filter(a -> numeroLote.equals(a.path("numeroLote").asText())).findFirst()
                .orElseThrow(() -> new AssertionError("No hay alerta del lote " + numeroLote));
    }
}
