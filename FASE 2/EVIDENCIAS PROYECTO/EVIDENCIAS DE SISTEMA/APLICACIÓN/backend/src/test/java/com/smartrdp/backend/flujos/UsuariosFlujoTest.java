package com.smartrdp.backend.flujos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** Flujo B: el administrador gestiona bodegueros y empleados. */
class UsuariosFlujoTest extends FlujoBase {

    @Test
    @DisplayName("HU-250 Crear Bodeguero y Empleado: quedan activos, en el listado y con el acceso propio de su rol")
    void crearBodegueroYEmpleado() {
        String sufijo = unico();
        var bodeguero = post("/api/usuarios", tokenAdmin(), objeto("nombre", "  Bodeguero " + sufijo + "  ",
                "email", "bod-" + sufijo + "@pruebas.test", "password", "clave-" + sufijo, "rol", "BODEGUERO"))
                .esperando(201).json();
        var empleado = post("/api/usuarios", tokenAdmin(), objeto("nombre", "Empleado " + sufijo,
                "email", "emp-" + sufijo + "@pruebas.test", "password", "clave-" + sufijo, "rol", "EMPLEADO"))
                .esperando(201).json();

        // La respuesta trae los datos del usuario (con el nombre recortado) y nunca la contraseña.
        assertThat(bodeguero.path("id").asLong()).isPositive();
        assertThat(bodeguero.path("nombre").asText()).isEqualTo("Bodeguero " + sufijo);
        assertThat(bodeguero.path("email").asText()).isEqualTo("bod-" + sufijo + "@pruebas.test");
        assertThat(bodeguero.path("rol").asText()).isEqualTo("BODEGUERO");
        assertThat(bodeguero.path("activo").asBoolean()).isTrue();
        assertThat(bodeguero.has("password")).isFalse();
        assertThat(empleado.path("rol").asText()).isEqualTo("EMPLEADO");
        assertThat(empleado.path("activo").asBoolean()).isTrue();

        // Ambos aparecen en el listado del administrador, cada uno con su rol.
        var listado = get("/api/usuarios", tokenAdmin()).esperando(200).json();
        JsonNode bodegueroListado = usuarioPorId(listado, bodeguero.path("id").asLong());
        JsonNode empleadoListado = usuarioPorId(listado, empleado.path("id").asLong());
        assertThat(bodegueroListado.path("rol").asText()).isEqualTo("BODEGUERO");
        assertThat(empleadoListado.path("rol").asText()).isEqualTo("EMPLEADO");
        assertThat(bodegueroListado.has("password")).isFalse();

        // El Bodeguero inicia sesión y entra a su módulo; el Empleado no inicia sesión: usa la vista pública.
        var login = iniciarSesion("bod-" + sufijo + "@pruebas.test", "clave-" + sufijo).esperando(200);
        assertThat(login.json().path("rol").asText()).isEqualTo("BODEGUERO");
        get("/api/productos", login.json().path("token").asText()).esperando(200);
        var loginEmpleado = iniciarSesion("emp-" + sufijo + "@pruebas.test", "clave-" + sufijo);
        assertThat(loginEmpleado.estado()).isEqualTo(422);
        assertThat(loginEmpleado.mensaje()).contains("no inician sesión");
        assertThat(loginEmpleado.json().has("token")).isFalse();

        // Solo el empleado figura en la lista de la vista pública.
        var publicos = get("/api/publico/empleados", null).esperando(200).json();
        assertThat(usuarioPorId(publicos, empleado.path("id").asLong()).path("nombre").asText())
                .isEqualTo("Empleado " + sufijo);
        assertThat(usuarioPorId(publicos, bodeguero.path("id").asLong()).isMissingNode()).isTrue();
    }

