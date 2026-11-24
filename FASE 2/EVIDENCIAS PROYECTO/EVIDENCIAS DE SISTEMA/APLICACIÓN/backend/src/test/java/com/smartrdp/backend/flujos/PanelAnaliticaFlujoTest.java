package com.smartrdp.backend.flujos;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    @DisplayName("HU-100 Unidades: la serie por período usa la misma regla que el resumen y cuenta también lo solicitado")
    void serieConUnidadSoloSolicitadaMezclada() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod carne = crearProductoConUnidad(categoria, crearUnidadPropia());
        Prod aceite = crearProductoConUnidad(categoria, crearUnidadPropia());
        entrada(carne.id(), 50, null, null).esperando(201);
        entrada(aceite.id(), 50, null, null).esperando(201);
        // Pidió carne (kg) y aceite (L), pero solo se le entregó la carne.
        long pedido = pedir(empleado, item(carne.id(), 3), item(aceite.id(), 2)).esperando(201).json()
                .path("id").asLong();
        aprobar(bodeguero, pedido, item(carne.id(), 3), item(aceite.id(), 0)).esperando(200);

        String filtro = "&categoriaId=" + categoria.id();
        JsonNode resumen = resumen(bodeguero, filtro);
        JsonNode serie = get(PANEL + "consumo-periodo?desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(1)
                + "&agrupar=DIA" + filtro, bodeguero.token()).esperando(200).json();

        assertThat(resumen.path("unidadComun").isNull()).isTrue();
        assertThat(serie.path("unidadComun").isNull()).isTrue();
        assertThat(serie.path("puntos").findValues("consumoCantidad")).allMatch(JsonNode::isNull);
        assertThat(valores(serie, "consumoValor").stream().mapToLong(Long::longValue).sum()).isEqualTo(3_000);
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

    // ================================================================== top de productos, personas, solicitantes

    @Test
    @DisplayName("HU-110 Productos más usados: por valor neto de devoluciones, con límite")
    void productosMasUsados() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod a = productoConEntrada(categoria, 100);
        Prod b = productoConEntrada(categoria, 100);
        Prod c = productoConEntrada(categoria, 100);
        long pedidoA = pedirOk(empleado, a.id(), 10);
        aprobar(bodeguero, pedidoA).esperando(200);
        long devolucion = devolver(empleado, pedidoA, item(a.id(), 2)).esperando(201).json().path("id").asLong();
        aprobar(bodeguero, devolucion).esperando(200); // A neto 8
        aprobar(bodeguero, pedirOk(empleado, b.id(), 15)).esperando(200); // B 15
        aprobar(bodeguero, pedirOk(empleado, c.id(), 3)).esperando(200); // C 3

        String base = "productos-top?desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(1)
                + "&categoriaId=" + categoria.id();
        JsonNode todos = get(PANEL + base, bodeguero.token()).esperando(200).json();
        assertThat(todos).hasSize(3);
        assertThat(todos.get(0).path("productoId").asLong()).isEqualTo(b.id());
        assertThat(todos.get(0).path("cantidad").asLong()).isEqualTo(15);
        assertThat(todos.get(0).path("valor").asLong()).isEqualTo(15_000);
        assertThat(todos.get(0).path("nombre").asText()).isEqualTo(b.nombre());
        assertThat(todos.get(1).path("productoId").asLong()).isEqualTo(a.id());
        assertThat(todos.get(1).path("cantidad").asLong()).isEqualTo(8);
        assertThat(todos.get(2).path("productoId").asLong()).isEqualTo(c.id());

        assertThat(get(PANEL + base + "&limite=2", bodeguero.token()).esperando(200).json()).hasSize(2);
        assertThat(get(PANEL + base + "&limite=0", bodeguero.token()).estado()).isEqualTo(422);
        // El filtro por productos recorta la lista.
        assertThat(get(PANEL + base + "&productoIds=" + c.id(), bodeguero.token()).esperando(200).json()).hasSize(1);
    }

    @Test
    @DisplayName("HU-100 Pedidos por persona: solicitado, entregado, devuelto y neto de cada empleado")
    void pedidosPorPersona() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta ana = crearEmpleado();
        Cuenta beto = crearEmpleado();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod producto = productoConEntrada(categoria, 100);
        long pedidoAna = pedirOk(ana, producto.id(), 10);
        aprobar(bodeguero, pedidoAna, item(producto.id(), 8)).esperando(200); // pidió 10, se entregaron 8
        long devolucion = devolver(ana, pedidoAna, item(producto.id(), 2)).esperando(201).json().path("id").asLong();
        aprobar(bodeguero, devolucion).esperando(200);
        aprobar(bodeguero, pedirOk(beto, producto.id(), 5)).esperando(200);

        String base = "pedidos-por-persona?desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(1)
                + "&categoriaId=" + categoria.id();
        JsonNode r = get(PANEL + base, bodeguero.token()).esperando(200).json();

        JsonNode filas = r.path("filas");
        assertThat(filas).hasSize(2);
        JsonNode filaAna = filas.get(0); // neto 6000 > 5000
        assertThat(filaAna.path("usuarioId").asLong()).isEqualTo(ana.id());
        assertThat(filaAna.path("nombre").asText()).isEqualTo(ana.nombre());
        assertThat(filaAna.path("pedidos").asLong()).isEqualTo(1);
        assertThat(filaAna.path("solicitado").path("valor").asLong()).isEqualTo(10_000);
        assertThat(filaAna.path("solicitado").path("cantidad").asLong()).isEqualTo(10);
        assertThat(filaAna.path("entregado").path("valor").asLong()).isEqualTo(8_000);
        assertThat(filaAna.path("devuelto").path("valor").asLong()).isEqualTo(2_000);
        assertThat(filaAna.path("neto").path("valor").asLong()).isEqualTo(6_000);
        assertThat(filaAna.path("neto").path("cantidad").asLong()).isEqualTo(6);
        assertThat(filas.get(1).path("usuarioId").asLong()).isEqualTo(beto.id());
        assertThat(filas.get(1).path("neto").path("valor").asLong()).isEqualTo(5_000);

        JsonNode soloBeto = get(PANEL + base + "&solicitanteId=" + beto.id(), bodeguero.token()).esperando(200).json();
        assertThat(soloBeto.path("filas")).hasSize(1);
    }

    @Test
    @DisplayName("HU-100 Pedidos por persona: con unidades mezcladas solo se muestra el valor")
    void pedidosPorPersonaConUnidadesMezcladas() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod carne = crearProductoConUnidad(categoria, crearUnidadPropia());
        Prod aceite = crearProductoConUnidad(categoria, crearUnidadPropia());
        entrada(carne.id(), 20, null, null).esperando(201);
        entrada(aceite.id(), 20, null, null).esperando(201);
        aprobar(bodeguero, pedirOk(empleado, carne.id(), 3)).esperando(200);
        aprobar(bodeguero, pedirOk(empleado, aceite.id(), 2)).esperando(200);

        JsonNode r = get(PANEL + "pedidos-por-persona?desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(1)
                + "&categoriaId=" + categoria.id(), bodeguero.token()).esperando(200).json();

        assertThat(r.path("unidadComun").isNull()).isTrue();
        JsonNode fila = r.path("filas").get(0);
        assertThat(fila.path("neto").path("valor").asLong()).isEqualTo(5_000);
        assertThat(fila.path("neto").path("cantidad").isNull()).isTrue();
        assertThat(fila.path("solicitado").path("cantidad").isNull()).isTrue();
    }

    @Test
    @DisplayName("Solicitantes: lista los empleados activos para el filtro (ADMIN y BODEGUERO)")
    void solicitantes() {
        Cuenta empleado = crearEmpleado();
        Cuenta otroBodeguero = crearBodeguero();

        for (String token : List.of(bodeguero().token(), tokenAdmin())) {
            JsonNode lista = get(PANEL + "solicitantes", token).esperando(200).json();
            List<Long> ids = lista.findValues("id").stream().map(JsonNode::asLong).toList();
            assertThat(ids).contains(empleado.id()).doesNotContain(otroBodeguero.id());
            assertThat(lista.get(0).path("nombre").asText()).isNotBlank();
        }
        assertThat(get(PANEL + "solicitantes", null).estado()).isEqualTo(401);
    }

    // ================================================================== costo obligatorio en compras

    @Test
    @DisplayName("HU-140 Una compra sin costo se rechaza; inventario inicial y ajustes siguen con costo opcional")
    void compraExigeCosto() {
        Prod producto = crearProducto(false);

        var sinCosto = entradaDeTipo("COMPRA", producto.id(), 5, null);
        assertThat(sinCosto.estado()).isEqualTo(422);
        assertThat(sinCosto.campo()).isEqualTo("costoUnitario");
        assertThat(stockDe(producto.id())).isZero();

        compra(producto.id(), 5, 1_200).esperando(201);
        entradaDeTipo("INVENTARIO_INICIAL", producto.id(), 3, null).esperando(201);
        entradaDeTipo("AJUSTE_ENTRADA", producto.id(), 2, null).esperando(201);
        assertThat(stockDe(producto.id())).isEqualTo(10);
    }

    @Test
    @DisplayName("HU-140 Importar entradas: la fila de compra sin costo es un error de fila; con otro tipo no lo es")
    void importarCompraExigeCosto() {
        Prod producto = crearProducto(false);
        var sinCosto = List.of(objeto("fila", 2, "codigo", producto.codigo(), "cantidad", 4));
        String validar = "/api/movimientos/entradas/importar/validar";
        String importar = "/api/movimientos/entradas/importar";
        String token = bodeguero().token();

        JsonNode compraSinCosto = post(validar, token, objeto("tipo", "COMPRA", "filas", sinCosto)).esperando(200).json();
        assertThat(compraSinCosto.get(0).path("errores").path("costoUnitario").asText()).contains("costo");
        JsonNode inicial = post(validar, token, objeto("tipo", "INVENTARIO_INICIAL", "filas", sinCosto))
                .esperando(200).json();
        assertThat(inicial.get(0).path("errores").has("costoUnitario")).isFalse();

        assertThat(post(importar, token, objeto("tipo", "COMPRA", "filas", sinCosto)).estado()).isEqualTo(422);
        assertThat(stockDe(producto.id())).isZero();
        var conCosto = List.of(objeto("fila", 2, "codigo", producto.codigo(), "cantidad", 4, "costoUnitario", 900));
        post(importar, token, objeto("tipo", "COMPRA", "filas", conCosto)).esperando(201);
        assertThat(stockDe(producto.id())).isEqualTo(4);
    }

    // ================================================================== vencimientos y exportación con filtros

    @Test
    @DisplayName("HU-130 Vencimientos filtrados por productos, tanto con umbral de días como por rango")
    void vencimientosFiltranPorProductos() {
        Cuenta bodeguero = crearBodeguero();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod uno = crearProductoEn(categoria.id(), true);
        Prod dos = crearProductoEn(categoria.id(), true);
        entrada(uno.id(), 4, hoy().plusDays(5), "U-" + unico()).esperando(201);
        entrada(dos.id(), 6, hoy().plusDays(8), "D-" + unico()).esperando(201);
        String base = "categoriaId=" + categoria.id();

        assertThat(idsDe(get("/api/analitica/vencimientos?dias=30&" + base, bodeguero.token()).esperando(200).json()))
                .containsExactlyInAnyOrder(uno.id(), dos.id());
        assertThat(idsDe(get("/api/analitica/vencimientos?dias=30&" + base + "&productoIds=" + uno.id(),
                bodeguero.token()).esperando(200).json())).containsExactly(uno.id());
        assertThat(idsDe(get("/api/analitica/vencimientos/rango?desde=" + hoy() + "&hasta=" + hoy().plusDays(30)
                + "&" + base + "&productoIds=" + dos.id() + "," + uno.id(), bodeguero.token()).esperando(200).json()))
                .containsExactlyInAnyOrder(uno.id(), dos.id());
        assertThat(idsDe(get("/api/analitica/vencimientos/rango?desde=" + hoy() + "&hasta=" + hoy().plusDays(30)
                + "&" + base + "&productoIds=" + dos.id(), bodeguero.token()).esperando(200).json()))
                .containsExactly(dos.id());
    }

    @Test
    @DisplayName("HU-120 Exportar a Excel respeta los filtros del panel: productos y persona")
    void exportarConFiltros() throws Exception {
        Cuenta bodeguero = crearBodeguero();
        Cuenta ana = crearEmpleado();
        Cuenta beto = crearEmpleado();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod uno = productoConEntrada(categoria, 50);
        Prod dos = productoConEntrada(categoria, 50);
        aprobar(bodeguero, pedirOk(ana, uno.id(), 3)).esperando(200);
        aprobar(bodeguero, pedirOk(beto, dos.id(), 2)).esperando(200);
        String base = "/api/analitica/exportar-movimientos?desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(1)
                + "&categoriaId=" + categoria.id();

        assertThat(productosDelExcel(base, bodeguero)).hasSize(4); // 2 entradas + 2 entregas
        assertThat(productosDelExcel(base + "&productoIds=" + uno.id(), bodeguero)).containsOnly(uno.nombre()).hasSize(2);
        assertThat(productosDelExcel(base + "&solicitanteId=" + ana.id(), bodeguero)).containsExactly(uno.nombre());
    }

    @Test
    @DisplayName("HU-120 Exportar valida el filtro igual que el panel: rango invertido, más de 366 días o más de 50 productos dan 422")
    void exportarValidaElFiltro() {
        String token = bodeguero().token();
        String exportar = "/api/analitica/exportar-movimientos?";

        var invertido = get(exportar + "desde=" + hoy() + "&hasta=" + hoy().minusDays(1), token);
        assertThat(invertido.estado()).isEqualTo(422);
        assertThat(invertido.campo()).isEqualTo("desde");
        var largo = get(exportar + "desde=" + hoy().minusDays(366) + "&hasta=" + hoy(), token);
        assertThat(largo.estado()).isEqualTo(422);
        assertThat(largo.campo()).isEqualTo("hasta");
        String cincuentaYUno = IntStream.rangeClosed(1, 51).mapToObj(Integer::toString).collect(Collectors.joining(","));
        var demasiados = get(exportar + "desde=" + hoy() + "&hasta=" + hoy() + "&productoIds=" + cincuentaYUno, token);
        assertThat(demasiados.estado()).isEqualTo(422);
        assertThat(demasiados.campo()).isEqualTo("productoIds");
        // El límite exacto sigue pasando.
        descargar(exportar + "desde=" + hoy().minusDays(365) + "&hasta=" + hoy(), token).esperando(200);
    }

    // ================================================================== historial de precios

    @Test
    @DisplayName("HU-140 Historial de precios: solo compras, con último, anterior, variación, mínimo, máximo y promedios")
    void historialDePrecios() {
        Cuenta bodeguero = crearBodeguero();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod producto = crearProductoEn(categoria.id(), false);
        long c1 = compra(producto.id(), 10, 1_000).esperando(201).json().path("id").asLong();
        long c2 = compra(producto.id(), 30, 1_200).esperando(201).json().path("id").asLong();
        long c3 = compra(producto.id(), 20, 900).esperando(201).json().path("id").asLong();
        fecharOperacion(c1, LocalDate.of(2026, 3, 2).atTime(10, 0));
        fecharOperacion(c2, LocalDate.of(2026, 4, 6).atTime(10, 0));
        fecharOperacion(c3, LocalDate.of(2026, 5, 4).atTime(10, 0));
        entradaDeTipo("INVENTARIO_INICIAL", producto.id(), 5, 5_000).esperando(201); // no es una compra
        String base = PANEL + "precios/" + producto.id() + "?";

        JsonNode r = get(base + "desde=2026-03-01&hasta=2026-06-30", bodeguero.token()).esperando(200).json();
        assertThat(r.path("nombre").asText()).isEqualTo(producto.nombre());
        assertThat(r.path("unidad").asText()).isNotBlank();
        assertThat(r.path("compras").findValues("costo").stream().map(JsonNode::asInt).toList())
                .containsExactly(1_000, 1_200, 900);
        assertThat(r.path("compras").findValues("fecha").stream().map(JsonNode::asText).toList())
                .containsExactly("2026-03-02", "2026-04-06", "2026-05-04");
        assertThat(r.path("ultimo").asInt()).isEqualTo(900);
        assertThat(r.path("anterior").asInt()).isEqualTo(1_200);
        assertThat(r.path("variacionPct").asDouble()).isEqualTo(-25.0);
        assertThat(r.path("minimo").asInt()).isEqualTo(900);
        assertThat(r.path("maximo").asInt()).isEqualTo(1_200);
        assertThat(r.path("promedio").asDouble()).isEqualTo(1_033.3);
        assertThat(r.path("promedioPonderado").asDouble()).isEqualTo(1_066.7);

        // Con una sola compra en el rango no hay estadísticos; sin compras tampoco.
        JsonNode una = get(base + "desde=2026-03-01&hasta=2026-03-31", bodeguero.token()).esperando(200).json();
        assertThat(una.path("compras")).hasSize(1);
        assertThat(una.path("ultimo").isNull()).isTrue();
        assertThat(una.path("variacionPct").isNull()).isTrue();
        assertThat(get(base + "desde=2026-07-01&hasta=2026-07-31", bodeguero.token()).esperando(200).json()
                .path("compras")).isEmpty();

        // Producto inexistente, rango inválido y acceso.
        assertThat(get(PANEL + "precios/999999999?desde=2026-03-01&hasta=2026-03-31", bodeguero.token()).estado())
                .isEqualTo(404);
        assertThat(get(base + "desde=2026-04-01&hasta=2026-03-01", bodeguero.token()).estado()).isEqualTo(422);
        assertThat(get(base + "desde=2026-03-01&hasta=2026-03-31", null).estado()).isEqualTo(401);
        get(base + "desde=2026-03-01&hasta=2026-03-31", tokenAdmin()).esperando(200);
    }

    // ------------------------------------------------------------------ ayudantes

    private static List<Long> idsDe(JsonNode vencimientos) {
        return vencimientos.findValues("productoId").stream().map(JsonNode::asLong).toList();
    }

    /** Nombres de producto de las filas de datos del Excel exportado (columna «Producto»). */
    private List<String> productosDelExcel(String url, Cuenta quien) throws Exception {
        Descarga descarga = descargar(url, quien.token()).esperando(200);
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(descarga.bytes()))) {
            var hoja = libro.getSheetAt(0);
            List<String> productos = new ArrayList<>();
            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                productos.add(hoja.getRow(i).getCell(2).getStringCellValue());
            }
            return productos;
        }
    }

    /** Consulta el resumen con un rango amplio (ayer a mañana) más los filtros extra indicados. */
    private JsonNode resumen(Cuenta quien, String filtrosExtra) {
        return get(PANEL + "resumen?desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(1) + filtrosExtra,
                quien.token()).esperando(200).json();
    }
}
