package com.smartrdp.backend.flujos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

import static org.assertj.core.api.Assertions.assertThat;

/** Flujo C: categorías y unidades de medida. */
class CatalogosFlujoTest extends FlujoBase {

    @Test
    @DisplayName("HU-10 Crear categoría y unidad de medida: quedan activas, en el listado y se pueden renombrar")
    void crearCategoriaYUnidad() {
        String sufijo = unico();
        String token = bodeguero().token();

        var categoria = post("/api/categorias", token, objeto("nombre", "  Lácteos " + sufijo + " "))
                .esperando(201).json();
        var unidad = post("/api/unidades-medida", token, objeto("nombre", "Caja " + sufijo)).esperando(201).json();

        assertThat(categoria.path("nombre").asText()).isEqualTo("Lácteos " + sufijo);
        assertThat(categoria.path("activo").asBoolean()).isTrue();
        assertThat(categoria.path("requierePtv").asBoolean()).isFalse();
        assertThat(unidad.path("nombre").asText()).isEqualTo("Caja " + sufijo);
        assertThat(unidad.path("activo").asBoolean()).isTrue();

        assertThat(porId(get("/api/categorias", token).esperando(200).json(), categoria.path("id").asLong())
                .path("nombre").asText()).isEqualTo("Lácteos " + sufijo);
        assertThat(porId(get("/api/unidades-medida", token).esperando(200).json(), unidad.path("id").asLong())
                .path("nombre").asText()).isEqualTo("Caja " + sufijo);

        // Renombrar y marcar la categoría como «requiere PTV».
        long categoriaId = categoria.path("id").asLong();
        var renombrada = put("/api/categorias/" + categoriaId, token, objeto("nombre", "Quesos " + sufijo))
                .esperando(200).json();
        assertThat(renombrada.path("nombre").asText()).isEqualTo("Quesos " + sufijo);
        var conPtv = put("/api/categorias/" + categoriaId + "/requiere-ptv", token, objeto("requierePtv", true))
                .esperando(200).json();
        assertThat(conPtv.path("requierePtv").asBoolean()).isTrue();
        var unidadRenombrada = put("/api/unidades-medida/" + unidad.path("id").asLong(), token,
                objeto("nombre", "Bandeja " + sufijo)).esperando(200).json();
        assertThat(unidadRenombrada.path("nombre").asText()).isEqualTo("Bandeja " + sufijo);

        // El administrador puede ver los catálogos, pero solo el Bodeguero los modifica.
        get("/api/categorias", tokenAdmin()).esperando(200);
        assertThat(post("/api/categorias", tokenAdmin(), objeto("nombre", "Del admin " + sufijo)).estado())
                .isEqualTo(403);
        assertThat(post("/api/unidades-medida", tokenAdmin(), objeto("nombre", "Del admin " + sufijo)).estado())
                .isEqualTo(403);
        assertThat(post("/api/categorias", null, objeto("nombre", "Anónima " + sufijo)).estado()).isEqualTo(401);
    }

    @Test
    @DisplayName("HU-10 Nombre duplicado rechazado en categorías y unidades, y los nombres inválidos no se aceptan")
    void nombreDuplicadoSeRechaza() {
        String token = bodeguero().token();
        Catalogo categoria = crearCategoriaPropia(false);
        Catalogo unidad = crearUnidadPropia();

        // Mismo nombre, también con otras mayúsculas y espacios alrededor.
        for (String nombre : new String[] {categoria.nombre(), categoria.nombre().toUpperCase(),
                "  " + categoria.nombre() + "  "}) {
            var repetida = post("/api/categorias", token, objeto("nombre", nombre));
            assertThat(repetida.estado()).as("categoría '%s'", nombre).isEqualTo(422);
            assertThat(repetida.campo()).isEqualTo("nombre");
            assertThat(repetida.mensaje()).contains("Ya existe la categoría");
        }
        for (String nombre : new String[] {unidad.nombre(), unidad.nombre().toLowerCase()}) {
            var repetida = post("/api/unidades-medida", token, objeto("nombre", nombre));
            assertThat(repetida.estado()).as("unidad '%s'", nombre).isEqualTo(422);
            assertThat(repetida.campo()).isEqualTo("nombre");
        }

        // Renombrar a un nombre que ya tiene otra también se rechaza; quedarse con el propio sí se puede.
        Catalogo otra = crearCategoriaPropia(false);
        var choque = put("/api/categorias/" + otra.id(), token, objeto("nombre", categoria.nombre()));
        assertThat(choque.estado()).isEqualTo(422);
        assertThat(choque.campo()).isEqualTo("nombre");
        put("/api/categorias/" + otra.id(), token, objeto("nombre", otra.nombre())).esperando(200);
        Catalogo otraUnidad = crearUnidadPropia();
        assertThat(put("/api/unidades-medida/" + otraUnidad.id(), token, objeto("nombre", unidad.nombre()))
                .estado()).isEqualTo(422);

        // Solo hay una con cada nombre en los listados.
        assertThat(cuantasConNombre(get("/api/categorias?soloActivas=false", token).esperando(200).json(),
                categoria.nombre())).isEqualTo(1);
        assertThat(cuantasConNombre(get("/api/unidades-medida?soloActivas=false", token).esperando(200).json(),
                unidad.nombre())).isEqualTo(1);

        // Nombre vacío o de más de 50 caracteres: error de validación y no se crea nada.
        assertThat(post("/api/categorias", token, objeto("nombre", "   ")).estado()).isEqualTo(400);
        assertThat(post("/api/unidades-medida", token, objeto("nombre", "")).estado()).isEqualTo(400);
        String largo = "L".repeat(51);
        assertThat(post("/api/categorias", token, objeto("nombre", largo)).estado()).isEqualTo(400);
        assertThat(cuantasConNombre(get("/api/categorias?soloActivas=false", token).esperando(200).json(), largo))
                .isZero();
    }

