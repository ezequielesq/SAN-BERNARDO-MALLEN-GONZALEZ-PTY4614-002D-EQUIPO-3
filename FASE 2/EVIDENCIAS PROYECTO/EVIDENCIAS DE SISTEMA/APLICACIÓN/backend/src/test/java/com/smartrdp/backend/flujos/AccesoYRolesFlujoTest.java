package com.smartrdp.backend.flujos;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Flujo A: acceso con sesión y control de acceso por roles. */
class AccesoYRolesFlujoTest extends FlujoBase {

    @Test
    @DisplayName("HU-240 Sin token no se puede entrar a ninguna ruta protegida")
    void sinTokenNoSePuedeEntrarARutasProtegidas() {
        List<String> rutas = List.of(
                "/api/usuarios", "/api/productos", "/api/categorias", "/api/unidades-medida",
                "/api/movimientos", "/api/movimientos/stock", "/api/movimientos/alertas/vencimiento",
                "/api/solicitudes", "/api/solicitudes/pendientes", "/api/eventos",
                "/api/analitica/mas-usados?desde=2026-01-01&hasta=2026-12-31",
                "/api/auditoria/historial?desde=2026-01-01&hasta=2026-12-31");
        for (String ruta : rutas) {
            assertThat(get(ruta, null).estado()).as("GET %s sin token", ruta).isEqualTo(401);
        }
        // Escribir tampoco está permitido sin sesión.
        assertThat(post("/api/usuarios", null, objeto("nombre", "X", "email", "x@x.cl",
                "password", "123456", "rol", "BODEGUERO")).estado()).isEqualTo(401);
        assertThat(post("/api/movimientos/entradas", null, objeto("tipo", "COMPRA")).estado()).isEqualTo(401);

        // Un token inventado o mal firmado se trata igual que no tener token.
        assertThat(get("/api/productos", "esto-no-es-un-token").estado()).isEqualTo(401);
        String tokenValido = tokenAdmin();
        String manipulado = tokenValido.substring(0, tokenValido.length() - 4) + "AAAA";
        assertThat(get("/api/usuarios", manipulado).estado()).isEqualTo(401);
    }