    @Test
    @DisplayName("HU-250 Correo duplicado rechazado, y los datos inválidos no crean usuarios")
    void correoDuplicadoYDatosInvalidosSeRechazan() {
        Cuenta existente = crearBodeguero();

        // Mismo correo, aun con otras mayúsculas y para otro rol.
        for (String correo : new String[] {existente.email(), existente.email().toUpperCase()}) {
            var duplicado = post("/api/usuarios", tokenAdmin(), objeto("nombre", "Otro " + unico(),
                    "email", correo, "password", "123456", "rol", "BODEGUERO"));
            assertThat(duplicado.estado()).isEqualTo(422);
            assertThat(duplicado.campo()).isEqualTo("email");
            assertThat(duplicado.mensaje()).contains("Ya existe un usuario con el correo");
        }
        var comoEmpleado = post("/api/usuarios", tokenAdmin(), objeto("nombre", "Otro " + unico(),
                "email", existente.email(), "password", "123456", "rol", "EMPLEADO"));
        assertThat(comoEmpleado.estado()).isEqualTo(422);
        assertThat(comoEmpleado.campo()).isEqualTo("email");

        // Un solo usuario con ese correo en el listado, y la clave original sigue siendo la válida.
        long conEseCorreo = 0;
        for (JsonNode u : get("/api/usuarios", tokenAdmin()).esperando(200).json()) {
            if (existente.email().equalsIgnoreCase(u.path("email").asText())) conEseCorreo++;
        }
        assertThat(conEseCorreo).isEqualTo(1);
        iniciarSesion(existente.email(), existente.clave()).esperando(200);

        // Al editar tampoco se puede tomar el correo de otro usuario.
        Cuenta otro = crearBodeguero();
        var robado = put("/api/usuarios/" + otro.id(), tokenAdmin(), objeto("nombre", otro.nombre(),
                "email", existente.email(), "rol", "BODEGUERO"));
        assertThat(robado.estado()).isEqualTo(422);
        assertThat(robado.campo()).isEqualTo("email");

        // Datos inválidos: rol que el administrador no gestiona, contraseña corta, correo mal escrito, nombre vacío.
        String sufijo = unico();
        var rolAdmin = post("/api/usuarios", tokenAdmin(), objeto("nombre", "Admin " + sufijo,
                "email", "adm-" + sufijo + "@pruebas.test", "password", "123456", "rol", "ADMIN"));
        assertThat(rolAdmin.estado()).isEqualTo(422);
        assertThat(rolAdmin.campo()).isEqualTo("rol");
        var corta = post("/api/usuarios", tokenAdmin(), objeto("nombre", "Corta " + sufijo,
                "email", "corta-" + sufijo + "@pruebas.test", "password", "12345", "rol", "BODEGUERO"));
        assertThat(corta.estado()).isEqualTo(400);
        assertThat(corta.json().path("fields").has("password")).isTrue();
        var correoMalo = post("/api/usuarios", tokenAdmin(), objeto("nombre", "Malo " + sufijo,
                "email", "esto-no-es-un-correo", "password", "123456", "rol", "BODEGUERO"));
        assertThat(correoMalo.estado()).isEqualTo(400);
        assertThat(correoMalo.json().path("fields").has("email")).isTrue();
        var sinNombre = post("/api/usuarios", tokenAdmin(), objeto("nombre", "  ",
                "email", "sin-nombre-" + sufijo + "@pruebas.test", "password", "123456", "rol", "BODEGUERO"));
        assertThat(sinNombre.estado()).isEqualTo(400);
        assertThat(sinNombre.json().path("fields").has("nombre")).isTrue();

        // Ninguno de esos intentos creó un usuario.
        var listado = get("/api/usuarios", tokenAdmin()).esperando(200).json();
        for (JsonNode u : listado) {
            assertThat(u.path("email").asText()).doesNotContain(sufijo);
        }

        // Dos empleados activos no pueden llamarse igual (se eligen por nombre en la vista pública).
        Cuenta empleado = crearEmpleado();
        var mismoNombre = post("/api/usuarios", tokenAdmin(), objeto("nombre", empleado.nombre().toUpperCase(),
                "email", "otro-" + sufijo + "@pruebas.test", "password", "123456", "rol", "EMPLEADO"));
        assertThat(mismoNombre.estado()).isEqualTo(422);
        assertThat(mismoNombre.campo()).isEqualTo("nombre");
    }

