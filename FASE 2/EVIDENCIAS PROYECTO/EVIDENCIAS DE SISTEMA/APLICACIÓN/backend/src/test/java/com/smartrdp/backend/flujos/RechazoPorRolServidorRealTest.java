package com.smartrdp.backend.flujos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Códigos de rechazo de la API contra un servidor real (Tomcat embebido en un puerto aleatorio).
 *
 * <p>MockMvc no hace el despacho de error del contenedor de servlets, así que no ve lo que ve el navegador:
 * cuando la respuesta se envía con {@code sendError}, el contenedor vuelve a pasar la petición por la cadena de
 * seguridad (despacho ERROR hacia {@code /error}) y ahí el usuario ya no está autenticado. Estas pruebas
 * comprueban que un usuario autenticado sin permiso recibe 403 (y no 401, que el frontend interpreta como
 * sesión caducada) y que las peticiones anónimas siguen recibiendo 401.
 *
 * <p>No usa {@code ConfiguracionBdPruebas}: no vacía la base, para no invalidar los datos de las demás pruebas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RechazoPorRolServidorRealTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient cliente = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int puerto;

    @Value("${app.seed.admin-email}")
    private String correoAdmin;

    @Value("${app.seed.admin-password}")
    private String claveAdmin;

    @Test
    void administradorSinPermisoDeEscrituraRecibe403() throws Exception {
        String admin = iniciarSesion(correoAdmin, claveAdmin);

        var respuesta = enviar("POST", "/api/productos", admin, "{}");

        assertThat(respuesta.statusCode()).as("POST /api/productos del administrador: %s", respuesta.body())
                .isEqualTo(403);
    }

    @Test
    void administradorNoPuedeRegistrarEntradasNiBorrarEventos() throws Exception {
        String admin = iniciarSesion(correoAdmin, claveAdmin);

        assertThat(enviar("POST", "/api/movimientos/entradas", admin, "{}").statusCode()).isEqualTo(403);
        assertThat(enviar("DELETE", "/api/eventos/1", admin, null).statusCode()).isEqualTo(403);
    }

    @Test
    void bodegueroSinPermisoDeAdministracionRecibe403() throws Exception {
        String admin = iniciarSesion(correoAdmin, claveAdmin);
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String correo = "rechazo-" + sufijo + "@pruebas.test";
        String clave = "clave-" + sufijo;
        var creado = enviar("POST", "/api/usuarios", admin, JSON.writeValueAsString(Map.of(
                "nombre", "Bodeguero " + sufijo, "email", correo, "password", clave, "rol", "BODEGUERO")));
        assertThat(creado.statusCode()).as("alta del bodeguero: %s", creado.body()).isEqualTo(201);
        String bodeguero = iniciarSesion(correo, clave);

        var respuesta = enviar("GET", "/api/usuarios", bodeguero, null);

        assertThat(respuesta.statusCode()).as("GET /api/usuarios del bodeguero: %s", respuesta.body())
                .isEqualTo(403);
    }

    @Test
    void usuarioAutenticadoConPermisoNoRecibeRechazo() throws Exception {
        String admin = iniciarSesion(correoAdmin, claveAdmin);

        assertThat(enviar("GET", "/api/productos", admin, null).statusCode()).isEqualTo(200);
    }

    @Test
    void peticionAnonimaSigueRecibiendo401() throws Exception {
        assertThat(enviar("GET", "/api/productos", null, null).statusCode()).isEqualTo(401);
        assertThat(enviar("POST", "/api/productos", null, "{}").statusCode()).isEqualTo(401);
    }

    @Test
    void tokenInvalidoRecibe401() throws Exception {
        assertThat(enviar("GET", "/api/productos", "token-que-no-es-valido", null).statusCode()).isEqualTo(401);
    }

    private String iniciarSesion(String correo, String clave) throws Exception {
        var respuesta = enviar("POST", "/api/auth/login", null,
                JSON.writeValueAsString(Map.of("email", correo, "password", clave)));
        assertThat(respuesta.statusCode()).as("login: %s", respuesta.body()).isEqualTo(200);
        return JSON.readTree(respuesta.body()).path("token").asText();
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String token, String cuerpo) throws Exception {
        var peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json");
        if (token != null) peticion.header("Authorization", "Bearer " + token);
        peticion.method(metodo, cuerpo == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        return cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }
}