    @Test
    @DisplayName("HU-10 Desactivar y reactivar categorías y unidades: salen del listado activo y no admiten productos nuevos")
    void desactivarYReactivar() {
        String token = bodeguero().token();
        Catalogo categoria = crearCategoriaPropia(false);
        Catalogo unidad = crearUnidadPropia();
        Catalogo categoriaActiva = crearCategoriaPropia(false);
        Catalogo unidadActiva = crearUnidadPropia();
        // Un producto existente en ambos catálogos no se ve afectado por desactivarlos.
        long productoId = post("/api/productos", token, objeto("codigo", siguienteCodigo(),
                "nombre", "Con catálogos " + unico(), "categoriaId", categoria.id(),
                "unidadMedidaId", unidad.id(), "esPerecible", false, "stockMinimo", 5, "stockCritico", 2,
                "costoUnitario", 100)).esperando(201).json().path("id").asLong();

        delete("/api/categorias/" + categoria.id(), token).esperando(204);
        delete("/api/unidades-medida/" + unidad.id(), token).esperando(204);

        // Ya no están en los listados activos, pero siguen en el completo marcadas como inactivas.
        assertThat(porId(get("/api/categorias", token).esperando(200).json(), categoria.id()).isMissingNode())
                .isTrue();
        assertThat(porId(get("/api/unidades-medida", token).esperando(200).json(), unidad.id()).isMissingNode())
                .isTrue();
        assertThat(porId(get("/api/categorias?soloActivas=false", token).esperando(200).json(), categoria.id())
                .path("activo").asBoolean()).isFalse();
        assertThat(porId(get("/api/unidades-medida?soloActivas=false", token).esperando(200).json(), unidad.id())
                .path("activo").asBoolean()).isFalse();

        // No se pueden crear productos nuevos con una categoría o unidad desactivada; el nombre sigue ocupado.
        var enCategoria = post("/api/productos", token, objeto("codigo", siguienteCodigo(),
                "nombre", "Nuevo " + unico(), "categoriaId", categoria.id(), "unidadMedidaId", unidadActiva.id(),
                "esPerecible", false, "stockMinimo", 5, "stockCritico", 2, "costoUnitario", 100));
        assertThat(enCategoria.estado()).isEqualTo(422);
        assertThat(enCategoria.campo()).isEqualTo("categoriaId");
        var enUnidad = post("/api/productos", token, objeto("codigo", siguienteCodigo(),
                "nombre", "Nuevo " + unico(), "categoriaId", categoriaActiva.id(), "unidadMedidaId", unidad.id(),
                "esPerecible", false, "stockMinimo", 5, "stockCritico", 2, "costoUnitario", 100));
        assertThat(enUnidad.estado()).isEqualTo(422);
        assertThat(enUnidad.campo()).isEqualTo("unidadMedidaId");
        assertThat(post("/api/categorias", token, objeto("nombre", categoria.nombre())).estado()).isEqualTo(422);

        // El producto que ya existía sigue activo y con sus catálogos.
        var existente = get("/api/productos/" + productoId, token).esperando(200).json();
        assertThat(existente.path("activo").asBoolean()).isTrue();
        assertThat(existente.path("categoriaNombre").asText()).isEqualTo(categoria.nombre());
        assertThat(existente.path("unidadMedidaNombre").asText()).isEqualTo(unidad.nombre());

        put("/api/categorias/" + categoria.id() + "/reactivar", token, null).esperando(204);
        put("/api/unidades-medida/" + unidad.id() + "/reactivar", token, null).esperando(204);

        assertThat(porId(get("/api/categorias", token).esperando(200).json(), categoria.id())
                .path("activo").asBoolean()).isTrue();
        assertThat(porId(get("/api/unidades-medida", token).esperando(200).json(), unidad.id())
                .path("activo").asBoolean()).isTrue();
        post("/api/productos", token, objeto("codigo", siguienteCodigo(), "nombre", "Ya sí " + unico(),
                "categoriaId", categoria.id(), "unidadMedidaId", unidad.id(), "esPerecible", false,
                "stockMinimo", 5, "stockCritico", 2, "costoUnitario", 100)).esperando(201);

        // Un id que no existe responde 404.
        assertThat(delete("/api/categorias/999999999", token).estado()).isEqualTo(404);
        assertThat(put("/api/unidades-medida/999999999/reactivar", token, null).estado()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ ayudantes

    private static JsonNode porId(JsonNode lista, long id) {
        for (JsonNode e : lista) {
            if (e.path("id").asLong() == id) return e;
        }
        return MissingNode.getInstance();
    }

    private static long cuantasConNombre(JsonNode lista, String nombre) {
        long total = 0;
        for (JsonNode e : lista) {
            if (nombre.equals(e.path("nombre").asText())) total++;
        }
        return total;
    }
}
