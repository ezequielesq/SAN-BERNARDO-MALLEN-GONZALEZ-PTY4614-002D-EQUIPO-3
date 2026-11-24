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
}
