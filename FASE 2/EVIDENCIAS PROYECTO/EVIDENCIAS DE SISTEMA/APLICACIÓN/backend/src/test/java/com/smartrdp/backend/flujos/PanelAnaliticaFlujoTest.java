package com.smartrdp.backend.flujos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Panel de analítica (HU-100 a HU-140). Cada prueba crea su categoría y sus productos y consulta con el
 * filtro de esa categoría: la base se comparte con el resto de las pruebas.
 */
class PanelAnaliticaFlujoTest extends FlujoBase {

    private static final String PANEL = "/api/analitica/panel/";

    @Test
    @DisplayName("Ayudante: fechar una operación la mueve al pasado y deja activo el trigger de inmutabilidad")
    void fecharOperacionLaMueveAlPasado() {
        Prod producto = productoConStock(10);
        long operacionId = salida(bodeguero(), producto.id(), 2).esperando(201).json().path("id").asLong();
        LocalDateTime fecha = LocalDateTime.of(2026, 3, 2, 12, 0);

        fecharOperacion(operacionId, fecha);

        // El listado del libro mira solo los últimos 30 días si no se le da un rango: se pide el de la fecha.
        JsonNode linea = get("/api/movimientos?productoId=" + producto.id() + "&desde=2026-03-01&hasta=2026-03-03",
                bodeguero().token()).esperando(200).json().get(0);
        assertThat(linea.path("tipo").asText()).isEqualTo("CONSUMO_INTERNO");
        assertThat(LocalDateTime.parse(linea.path("fecha").asText())).isEqualTo(fecha);
        String estadoDelTrigger = jdbc.queryForObject(
                "SELECT tgenabled FROM pg_trigger WHERE tgname = 'trg_operaciones_inmutable'", String.class);
        assertThat(estadoDelTrigger).as("trigger activo (O = habilitado)").isEqualTo("O");
    }

    // ================================================================== resumen: consumo neto, pedidos, personas

    @Test
    @DisplayName("HU-100 Consumo neto = entregas menos devoluciones, a costo de lote; el consumo interno no cuenta")
    void consumoNetoEsEntregasMenosDevoluciones() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod producto = productoConEntrada(categoria, 100);

        long pedido = pedirOk(empleado, producto.id(), 10);
        aprobar(bodeguero, pedido).esperando(200);
        long devolucion = devolver(empleado, pedido, item(producto.id(), 3)).esperando(201).json().path("id").asLong();
        aprobar(bodeguero, devolucion).esperando(200);
        salida(bodeguero, producto.id(), 5).esperando(201); // consumo interno: no entra al panel

        JsonNode r = resumen(bodeguero, "&categoriaId=" + categoria.id());

        assertThat(r.path("consumoNetoValor").asLong()).isEqualTo(7_000);
        assertThat(r.path("cantidadNeta").asLong()).isEqualTo(7);
        assertThat(r.path("pedidos").asLong()).isEqualTo(1);
        assertThat(r.path("personas").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-100 Filtro por persona: cada empleado ve solo lo suyo")
    void filtroPorPersona() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta ana = crearEmpleado();
        Cuenta beto = crearEmpleado();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod producto = productoConEntrada(categoria, 100);
        aprobar(bodeguero, pedirOk(ana, producto.id(), 10)).esperando(200);
        aprobar(bodeguero, pedirOk(beto, producto.id(), 4)).esperando(200);

        String base = "&categoriaId=" + categoria.id();
        assertThat(resumen(bodeguero, base).path("consumoNetoValor").asLong()).isEqualTo(14_000);
        JsonNode soloAna = resumen(bodeguero, base + "&solicitanteId=" + ana.id());
        assertThat(soloAna.path("consumoNetoValor").asLong()).isEqualTo(10_000);
        assertThat(soloAna.path("personas").asLong()).isEqualTo(1);
        assertThat(resumen(bodeguero, base + "&solicitanteId=" + beto.id()).path("consumoNetoValor").asLong())
                .isEqualTo(4_000);
    }

