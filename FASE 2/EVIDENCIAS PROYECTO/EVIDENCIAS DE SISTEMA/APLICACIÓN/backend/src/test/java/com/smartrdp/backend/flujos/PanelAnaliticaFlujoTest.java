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

    // ------------------------------------------------------------------ ayudantes

    /** Consulta el resumen con un rango amplio (ayer a mañana) más los filtros extra indicados. */
    private JsonNode resumen(Cuenta quien, String filtrosExtra) {
        return get(PANEL + "resumen?desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(1) + filtrosExtra,
                quien.token()).esperando(200).json();
    }
}
