package com.smartrdp.backend.flujos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Productos clave: la lista personal de productos que cada usuario quiere seguir de cerca (tabla productos_clave). */
class ProductosClaveFlujoTest extends FlujoBase {

    private static final String URL = "/api/analitica/panel/productos-clave";

    private Respuesta guardar(String token, Long... ids) {
        return put(URL, token, objeto("productoIds", List.of(ids)));
    }

    private static List<Long> idsDe(JsonNode lista) {
        return lista.findValues("productoId").stream().map(JsonNode::asLong).toList();
    }

    private Prod producto(Catalogo categoria) {
        return crearProductoEn(categoria.id(), false);
    }

    @Test
    @DisplayName("Productos clave: se guardan, se leen por nombre, y cada guardado reemplaza la lista completa")
    void guardarLeerYReemplazar() {
        Cuenta usuario = crearBodeguero();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod a = producto(categoria);
        Prod b = producto(categoria);
        Prod c = producto(categoria);
        assertThat(get(URL, usuario.token()).esperando(200).json()).isEmpty();

        JsonNode guardado = guardar(usuario.token(), a.id(), b.id()).esperando(200).json();
        assertThat(idsDe(guardado)).containsExactlyInAnyOrder(a.id(), b.id());
        assertThat(guardado.findValues("nombre").stream().map(JsonNode::asText).toList()).isSorted();
        assertThat(idsDe(get(URL, usuario.token()).esperando(200).json())).containsExactlyInAnyOrder(a.id(), b.id());

        // Reemplazo completo: lo que no se envía se elimina.
        assertThat(idsDe(guardar(usuario.token(), b.id(), c.id()).esperando(200).json()))
                .containsExactlyInAnyOrder(b.id(), c.id());
        // Duplicados se ignoran y guardar dos veces el mismo par no lo repite.
        assertThat(idsDe(guardar(usuario.token(), a.id(), a.id(), b.id()).esperando(200).json())).hasSize(2);
        guardar(usuario.token(), a.id(), b.id()).esperando(200);
        Integer filas = jdbc.queryForObject("SELECT count(*) FROM productos_clave WHERE usuario_id = ? AND producto_id = ?",
                Integer.class, usuario.id(), a.id());
        assertThat(filas).isEqualTo(1);
        // La restricción única existe en la base: un duplicado directo falla.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO productos_clave (usuario_id, producto_id) VALUES (?, ?)",
                usuario.id(), a.id())).hasMessageContaining("uq_productos_clave_usuario_producto");
        // Lista vacía borra todo.
        assertThat(guardar(usuario.token()).esperando(200).json()).isEmpty();
        assertThat(get(URL, usuario.token()).esperando(200).json()).isEmpty();
    }

    @Test
    @DisplayName("Productos clave: cada usuario ve solo su lista; la Administradora también tiene la suya")
    void aislamientoEntreUsuarios() {
        Cuenta uno = crearBodeguero();
        Cuenta dos = crearBodeguero();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod a = producto(categoria);
        Prod b = producto(categoria);
        guardar(uno.token(), a.id()).esperando(200);
        guardar(dos.token(), b.id()).esperando(200);
        try {
            guardar(tokenAdmin(), a.id(), b.id()).esperando(200);

            assertThat(idsDe(get(URL, uno.token()).esperando(200).json())).containsExactly(a.id());
            assertThat(idsDe(get(URL, dos.token()).esperando(200).json())).containsExactly(b.id());
            assertThat(idsDe(get(URL, tokenAdmin()).esperando(200).json())).containsExactlyInAnyOrder(a.id(), b.id());
        } finally {
            guardar(tokenAdmin()).esperando(200); // la cuenta de administración se comparte entre pruebas
        }
    }

    @Test
    @DisplayName("Productos clave: más de 50 o un id inexistente se rechazan sin tocar la lista guardada")
    void validaciones() {
        Cuenta usuario = crearBodeguero();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod a = producto(categoria);
        guardar(usuario.token(), a.id()).esperando(200);

        var muchos = put(URL, usuario.token(),
                objeto("productoIds", LongStream.rangeClosed(1, 51).boxed().toList()));
        assertThat(muchos.estado()).isEqualTo(422);
        assertThat(muchos.campo()).isEqualTo("productoIds");
        var inexistente = guardar(usuario.token(), a.id(), 9_999_999_999L);
        assertThat(inexistente.estado()).isEqualTo(422);
        assertThat(inexistente.campo()).isEqualTo("productoIds");
        assertThat(put(URL, usuario.token(), objeto()).estado()).isEqualTo(400); // falta productoIds

        assertThat(idsDe(get(URL, usuario.token()).esperando(200).json())).containsExactly(a.id());
    }

    @Test
    @DisplayName("Productos clave: un producto desactivado se omite al leer")
    void productoDesactivadoSeOmite() {
        Cuenta usuario = crearBodeguero();
        Catalogo categoria = crearCategoriaPropia(false);
        Prod a = producto(categoria);
        Prod b = producto(categoria);
        guardar(usuario.token(), a.id(), b.id()).esperando(200);

        assertThat(delete("/api/productos/" + a.id(), usuario.token()).estado()).isIn(200, 204);

        assertThat(idsDe(get(URL, usuario.token()).esperando(200).json())).containsExactly(b.id());
    }

    @Test
    @DisplayName("Productos clave: sin sesión se rechaza")
    void exigeSesion() {
        assertThat(get(URL, null).estado()).isEqualTo(401);
        assertThat(put(URL, null, objeto("productoIds", List.of())).estado()).isEqualTo(401);
        assertThat(get(URL, "esto-no-es-un-token").estado()).isEqualTo(401);
    }
}
