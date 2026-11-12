package com.smartrdp.backend.flujos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flujo I: agenda de eventos del calendario. Es compartida por todos los bodegueros; el administrador no
 * tiene acceso (la ruta es solo del rol Bodeguero).
 */
class EventosFlujoTest extends FlujoBase {

    private static final AtomicInteger SEMANAS = new AtomicInteger();

    /** Día propio de la prueba, lejano y distinto en cada llamada: las listas por rango solo traen sus eventos. */
    private static LocalDate diaPropio() {
        return LocalDate.of(2100, 1, 1).plusDays(7L * SEMANAS.incrementAndGet());
    }

    @Test
    @DisplayName("Crear, listar por rango, editar y eliminar un evento")
    void crearListarEditarYEliminarUnEvento() {
        Cuenta autor = crearBodeguero();
        Cuenta otro = crearBodeguero();
        LocalDate dia = diaPropio();

        // Crear: con hora y con todo el día; el título y la descripción se recortan.
        var reunion = post("/api/eventos", autor.token(), objeto("titulo", "  Reunión con proveedor  ",
                "fecha", dia.toString(), "hora", "09:30", "descripcion", "  Llevar la lista de precios  "))
                .esperando(201).json();
        var inventario = post("/api/eventos", autor.token(), objeto("titulo", "Inventario general",
                "fecha", dia.toString(), "descripcion", "   ")).esperando(201).json();
        assertThat(reunion.path("id").asLong()).isPositive();
        assertThat(reunion.path("titulo").asText()).isEqualTo("Reunión con proveedor");
        assertThat(LocalDate.parse(reunion.path("fecha").asText())).isEqualTo(dia);
        assertThat(LocalTime.parse(reunion.path("hora").asText())).isEqualTo(LocalTime.of(9, 30));
        assertThat(reunion.path("descripcion").asText()).isEqualTo("Llevar la lista de precios");
        assertThat(reunion.path("creadoPorNombre").asText()).isEqualTo(autor.nombre());
        assertThat(inventario.path("hora").isNull()).isTrue();
        assertThat(inventario.path("descripcion").isNull()).isTrue();

        // Listar por rango: aparecen los de esos días, con los de todo el día primero.
        long reunionId = reunion.path("id").asLong();
        long inventarioId = inventario.path("id").asLong();
        var enElDia = idsDe(get("/api/eventos?desde=" + dia + "&hasta=" + dia, autor.token()).esperando(200).json());
        assertThat(enElDia).containsExactly(inventarioId, reunionId);

        // Los extremos del rango cuentan; un evento fuera de él no aparece.
        var antes = post("/api/eventos", autor.token(), objeto("titulo", "Día anterior",
                "fecha", dia.minusDays(1).toString())).esperando(201).json().path("id").asLong();
        var despues = post("/api/eventos", autor.token(), objeto("titulo", "Día posterior",
                "fecha", dia.plusDays(1).toString())).esperando(201).json().path("id").asLong();
        var soloMedio = idsDe(get("/api/eventos?desde=" + dia + "&hasta=" + dia, autor.token()).esperando(200).json());
        assertThat(soloMedio).containsExactly(inventarioId, reunionId).doesNotContain(antes, despues);
        var conExtremos = idsDe(get("/api/eventos?desde=" + dia.minusDays(1) + "&hasta=" + dia.plusDays(1),
                autor.token()).esperando(200).json());
        assertThat(conExtremos).containsExactly(antes, inventarioId, reunionId, despues);

        // Editar (lo hace otro bodeguero: la agenda es compartida): cambia los datos, no el autor.
        var editado = put("/api/eventos/" + reunionId, otro.token(), objeto("titulo", "Reunión reprogramada",
                "fecha", dia.plusDays(2).toString(), "hora", "15:00", "descripcion", "Nueva hora"))
                .esperando(200).json();
        assertThat(editado.path("titulo").asText()).isEqualTo("Reunión reprogramada");
        assertThat(LocalDate.parse(editado.path("fecha").asText())).isEqualTo(dia.plusDays(2));
        assertThat(LocalTime.parse(editado.path("hora").asText())).isEqualTo(LocalTime.of(15, 0));
        assertThat(editado.path("creadoPorNombre").asText()).isEqualTo(autor.nombre());
        assertThat(idsDe(get("/api/eventos?desde=" + dia + "&hasta=" + dia, autor.token()).esperando(200).json()))
                .containsExactly(inventarioId);
        var reprogramado = eventoPorId(get("/api/eventos?desde=" + dia.plusDays(2) + "&hasta=" + dia.plusDays(2),
                autor.token()).esperando(200).json(), reunionId);
        assertThat(reprogramado.path("titulo").asText()).isEqualTo("Reunión reprogramada");

        // Eliminar: desaparece del listado y borrarlo otra vez responde 404.
        delete("/api/eventos/" + reunionId, otro.token()).esperando(204);
        assertThat(idsDe(get("/api/eventos?desde=" + dia.plusDays(2) + "&hasta=" + dia.plusDays(2), autor.token())
                .esperando(200).json())).isEmpty();
        assertThat(delete("/api/eventos/" + reunionId, autor.token()).estado()).isEqualTo(404);
        assertThat(put("/api/eventos/" + reunionId, autor.token(), objeto("titulo", "Fantasma",
                "fecha", dia.toString())).estado()).isEqualTo(404);
        // Los demás eventos siguen ahí.
        assertThat(idsDe(get("/api/eventos?desde=" + dia.minusDays(1) + "&hasta=" + dia.plusDays(1), autor.token())
                .esperando(200).json())).containsExactly(antes, inventarioId, despues);
    }

