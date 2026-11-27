package com.smartrdp.backend.flujos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

class ProveedoresFlujoTest extends FlujoBase {

    private static boolean contiene(JsonNode lista, long id) {
        for (JsonNode p : lista) if (p.path("id").asLong() == id) return true;
        return false;
    }

    /** «60000123-4» → «60.000.123-4» (el RUT tal como lo escribe una persona). */
    private static String conPuntos(String rutNormalizado) {
        String[] partes = rutNormalizado.split("-");
        String cuerpo = new StringBuilder(partes[0]).reverse().toString().replaceAll("(\\d{3})(?=\\d)", "$1.");
        return new StringBuilder(cuerpo).reverse() + "-" + partes[1];
    }

    @Test
    @DisplayName("HU-220 Crear un proveedor con RUT y nombre lo deja activo y disponible en la lista")
    void creaYLista() {
        String rut = rutUnico();
        String nombre = "Distribuidora " + unico();

        var creado = post("/api/proveedores", bodeguero().token(), objeto("rut", rut, "nombre", nombre))
                .esperando(201).json();

        assertThat(creado.path("rut").asText()).isEqualTo(rut);
        assertThat(creado.path("nombre").asText()).isEqualTo(nombre);
        assertThat(creado.path("activo").asBoolean()).isTrue();
        var lista = get("/api/proveedores", bodeguero().token()).esperando(200).json();
        assertThat(contiene(lista, creado.path("id").asLong())).isTrue();
    }

    @Test
    @DisplayName("HU-220 El RUT se acepta con puntos y se guarda normalizado")
    void normalizaElRut() {
        String rut = rutUnico();

        var creado = post("/api/proveedores", bodeguero().token(),
                objeto("rut", conPuntos(rut), "nombre", "Con puntos " + unico())).esperando(201).json();

        assertThat(creado.path("rut").asText()).isEqualTo(rut);
    }

    @Test
    @DisplayName("HU-220 Un RUT con dígito verificador incorrecto se rechaza")
    void rechazaDigitoInvalido() {
        var r = post("/api/proveedores", bodeguero().token(),
                objeto("rut", "12345678-9", "nombre", "Malo " + unico())).esperando(422);
        assertThat(r.campo()).isEqualTo("rut");
        assertThat(r.mensaje()).contains("dígito verificador");
    }

    @Test
    @DisplayName("HU-220 No se repiten el RUT ni el nombre (sin distinguir mayúsculas)")
    void rechazaRepetidos() {
        Prov existente = crearProveedor();

        var mismoRut = post("/api/proveedores", bodeguero().token(),
                objeto("rut", existente.rut(), "nombre", "Otro " + unico())).esperando(422);
        assertThat(mismoRut.campo()).isEqualTo("rut");
        // el mensaje muestra el RUT como lo escribe una persona, no el normalizado
        assertThat(mismoRut.mensaje()).contains(conPuntos(existente.rut()));

        var mismoNombre = post("/api/proveedores", bodeguero().token(),
                objeto("rut", rutUnico(), "nombre", existente.nombre().toUpperCase())).esperando(422);
        assertThat(mismoNombre.campo()).isEqualTo("nombre");
    }

    @Test
    @DisplayName("HU-220 El RUT con ceros a la izquierda es el mismo RUT: no se puede repetir")
    void elRutConCerosALaIzquierdaEsElMismo() {
        post("/api/proveedores", bodeguero().token(), objeto("rut", "1000005-K", "nombre", "Siete " + unico()))
                .esperando(201);

        var repetido = post("/api/proveedores", bodeguero().token(),
                objeto("rut", "01.000.005-k", "nombre", "Con cero " + unico())).esperando(422);

        assertThat(repetido.campo()).isEqualTo("rut");
        var ceros = post("/api/proveedores", bodeguero().token(),
                objeto("rut", "00000000-0", "nombre", "Ceros " + unico())).esperando(422);
        assertThat(ceros.campo()).isEqualTo("rut");
    }

    @Test
    @DisplayName("HU-220 Editar cambia el nombre y el RUT, sin chocar con otro proveedor")
    void edita() {
        Prov a = crearProveedor();
        Prov b = crearProveedor();
        String nuevoRut = rutUnico();

        var editado = put("/api/proveedores/" + a.id(), bodeguero().token(),
                objeto("rut", nuevoRut, "nombre", "Renombrado " + unico())).esperando(200).json();
        assertThat(editado.path("rut").asText()).isEqualTo(nuevoRut);

        var choca = put("/api/proveedores/" + a.id(), bodeguero().token(),
                objeto("rut", b.rut(), "nombre", "Renombrado " + unico())).esperando(422);
        assertThat(choca.campo()).isEqualTo("rut");
        put("/api/proveedores/999999999", bodeguero().token(), objeto("rut", rutUnico(), "nombre", "X " + unico()))
                .esperando(404);
    }

    @Test
    @DisplayName("HU-220 Deshabilitar lo saca de la lista de activos y reactivar lo devuelve")
    void deshabilitaYReactiva() {
        Prov p = crearProveedor();

        delete("/api/proveedores/" + p.id(), bodeguero().token()).esperando(204);
        assertThat(contiene(get("/api/proveedores?soloActivos=true", bodeguero().token()).json(), p.id())).isFalse();
        var todos = get("/api/proveedores?soloActivos=false", bodeguero().token()).esperando(200).json();
        assertThat(contiene(todos, p.id())).isTrue();

        put("/api/proveedores/" + p.id() + "/reactivar", bodeguero().token(), null).esperando(204);
        assertThat(contiene(get("/api/proveedores?soloActivos=true", bodeguero().token()).json(), p.id())).isTrue();
    }

    @Test
    @DisplayName("HU-220 Permisos: la administradora solo lee; sin sesión, 401")
    void permisos() {
        Prov p = crearProveedor();

        assertThat(get("/api/proveedores", tokenAdmin()).estado()).isEqualTo(200);
        assertThat(post("/api/proveedores", tokenAdmin(), objeto("rut", rutUnico(), "nombre", "No " + unico()))
                .estado()).isEqualTo(403);
        assertThat(delete("/api/proveedores/" + p.id(), tokenAdmin()).estado()).isEqualTo(403);
        assertThat(get("/api/proveedores", null).estado()).isEqualTo(401);
    }
}