    @Test
    @DisplayName("HU-240 Desactivar un usuario le impide iniciar sesión; reactivarlo se lo permite")
    void desactivarImpideIniciarSesionYReactivarLoPermite() {
        Cuenta bodeguero = crearBodeguero();
        get("/api/productos", bodeguero.token()).esperando(200);

        delete("/api/usuarios/" + bodeguero.id(), tokenAdmin()).esperando(204);

        // Sin acceso: ni con la contraseña correcta, ni con la sesión que ya tenía abierta.
        var rechazado = iniciarSesion(bodeguero.email(), bodeguero.clave());
        assertThat(rechazado.estado()).isEqualTo(401);
        assertThat(rechazado.json().has("token")).isFalse();
        assertThat(get("/api/productos", bodeguero.token()).estado()).isEqualTo(401);

        // Sigue existiendo, marcado como inactivo; el listado de activos ya no lo incluye.
        var todos = get("/api/usuarios", tokenAdmin()).esperando(200).json();
        assertThat(usuarioPorId(todos, bodeguero.id()).path("activo").asBoolean()).isFalse();
        var activos = get("/api/usuarios?soloActivos=true", tokenAdmin()).esperando(200).json();
        assertThat(usuarioPorId(activos, bodeguero.id()).isMissingNode()).isTrue();

        put("/api/usuarios/" + bodeguero.id() + "/reactivar", tokenAdmin(), null).esperando(204);

        var aceptado = iniciarSesion(bodeguero.email(), bodeguero.clave()).esperando(200);
        get("/api/productos", aceptado.json().path("token").asText()).esperando(200);
        var reactivados = get("/api/usuarios?soloActivos=true", tokenAdmin()).esperando(200).json();
        assertThat(usuarioPorId(reactivados, bodeguero.id()).path("activo").asBoolean()).isTrue();

        // Un usuario que no existe responde 404.
        assertThat(delete("/api/usuarios/999999999", tokenAdmin()).estado()).isEqualTo(404);
    }

    @Test
    @DisplayName("HU-300 Desactivar un empleado lo saca de la vista pública y no puede pedir; reactivarlo lo devuelve")
    void desactivarEmpleadoLoSacaDeLaVistaPublica() {
        Cuenta empleado = crearEmpleado();
        var producto = productoConStock(10);
        pedirOk(empleado, producto.id(), 1);

        delete("/api/usuarios/" + empleado.id(), tokenAdmin()).esperando(204);

        var publicos = get("/api/publico/empleados", null).esperando(200).json();
        assertThat(usuarioPorId(publicos, empleado.id()).isMissingNode()).isTrue();
        var pedido = pedir(empleado, item(producto.id(), 1));
        assertThat(pedido.estado()).isEqualTo(422);
        assertThat(pedido.mensaje()).isEqualTo("Usuario o contraseña incorrectos.");

        put("/api/usuarios/" + empleado.id() + "/reactivar", tokenAdmin(), null).esperando(204);

        assertThat(usuarioPorId(get("/api/publico/empleados", null).esperando(200).json(), empleado.id())
                .path("nombre").asText()).isEqualTo(empleado.nombre());
        pedirOk(empleado, producto.id(), 1);
    }

    @Test
    @DisplayName("HU-250 Editar un usuario: cambia nombre y contraseña, y la contraseña anterior deja de servir")
    void editarUsuarioCambiaNombreYContrasena() {
        Cuenta bodeguero = crearBodeguero();
        String nuevoNombre = "Renombrado " + unico();

        var editado = put("/api/usuarios/" + bodeguero.id(), tokenAdmin(), objeto("nombre", nuevoNombre,
                "email", bodeguero.email(), "rol", "BODEGUERO", "password", "nueva-clave-1")).esperando(200).json();

        assertThat(editado.path("nombre").asText()).isEqualTo(nuevoNombre);
        assertThat(iniciarSesion(bodeguero.email(), bodeguero.clave()).estado()).isEqualTo(401);
        var login = iniciarSesion(bodeguero.email(), "nueva-clave-1").esperando(200);
        assertThat(login.json().path("nombre").asText()).isEqualTo(nuevoNombre);

        // Sin contraseña en la edición, la actual se conserva.
        put("/api/usuarios/" + bodeguero.id(), tokenAdmin(), objeto("nombre", nuevoNombre,
                "email", bodeguero.email(), "rol", "BODEGUERO")).esperando(200);
        iniciarSesion(bodeguero.email(), "nueva-clave-1").esperando(200);
    }

    private static JsonNode usuarioPorId(JsonNode lista, long id) {
        for (JsonNode u : lista) {
            if (u.path("id").asLong() == id) return u;
        }
        return tools.jackson.databind.node.MissingNode.getInstance();
    }
}