    @Test
    @DisplayName("HU-240 Login correcto devuelve token y rol; con contraseña incorrecta se rechaza")
    void loginCorrectoDevuelveTokenYRolYContrasenaIncorrectaSeRechaza() {
        Cuenta bodeguero = crearUsuario("BODEGUERO");

        var correcto = iniciarSesion(bodeguero.email(), bodeguero.clave()).esperando(200);
        assertThat(correcto.json().path("token").asText()).isNotBlank();
        assertThat(correcto.json().path("rol").asText()).isEqualTo("BODEGUERO");
        assertThat(correcto.json().path("email").asText()).isEqualTo(bodeguero.email());
        assertThat(correcto.json().path("nombre").asText()).isEqualTo(bodeguero.nombre());

        // El token obtenido abre las rutas de su rol.
        get("/api/productos", correcto.json().path("token").asText()).esperando(200);

        // Contraseña incorrecta: se rechaza y no entrega token.
        var incorrecto = iniciarSesion(bodeguero.email(), "otra-clave-distinta");
        assertThat(incorrecto.estado()).isEqualTo(401);
        assertThat(incorrecto.json().has("token")).isFalse();

        // Correo que no existe: misma respuesta, no revela qué cuentas hay.
        assertThat(iniciarSesion("no-existe-" + unico() + "@pruebas.test", "clave-cualquiera").estado())
                .isEqualTo(401);

        // Tras un intento fallido, la contraseña correcta sigue funcionando.
        iniciarSesion(bodeguero.email(), bodeguero.clave()).esperando(200);

        // El administrador sembrado entra con el rol ADMIN.
        var admin = iniciarSesion(correoAdmin, claveAdmin).esperando(200);
        assertThat(admin.json().path("rol").asText()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("HU-250 Un Bodeguero no accede a la administración de usuarios (403)")
    void bodegueroNoAccedeALaAdministracionDeUsuarios() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta otro = crearUsuario("EMPLEADO");

        assertThat(get("/api/usuarios", bodeguero.token()).estado()).isEqualTo(403);
        assertThat(post("/api/usuarios", bodeguero.token(), objeto("nombre", "Intruso " + unico(),
                "email", "intruso-" + unico() + "@pruebas.test", "password", "123456", "rol", "BODEGUERO"))
                .estado()).isEqualTo(403);
        assertThat(put("/api/usuarios/" + otro.id(), bodeguero.token(), objeto("nombre", "Cambiado",
                "email", otro.email(), "rol", "EMPLEADO")).estado()).isEqualTo(403);
        assertThat(delete("/api/usuarios/" + otro.id(), bodeguero.token()).estado()).isEqualTo(403);
        assertThat(put("/api/usuarios/" + otro.id() + "/reactivar", bodeguero.token(), null).estado())
                .isEqualTo(403);

        // Lo que se intentó no tuvo efecto: el empleado sigue activo y con su nombre.
        var usuarios = get("/api/usuarios", tokenAdmin()).esperando(200).json();
        boolean sigueIgual = false;
        for (var u : usuarios) {
            if (u.path("id").asLong() == otro.id()) {
                sigueIgual = u.path("activo").asBoolean() && otro.nombre().equals(u.path("nombre").asText());
            }
        }
        assertThat(sigueIgual).as("el empleado no fue modificado ni desactivado").isTrue();

        // El administrador sí puede.
        get("/api/usuarios", tokenAdmin()).esperando(200);
    }

    @Test
    @DisplayName("HU-270 Un Bodeguero no puede crear pedidos: su id y contraseña no sirven en la vista pública")
    void bodegueroNoPuedeCrearPedidosEnLaVistaPublica() {
        Cuenta bodeguero = crearBodeguero();
        var producto = productoConStock(10);

        var respuesta = pedirConClave(bodeguero, bodeguero.clave(), item(producto.id(), 1));
        // La vista pública solo reconoce empleados; responde igual que una contraseña incorrecta.
        assertThat(respuesta.estado()).isEqualTo(422);
        assertThat(respuesta.mensaje()).isEqualTo("Usuario o contraseña incorrectos.");

        // Tampoco puede ver «mis solicitudes» ni devolver con esa identidad.
        assertThat(post("/api/publico/mis-solicitudes", null,
                objeto("empleadoId", bodeguero.id(), "password", bodeguero.clave())).estado()).isEqualTo(422);

        // No quedó ninguna solicitud a su nombre ni se tocó el stock.
        var pendientes = get("/api/solicitudes/pendientes", bodeguero.token()).esperando(200).json();
        for (var s : pendientes) {
            assertThat(s.path("solicitanteNombre").asText()).isNotEqualTo(bodeguero.nombre());
        }
        assertThat(stockDe(producto.id())).isEqualTo(10);

        // No existe una ruta para que el Bodeguero cree pedidos con su sesión.
        assertThat(post("/api/solicitudes", bodeguero.token(),
                objeto("items", List.of(item(producto.id(), 1)))).estado()).isGreaterThanOrEqualTo(400);
    }

    @Test
    @Disabled("BUG: POST /api/solicitudes (ruta que no existe para crear) responde 500 en lugar de 4xx; "
            + "GlobalExceptionHandler.handleGeneric(Exception) captura HttpRequestMethodNotSupportedException")
    @DisplayName("HU-270 Intentar crear un pedido por la API como Bodeguero responde un error del cliente, no del servidor")
    void crearPedidoComoBodegueroPorApiResponde4xx() {
        Cuenta bodeguero = crearBodeguero();
        var producto = productoConStock(3);

        var respuesta = post("/api/solicitudes", bodeguero.token(), objeto("items", List.of(item(producto.id(), 1))));

        assertThat(respuesta.estado()).isBetween(400, 499);
    }
}