    @Test
    @DisplayName("Panel: rango, número de productos y parámetros se validan; el acceso exige sesión")
    void validacionesYAcceso() {
        String token = bodeguero().token();
        // Reglas de negocio: 422 con el campo.
        var invertido = get(PANEL + "resumen?desde=" + hoy() + "&hasta=" + hoy().minusDays(1), token);
        assertThat(invertido.estado()).isEqualTo(422);
        assertThat(invertido.campo()).isEqualTo("desde");
        var largo = get(PANEL + "resumen?desde=" + hoy().minusDays(366) + "&hasta=" + hoy(), token);
        assertThat(largo.estado()).isEqualTo(422);
        assertThat(largo.campo()).isEqualTo("hasta");
        get(PANEL + "resumen?desde=" + hoy().minusDays(365) + "&hasta=" + hoy(), token).esperando(200);
        String cincuentaYUno = IntStream.rangeClosed(1, 51).mapToObj(Integer::toString).collect(Collectors.joining(","));
        var demasiados = get(PANEL + "resumen?desde=" + hoy() + "&hasta=" + hoy() + "&productoIds=" + cincuentaYUno, token);
        assertThat(demasiados.estado()).isEqualTo(422);
        assertThat(demasiados.campo()).isEqualTo("productoIds");
        String cincuenta = IntStream.rangeClosed(1, 50).mapToObj(Integer::toString).collect(Collectors.joining(","));
        get(PANEL + "resumen?desde=" + hoy() + "&hasta=" + hoy() + "&productoIds=" + cincuenta, token).esperando(200);
        // Parámetros faltantes o mal formados: 400.
        assertThat(get(PANEL + "resumen?hasta=" + hoy(), token).estado()).isEqualTo(400);
        assertThat(get(PANEL + "resumen?desde=" + hoy() + "&hasta=" + hoy() + "&agrupar=TRIMESTRE", token).estado())
                .isEqualTo(400);
        // Acceso: sin sesión 401; con un token inventado 401; ADMIN y BODEGUERO 200.
        String consulta = "resumen?desde=" + hoy() + "&hasta=" + hoy();
        assertThat(get(PANEL + consulta, null).estado()).isEqualTo(401);
        assertThat(get(PANEL + consulta, "esto-no-es-un-token").estado()).isEqualTo(401);
        get(PANEL + consulta, tokenAdmin()).esperando(200);
        get(PANEL + consulta, token).esperando(200);
    }

    // ================================================================== resumen: unidades, líneas sin costo y pérdidas

    @Test
    @DisplayName("HU-100 Unidades: con una sola unidad se devuelve la cantidad; con kg y L mezclados, solo el valor")
    void unidadesComunesOMezcladas() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        Catalogo categoria = crearCategoriaPropia(false);
        Catalogo kilos = crearUnidadPropia();
        Catalogo litros = crearUnidadPropia();
        Prod carne = crearProductoConUnidad(categoria, kilos);
        Prod aceite = crearProductoConUnidad(categoria, litros);
        entrada(carne.id(), 50, null, null).esperando(201);
        entrada(aceite.id(), 50, null, null).esperando(201);
        aprobar(bodeguero, pedirOk(empleado, carne.id(), 6)).esperando(200);

        JsonNode soloKilos = resumen(bodeguero, "&categoriaId=" + categoria.id());
        assertThat(soloKilos.path("unidadComun").asText()).isEqualTo(kilos.nombre());
        assertThat(soloKilos.path("cantidadNeta").asLong()).isEqualTo(6);