    @Test
    @DisplayName("Un evento con título vacío o datos inválidos se rechaza y no se crea")
    void tituloVacioSeRechaza() {
        Cuenta bodeguero = crearBodeguero();
        LocalDate dia = diaPropio();

        for (Object titulo : new Object[] {"", "   ", null}) {
            var r = post("/api/eventos", bodeguero.token(), objeto("titulo", titulo, "fecha", dia.toString()));
            assertThat(r.estado()).as("título '%s'", titulo).isEqualTo(400);
            assertThat(r.json().path("fields").has("titulo")).isTrue();
        }
        var sinFecha = post("/api/eventos", bodeguero.token(), objeto("titulo", "Sin fecha"));
        assertThat(sinFecha.estado()).isEqualTo(400);
        assertThat(sinFecha.json().path("fields").has("fecha")).isTrue();
        assertThat(post("/api/eventos", bodeguero.token(), objeto("titulo", "T".repeat(121), "fecha", dia.toString()))
                .estado()).isEqualTo(400);
        assertThat(post("/api/eventos", bodeguero.token(), objeto("titulo", "Largo", "fecha", dia.toString(),
                "descripcion", "d".repeat(501))).estado()).isEqualTo(400);
        assertThat(post("/api/eventos", bodeguero.token(), objeto("titulo", "Fecha mala", "fecha", "no-es-fecha"))
                .estado()).isEqualTo(400);

        // Al editar, un título vacío tampoco se acepta y el evento conserva el suyo.
        long id = post("/api/eventos", bodeguero.token(), objeto("titulo", "Válido", "fecha", dia.toString()))
                .esperando(201).json().path("id").asLong();
        assertThat(put("/api/eventos/" + id, bodeguero.token(), objeto("titulo", "  ", "fecha", dia.toString()))
                .estado()).isEqualTo(400);
        var enElDia = get("/api/eventos?desde=" + dia + "&hasta=" + dia, bodeguero.token()).esperando(200).json();
        assertThat(idsDe(enElDia)).containsExactly(id);
        assertThat(eventoPorId(enElDia, id).path("titulo").asText()).isEqualTo("Válido");

        // Rangos inválidos al listar.
        var invertido = get("/api/eventos?desde=" + dia.plusDays(1) + "&hasta=" + dia, bodeguero.token());
        assertThat(invertido.estado()).isEqualTo(422);
        assertThat(invertido.campo()).isEqualTo("desde");
        var demasiadoLargo = get("/api/eventos?desde=" + dia + "&hasta=" + dia.plusDays(366), bodeguero.token());
        assertThat(demasiadoLargo.estado()).isEqualTo(422);
        get("/api/eventos?desde=" + dia + "&hasta=" + dia.plusDays(365), bodeguero.token()).esperando(200);
        assertThat(get("/api/eventos", bodeguero.token()).estado()).isEqualTo(400);
    }

    @Test
    @DisplayName("HU-250 Solo el Bodeguero usa la agenda: el administrador y quien no tiene sesión reciben error")
    void soloElBodegueroUsaLaAgenda() {
        Cuenta bodeguero = crearBodeguero();
        LocalDate dia = diaPropio();
        long id = post("/api/eventos", bodeguero.token(), objeto("titulo", "Del bodeguero", "fecha", dia.toString()))
                .esperando(201).json().path("id").asLong();
        String rango = "?desde=" + dia + "&hasta=" + dia;

        // Administrador: 403 en todo, y no logra tocar el evento.
        assertThat(get("/api/eventos" + rango, tokenAdmin()).estado()).isEqualTo(403);
        assertThat(post("/api/eventos", tokenAdmin(), objeto("titulo", "Del admin", "fecha", dia.toString()))
                .estado()).isEqualTo(403);
        assertThat(put("/api/eventos/" + id, tokenAdmin(), objeto("titulo", "Cambiado", "fecha", dia.toString()))
                .estado()).isEqualTo(403);
        assertThat(delete("/api/eventos/" + id, tokenAdmin()).estado()).isEqualTo(403);

        // Sin sesión: 401.
        assertThat(get("/api/eventos" + rango, null).estado()).isEqualTo(401);
        assertThat(post("/api/eventos", null, objeto("titulo", "Anónimo", "fecha", dia.toString())).estado())
                .isEqualTo(401);
        assertThat(delete("/api/eventos/" + id, null).estado()).isEqualTo(401);

        // El evento quedó intacto y no se creó ninguno más.
        List<JsonNode> enElDia = new ArrayList<>();
        get("/api/eventos" + rango, bodeguero.token()).esperando(200).json().forEach(enElDia::add);
        assertThat(enElDia).hasSize(1);
        assertThat(enElDia.get(0).path("titulo").asText()).isEqualTo("Del bodeguero");
    }

    // ------------------------------------------------------------------ ayudantes

    private static List<Long> idsDe(JsonNode eventos) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode e : eventos) ids.add(e.path("id").asLong());
        return ids;
    }

    private static JsonNode eventoPorId(JsonNode eventos, long id) {
        for (JsonNode e : eventos) {
            if (e.path("id").asLong() == id) return e;
        }
        throw new AssertionError("El evento " + id + " no está en la lista");
    }
}
