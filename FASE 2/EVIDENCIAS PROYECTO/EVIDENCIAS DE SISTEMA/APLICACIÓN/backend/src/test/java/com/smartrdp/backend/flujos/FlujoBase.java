package com.smartrdp.backend.flujos;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base de las pruebas de flujo: cada prueba recorre la API real (seguridad, JWT, servicios y la base
 * PostgreSQL con sus triggers) y cada llamada confirma su transacción, como en producción.
 *
 * <p>El contexto se comparte entre todas las clases y la base no se limpia entre pruebas: cada prueba crea
 * sus propios datos con nombres y códigos únicos y solo afirma sobre ellos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfiguracionBdPruebas.class)
abstract class FlujoBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicLong CODIGOS = new AtomicLong(7_000_000);

    // Datos compartidos por todas las pruebas, creados una sola vez por corrida.
    private static String tokenAdmin;
    private static Cuenta bodegueroCompartido;
    private static Long categoriaComun;
    private static Long unidadComun;

    @Autowired
    private MockMvc mvc;

    @Value("${app.seed.admin-email}")
    protected String correoAdmin;

    @Value("${app.seed.admin-password}")
    protected String claveAdmin;

    // ------------------------------------------------------------------ modelos de apoyo

    /** Respuesta de la API: código HTTP y cuerpo JSON (vacío si no hubo cuerpo). */
    protected record Respuesta(int estado, JsonNode json) {

        public String mensaje() {
            return json.path("message").asText();
        }

        public String campo() {
            return json.path("field").asText();
        }

        /** Falla mostrando el cuerpo recibido si el código HTTP no es el esperado. */
        public Respuesta esperando(int esperado) {
            assertThat(estado).as("código HTTP (cuerpo recibido: %s)", json).isEqualTo(esperado);
            return this;
        }
    }

    /** Usuario creado por la prueba. {@code token} es nulo en los empleados: ellos no inician sesión. */
    protected record Cuenta(long id, String nombre, String email, String clave, String token) {}

    protected record Prod(long id, String nombre, String codigo) {}

    protected record Stock(int cantidad, String estado, LocalDate proximoVencimiento) {}

    /** Categoría o unidad de medida creada por la prueba. */
    protected record Catalogo(long id, String nombre) {}

    /** Archivo descargado: código HTTP, bytes y las cabeceras que importan para una descarga. */
    protected record Descarga(int estado, byte[] bytes, String tipoContenido, String disposicion) {

        public Descarga esperando(int esperado) {
            assertThat(estado).as("código HTTP de la descarga").isEqualTo(esperado);
            return this;
        }
    }

    // ------------------------------------------------------------------ llamadas HTTP

    protected Respuesta llamar(HttpMethod metodo, String url, String token, Object cuerpo) {
        try {
            MockHttpServletRequestBuilder peticion =
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(metodo, url);
            if (token != null) peticion.header("Authorization", "Bearer " + token);
            if (cuerpo != null) {
                peticion.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(cuerpo));
            }
            var resultado = mvc.perform(peticion).andReturn().getResponse();
            String texto = resultado.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            JsonNode json = texto.isBlank() ? MissingNode.getInstance() : JSON.readTree(texto);
            return new Respuesta(resultado.getStatus(), json);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo llamar a " + metodo + " " + url, e);
        }
    }

    /** Descarga un archivo (GET) sin intentar leerlo como JSON. */
    protected Descarga descargar(String url, String token) {
        try {
            MockHttpServletRequestBuilder peticion = MockMvcRequestBuilders.get(url);
            if (token != null) peticion.header("Authorization", "Bearer " + token);
            var respuesta = mvc.perform(peticion).andReturn().getResponse();
            return new Descarga(respuesta.getStatus(), respuesta.getContentAsByteArray(),
                    respuesta.getContentType(), respuesta.getHeader("Content-Disposition"));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descargar " + url, e);
        }
    }

    /** Sube un archivo como multipart con el nombre de parte que espera el controlador. */
    protected Respuesta subirArchivo(String url, String token, String parte, String nombreArchivo, byte[] contenido) {
        try {
            var peticion = MockMvcRequestBuilders.multipart(url)
                    .file(new MockMultipartFile(parte, nombreArchivo, "application/octet-stream", contenido));
            if (token != null) peticion.header("Authorization", "Bearer " + token);
            var resultado = mvc.perform(peticion).andReturn().getResponse();
            String texto = resultado.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            JsonNode json = texto.isBlank() ? MissingNode.getInstance() : JSON.readTree(texto);
            return new Respuesta(resultado.getStatus(), json);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo subir el archivo a " + url, e);
        }
    }

    protected Respuesta get(String url, String token) {
        return llamar(HttpMethod.GET, url, token, null);
    }

    protected Respuesta post(String url, String token, Object cuerpo) {
        return llamar(HttpMethod.POST, url, token, cuerpo);
    }

    protected Respuesta put(String url, String token, Object cuerpo) {
        return llamar(HttpMethod.PUT, url, token, cuerpo);
    }

    protected Respuesta delete(String url, String token) {
        return llamar(HttpMethod.DELETE, url, token, null);
    }

    /** Arma un objeto JSON a partir de pares clave, valor. */
    protected static Map<String, Object> objeto(Object... paresClaveValor) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < paresClaveValor.length; i += 2) {
            mapa.put((String) paresClaveValor[i], paresClaveValor[i + 1]);
        }
        return mapa;
    }

    protected static String unico() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected static LocalDate hoy() {
        return LocalDate.now();
    }

    // ------------------------------------------------------------------ sesión y usuarios

    protected Respuesta iniciarSesion(String correo, String clave) {
        return post("/api/auth/login", null, objeto("email", correo, "password", clave));
    }

    protected synchronized String tokenAdmin() {
        if (tokenAdmin == null) {
            var respuesta = iniciarSesion(correoAdmin, claveAdmin).esperando(200);
            tokenAdmin = respuesta.json().path("token").asText();
        }
        return tokenAdmin;
    }

    /** El administrador crea un usuario del rol indicado ({@code BODEGUERO} o {@code EMPLEADO}). */
    protected Cuenta crearUsuario(String rol) {
        String sufijo = unico();
        String nombre = ("EMPLEADO".equals(rol) ? "Empleado " : "Bodeguero ") + sufijo;
        String correo = rol.toLowerCase() + "-" + sufijo + "@pruebas.test";
        String clave = "clave-" + sufijo;
        var respuesta = post("/api/usuarios", tokenAdmin(),
                objeto("nombre", nombre, "email", correo, "password", clave, "rol", rol)).esperando(201);
        return new Cuenta(respuesta.json().path("id").asLong(), nombre, correo, clave, null);
    }

    /** Crea un bodeguero y deja su token listo (login real). */
    protected Cuenta crearBodeguero() {
        Cuenta creada = crearUsuario("BODEGUERO");
        String token = iniciarSesion(creada.email(), creada.clave()).esperando(200).json().path("token").asText();
        return new Cuenta(creada.id(), creada.nombre(), creada.email(), creada.clave(), token);
    }

    /** Los empleados no tienen token: se identifican con id y contraseña en la vista pública. */
    protected Cuenta crearEmpleado() {
        return crearUsuario("EMPLEADO");
    }

    protected synchronized Cuenta bodeguero() {
        if (bodegueroCompartido == null) bodegueroCompartido = crearBodeguero();
        return bodegueroCompartido;
    }

    // ------------------------------------------------------------------ catálogo y productos

    private synchronized long categoriaComun() {
        if (categoriaComun == null) {
            var r = post("/api/categorias", bodeguero().token(), objeto("nombre", "Categoría " + unico())).esperando(201);
            categoriaComun = r.json().path("id").asLong();
        }
        return categoriaComun;
    }

    private synchronized long unidadComun() {
        if (unidadComun == null) {
            var r = post("/api/unidades-medida", bodeguero().token(), objeto("nombre", "Unidad " + unico())).esperando(201);
            unidadComun = r.json().path("id").asLong();
        }
        return unidadComun;
    }

    protected Prod crearProducto(boolean perecible) {
        return crearProducto(perecible, 5, 2);
    }

    /** Crea un producto único (nombre y código numérico propios) con los umbrales indicados. */
    protected Prod crearProducto(boolean perecible, int stockMinimo, int stockCritico) {
        return crearProductoEn(categoriaComun(), perecible, stockMinimo, stockCritico);
    }

    /** Producto único dentro de la categoría indicada (no requiere PTV), con umbrales 5 y 2. */
    protected Prod crearProductoEn(long categoriaId, boolean perecible) {
        return crearProductoEn(categoriaId, perecible, 5, 2);
    }

    protected Prod crearProductoEn(long categoriaId, boolean perecible, int stockMinimo, int stockCritico) {
        String nombre = "Producto " + unico();
        String codigo = siguienteCodigo();
        var r = post("/api/productos", bodeguero().token(), objeto(
                "codigo", codigo, "nombre", nombre,
                "categoriaId", categoriaId, "unidadMedidaId", unidadComun(),
                "esPerecible", perecible, "stockMinimo", stockMinimo, "stockCritico", stockCritico,
                "costoUnitario", 1000)).esperando(201);
        return new Prod(r.json().path("id").asLong(), nombre, codigo);
    }

    /** Código numérico que ningún otro producto de la corrida usa. */
    protected static String siguienteCodigo() {
        return Long.toString(CODIGOS.incrementAndGet());
    }

    /** Categoría propia de la prueba (nombre único); con {@code requierePtv} sus productos usan código PTV. */
    protected Catalogo crearCategoriaPropia(boolean requierePtv) {
        String nombre = "Cat " + unico();
        var r = post("/api/categorias", bodeguero().token(), objeto("nombre", nombre)).esperando(201);
        long id = r.json().path("id").asLong();
        if (requierePtv) {
            put("/api/categorias/" + id + "/requiere-ptv", bodeguero().token(), objeto("requierePtv", true))
                    .esperando(200);
        }
        return new Catalogo(id, nombre);
    }

    /** Unidad de medida propia de la prueba (nombre único). */
    protected Catalogo crearUnidadPropia() {
        String nombre = "Uni " + unico();
        var r = post("/api/unidades-medida", bodeguero().token(), objeto("nombre", nombre)).esperando(201);
        return new Catalogo(r.json().path("id").asLong(), nombre);
    }

    /** Producto no perecible con una entrada de la cantidad indicada. */
    protected Prod productoConStock(int cantidad) {
        Prod producto = crearProducto(false);
        entrada(producto.id(), cantidad, null, null).esperando(201);
        return producto;
    }

    // ------------------------------------------------------------------ entradas, salidas y stock

    protected static Map<String, Object> lineaEntrada(long productoId, int cantidad, LocalDate vencimiento, String lote) {
        return objeto("productoId", productoId, "cantidad", cantidad,
                "fechaVencimiento", vencimiento == null ? null : vencimiento.toString(), "numeroLote", lote);
    }

    protected Respuesta entrada(long productoId, int cantidad, LocalDate vencimiento, String lote) {
        return entradaConLineas(lineaEntrada(productoId, cantidad, vencimiento, lote));
    }

    @SafeVarargs
    protected final Respuesta entradaConLineas(Map<String, Object>... lineas) {
        return post("/api/movimientos/entradas", bodeguero().token(),
                objeto("tipo", "COMPRA", "lineas", List.of(lineas)));
    }

    protected Respuesta salida(long productoId, int cantidad) {
        return salida(bodeguero(), productoId, cantidad);
    }

    protected Respuesta salida(Cuenta bodeguero, long productoId, int cantidad) {
        return post("/api/movimientos/salidas", bodeguero.token(), objeto("tipo", "CONSUMO_INTERNO",
                "lineas", List.of(objeto("productoId", productoId, "cantidad", cantidad))));
    }

    protected Stock stock(long productoId) {
        var r = get("/api/movimientos/productos/" + productoId + "/stock", bodeguero().token()).esperando(200);
        JsonNode proximo = r.json().path("fechaVencimientoProximo");
        return new Stock(r.json().path("stockActual").asInt(), r.json().path("estado").asText(),
                proximo.isNull() || proximo.isMissingNode() ? null : LocalDate.parse(proximo.asText()));
    }

    protected int stockDe(long productoId) {
        return stock(productoId).cantidad();
    }

    /** Líneas del libro de un producto (más recientes primero o en el orden que devuelva la API). */
    protected List<JsonNode> movimientos(long productoId) {
        var r = get("/api/movimientos?productoId=" + productoId, bodeguero().token()).esperando(200);
        List<JsonNode> lineas = new ArrayList<>();
        r.json().forEach(lineas::add);
        return lineas;
    }

    protected List<JsonNode> movimientosDeTipo(long productoId, String tipo) {
        return movimientos(productoId).stream().filter(m -> tipo.equals(m.path("tipo").asText())).toList();
    }

    // ------------------------------------------------------------------ pedidos del empleado

    protected static Map<String, Object> item(long productoId, int cantidad) {
        return objeto("productoId", productoId, "cantidad", cantidad);
    }

    @SafeVarargs
    protected final Respuesta pedir(Cuenta empleado, Map<String, Object>... items) {
        return pedirConClave(empleado, empleado.clave(), items);
    }

    @SafeVarargs
    protected final Respuesta pedirConClave(Cuenta empleado, String clave, Map<String, Object>... items) {
        return post("/api/publico/solicitudes", null,
                objeto("empleadoId", empleado.id(), "password", clave, "items", List.of(items)));
    }

    /** Pedido de un solo producto que debe quedar registrado; devuelve el id de la solicitud. */
    protected long pedirOk(Cuenta empleado, long productoId, int cantidad) {
        return pedir(empleado, item(productoId, cantidad)).esperando(201).json().path("id").asLong();
    }

    protected Respuesta misSolicitudes(Cuenta empleado) {
        return post("/api/publico/mis-solicitudes", null,
                objeto("empleadoId", empleado.id(), "password", empleado.clave()));
    }

    /** Solicitud del empleado según su vista «mis solicitudes». */
    protected JsonNode solicitudDelEmpleado(Cuenta empleado, long solicitudId) {
        for (JsonNode s : misSolicitudes(empleado).esperando(200).json()) {
            if (s.path("id").asLong() == solicitudId) return s;
        }
        throw new AssertionError("La solicitud " + solicitudId + " no aparece en las solicitudes del empleado");
    }

    /** Detalle de un producto dentro de una solicitud. */
    protected static JsonNode detalleDe(JsonNode solicitud, long productoId) {
        for (JsonNode d : solicitud.path("detalles")) {
            if (d.path("productoId").asLong() == productoId) return d;
        }
        throw new AssertionError("La solicitud no tiene detalle del producto " + productoId + ": " + solicitud);
    }

    @SafeVarargs
    protected final Respuesta aprobar(Cuenta bodeguero, long solicitudId, Map<String, Object>... entregas) {
        Object cuerpo = entregas.length == 0 ? null : objeto("items", List.of(entregas));
        return put("/api/solicitudes/" + solicitudId + "/aprobar", bodeguero.token(), cuerpo);
    }

    protected Respuesta rechazar(Cuenta bodeguero, long solicitudId) {
        return put("/api/solicitudes/" + solicitudId + "/rechazar", bodeguero.token(), null);
    }

    protected boolean estaPendiente(long solicitudId) {
        for (JsonNode s : get("/api/solicitudes/pendientes", bodeguero().token()).esperando(200).json()) {
            if (s.path("id").asLong() == solicitudId) return true;
        }
        return false;
    }

    @SafeVarargs
    protected final Respuesta devolver(Cuenta empleado, long pedidoId, Map<String, Object>... items) {
        return post("/api/publico/devoluciones", null, objeto("empleadoId", empleado.id(),
                "password", empleado.clave(), "solicitudOrigenId", pedidoId, "items", List.of(items)));
    }
}