        aprobar(bodeguero, pedirOk(empleado, aceite.id(), 2)).esperando(200);
        JsonNode mezcla = resumen(bodeguero, "&categoriaId=" + categoria.id());
        assertThat(mezcla.path("consumoNetoValor").asLong()).isEqualTo(8_000);
        assertThat(mezcla.path("unidadComun").isNull()).isTrue();
        assertThat(mezcla.path("cantidadNeta").isNull()).isTrue();
    }

    @Test
    @DisplayName("HU-100 Pérdidas: merma y vencimiento van aparte, no entran al consumo, y una merma corregida no cuenta")
    void perdidasFueraDelConsumo() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod producto = productoConEntrada(categoria, 100);
        aprobar(bodeguero, pedirOk(empleado, producto.id(), 10)).esperando(200);
        salidaDeTipo(bodeguero, "MERMA", producto.id(), 3).esperando(201);
        salidaDeTipo(bodeguero, "VENCIMIENTO", producto.id(), 2).esperando(201);
        long equivocada = salidaDeTipo(bodeguero, "MERMA", producto.id(), 9).esperando(201).json().path("id").asLong();
        post("/api/movimientos/operaciones/" + equivocada + "/correccion", bodeguero.token(), null).esperando(201);

        JsonNode r = resumen(bodeguero, "&categoriaId=" + categoria.id());

        assertThat(r.path("consumoNetoValor").asLong()).isEqualTo(10_000);
        assertThat(r.path("perdidasValor").asLong()).isEqualTo(5_000); // 3 + 2 unidades; la merma corregida no cuenta
        // Con una persona elegida las pérdidas no se atribuyen a nadie.
        assertThat(resumen(bodeguero, "&categoriaId=" + categoria.id() + "&solicitanteId=" + empleado.id())
                .path("perdidasValor").asLong()).isZero();
    }

    @Test
    @DisplayName("HU-100 Líneas sin costo: no se valorizan y se cuentan aparte")
    void lineasSinCosto() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod sinCosto = crearProductoSinCosto(categoria);
        entradaDeTipo("INVENTARIO_INICIAL", sinCosto.id(), 20, null).esperando(201);
        Prod conCosto = productoConEntrada(categoria, 20);
        aprobar(bodeguero, pedirOk(empleado, sinCosto.id(), 5)).esperando(200);
        aprobar(bodeguero, pedirOk(empleado, conCosto.id(), 4)).esperando(200);

        JsonNode r = resumen(bodeguero, "&categoriaId=" + categoria.id());

        assertThat(r.path("consumoNetoValor").asLong()).isEqualTo(4_000);
        assertThat(r.path("lineasSinCosto").asLong()).isEqualTo(1);
    }

    // ================================================================== resumen: solicitado frente a entregado

    @Test
    @DisplayName("HU-100 Solicitado: solo pedidos aprobados; pendientes y rechazados no cuentan; se valoriza con el costo de referencia")
    void solicitadoSoloDePedidosAprobados() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod producto = productoConEntrada(categoria, 100);
        // Aprobado: pidió 10 y se entregaron 8.
        aprobar(bodeguero, pedirOk(empleado, producto.id(), 10), item(producto.id(), 8)).esperando(200);
        // Pendiente y rechazado: no cuentan.
        pedirOk(empleado, producto.id(), 50);
        rechazar(bodeguero, pedirOk(empleado, producto.id(), 70)).esperando(204);

        JsonNode r = resumen(bodeguero, "&categoriaId=" + categoria.id());

        assertThat(r.path("solicitadoValor").asLong()).isEqualTo(10_000);
        assertThat(r.path("solicitadoCantidad").asLong()).isEqualTo(10);
        assertThat(r.path("entregadoValor").asLong()).isEqualTo(8_000);
        assertThat(r.path("entregadoCantidad").asLong()).isEqualTo(8);
        assertThat(r.path("pedidos").asLong()).isEqualTo(1);
    }

    // ================================================================== consumo por período

    private static final LocalDate LUNES = LocalDate.of(2026, 3, 2);

    /** Escenario común: entregas de 10 (lun 2/3), 5 (dom 8/3), 4 (lun 9/3), 2 (mar 31/3) y 3 (mié 1/4); merma de 1 el 9/3. */
    private record Escenario(Cuenta bodeguero, Catalogo categoria) {}

    private Escenario escenarioDeSerie() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod producto = productoConEntrada(categoria, 100);
        entregar(bodeguero, empleado, producto.id(), 10, LUNES);
        entregar(bodeguero, empleado, producto.id(), 5, LUNES.plusDays(6));
        entregar(bodeguero, empleado, producto.id(), 4, LUNES.plusDays(7));
        entregar(bodeguero, empleado, producto.id(), 2, LocalDate.of(2026, 3, 31));
        entregar(bodeguero, empleado, producto.id(), 3, LocalDate.of(2026, 4, 1));
        long merma = salidaDeTipo(bodeguero, "MERMA", producto.id(), 1).esperando(201).json().path("id").asLong();
        fecharOperacion(merma, LUNES.plusDays(7).atTime(9, 0));
        return new Escenario(bodeguero, categoria);
    }

    private JsonNode serie(Escenario e, LocalDate desde, LocalDate hasta, String agrupar) {
        return get(PANEL + "consumo-periodo?desde=" + desde + "&hasta=" + hasta + "&agrupar=" + agrupar
                + "&categoriaId=" + e.categoria().id(), e.bodeguero().token()).esperando(200).json();
    }

    private static List<Long> valores(JsonNode serie, String campo) {
        return serie.path("puntos").findValues(campo).stream().map(JsonNode::asLong).toList();
    }

    private static List<String> inicios(JsonNode serie) {
        return serie.path("puntos").findValues("inicio").stream().map(JsonNode::asText).toList();
    }

    @Test
    @DisplayName("HU-100 Serie por día: un punto por día del rango, con ceros en los días sin movimiento")
    void serieAgrupadaPorDia() {
        JsonNode s = serie(escenarioDeSerie(), LUNES, LUNES.plusDays(8), "DIA");

        assertThat(inicios(s)).hasSize(9).first().isEqualTo("2026-03-02");
        assertThat(valores(s, "consumoValor")).containsExactly(10_000L, 0L, 0L, 0L, 0L, 0L, 5_000L, 4_000L, 0L);
        assertThat(valores(s, "perdidasValor")).containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 0L, 1_000L, 0L);
    }

    @Test
    @DisplayName("HU-100 Serie por semana: empieza el lunes, el rango parcial arranca en el lunes anterior y las semanas vacías van en cero")
    void serieAgrupadaPorSemana() {
        Escenario e = escenarioDeSerie();

        JsonNode s = serie(e, LUNES, LUNES.plusDays(21), "SEMANA");
        assertThat(inicios(s)).containsExactly("2026-03-02", "2026-03-09", "2026-03-16", "2026-03-23");
        assertThat(valores(s, "consumoValor")).containsExactly(15_000L, 4_000L, 0L, 0L);

        // Un rango que empieza a mitad de semana (miércoles 4/3) arranca en el lunes de esa semana.
        JsonNode parcial = serie(e, LUNES.plusDays(2), LUNES.plusDays(9), "SEMANA");
        assertThat(inicios(parcial)).containsExactly("2026-03-02", "2026-03-09");
    }

    @Test
    @DisplayName("HU-100 Serie por mes: el borde de mes separa marzo de abril")
    void serieAgrupadaPorMes() {
        JsonNode s = serie(escenarioDeSerie(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 30), "MES");

        assertThat(inicios(s)).containsExactly("2026-03-01", "2026-04-01");
        assertThat(valores(s, "consumoValor")).containsExactly(21_000L, 3_000L);
        assertThat(s.path("unidadComun").asText()).isNotBlank();
        assertThat(s.path("puntos").get(0).path("consumoCantidad").asLong()).isEqualTo(21);
    }

    // ------------------------------------------------------------------ ayudantes

    /** Consulta el resumen con un rango amplio (ayer a mañana) más los filtros extra indicados. */
    private JsonNode resumen(Cuenta quien, String filtrosExtra) {
        return get(PANEL + "resumen?desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(1) + filtrosExtra,
                quien.token()).esperando(200).json();
    }
}
