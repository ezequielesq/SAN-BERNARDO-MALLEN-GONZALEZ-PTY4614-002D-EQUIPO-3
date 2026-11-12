package com.smartrdp.backend.flujos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Flujo D (parte 1): alta, validación de códigos, edición y baja lógica de productos. */
class ProductosFlujoTest extends FlujoBase {

    @Test
    @DisplayName("HU-10 Crear producto con nombre y unidad de medida: queda registrado y se ve en el listado")
    void crearProductoYVerloEnElListado() {
        Catalogo categoria = crearCategoriaPropia(false);
        Catalogo unidad = crearUnidadPropia();
        String codigo = siguienteCodigo();
        String nombre = "Harina " + unico();

        var creado = post("/api/productos", bodeguero().token(), objeto("codigo", codigo, "nombre", "  " + nombre + " ",
                "categoriaId", categoria.id(), "unidadMedidaId", unidad.id(), "esPerecible", true,
                "stockMinimo", 20, "stockCritico", 8, "costoUnitario", 1250)).esperando(201).json();

        long id = creado.path("id").asLong();
        assertThat(id).isPositive();
        assertThat(creado.path("nombre").asText()).isEqualTo(nombre);
        assertThat(creado.path("activo").asBoolean()).isTrue();

        // Está en el listado con todos sus datos y se puede abrir por id.
        var delListado = porId(get("/api/productos", bodeguero().token()).esperando(200).json(), id);
        for (JsonNode p : new JsonNode[] {delListado, get("/api/productos/" + id, bodeguero().token()).esperando(200).json()}) {
            assertThat(p.path("codigo").asText()).isEqualTo(codigo);
            assertThat(p.path("codigoPtv").isNull()).isTrue();
            assertThat(p.path("nombre").asText()).isEqualTo(nombre);
            assertThat(p.path("categoriaId").asLong()).isEqualTo(categoria.id());
            assertThat(p.path("categoriaNombre").asText()).isEqualTo(categoria.nombre());
            assertThat(p.path("unidadMedidaId").asLong()).isEqualTo(unidad.id());
            assertThat(p.path("unidadMedidaNombre").asText()).isEqualTo(unidad.nombre());
            assertThat(p.path("esPerecible").asBoolean()).isTrue();
            assertThat(p.path("stockMinimo").asInt()).isEqualTo(20);
            assertThat(p.path("stockCritico").asInt()).isEqualTo(8);
            assertThat(p.path("costoUnitario").asInt()).isEqualTo(1250);
            assertThat(p.path("activo").asBoolean()).isTrue();
        }

        // Un producto recién creado no tiene stock: figura agotado en el inventario.
        var stock = stock(id);
        assertThat(stock.cantidad()).isZero();
        assertThat(stock.estado()).isEqualTo("AGOTADO");

        // Datos inválidos: no se crea nada.
        long antes = get("/api/productos?soloActivos=false", bodeguero().token()).esperando(200).json().size();
        String token = bodeguero().token();
        assertThat(post("/api/productos", token, objeto("codigo", siguienteCodigo(), "nombre", "  ",
                "categoriaId", categoria.id(), "unidadMedidaId", unidad.id(), "esPerecible", false,
                "stockMinimo", 5, "stockCritico", 2, "costoUnitario", 10)).estado()).isEqualTo(400);
        assertThat(post("/api/productos", token, objeto("codigo", siguienteCodigo(), "nombre", "Sin unidad " + unico(),
                "categoriaId", categoria.id(), "esPerecible", false,
                "stockMinimo", 5, "stockCritico", 2, "costoUnitario", 10)).estado()).isEqualTo(400);
        assertThat(post("/api/productos", token, objeto("codigo", siguienteCodigo(), "nombre", "Costo negativo",
                "categoriaId", categoria.id(), "unidadMedidaId", unidad.id(), "esPerecible", false,
                "stockMinimo", 5, "stockCritico", 2, "costoUnitario", -1)).estado()).isEqualTo(400);
        var umbrales = post("/api/productos", token, objeto("codigo", siguienteCodigo(), "nombre", "Umbrales " + unico(),
                "categoriaId", categoria.id(), "unidadMedidaId", unidad.id(), "esPerecible", false,
                "stockMinimo", 5, "stockCritico", 9, "costoUnitario", 10));
        assertThat(umbrales.estado()).isEqualTo(422);
        assertThat(umbrales.campo()).isEqualTo("stockCritico");
        assertThat(post("/api/productos", token, objeto("codigo", siguienteCodigo(), "nombre", "Sin categoría",
                "categoriaId", 999_999_999L, "unidadMedidaId", unidad.id(), "esPerecible", false,
                "stockMinimo", 5, "stockCritico", 2, "costoUnitario", 10)).estado()).isEqualTo(404);
        assertThat(get("/api/productos?soloActivos=false", token).esperando(200).json().size()).isEqualTo(antes);

        // Solo el Bodeguero crea productos; el administrador únicamente los consulta.
        assertThat(post("/api/productos", tokenAdmin(), objeto("codigo", siguienteCodigo(), "nombre", "Del admin",
                "categoriaId", categoria.id(), "unidadMedidaId", unidad.id(), "esPerecible", false,
                "stockMinimo", 5, "stockCritico", 2, "costoUnitario", 10)).estado()).isEqualTo(403);
        get("/api/productos", tokenAdmin()).esperando(200);
    }

    @Test
    @DisplayName("HU-150 Código PTV con formato inválido o duplicado rechazado; una categoría con PTV lo exige")
    void codigoPtvInvalidoYDuplicadoSeRechazan() {
        Catalogo conPtv = crearCategoriaPropia(true);
        Catalogo sinPtv = crearCategoriaPropia(false);
        Catalogo unidad = crearUnidadPropia();
        String base = "PTV-" + unico().toUpperCase(Locale.ROOT);

        // Categoría con PTV: sin código PTV se rechaza.
        var sinCodigo = crearConCodigos(conPtv, unidad, null, null);
        assertThat(sinCodigo.estado()).isEqualTo(422);
        assertThat(sinCodigo.campo()).isEqualTo("codigoPtv");

        // Formato inválido: espacios, símbolos o guion al inicio.
        for (String invalido : new String[] {"PTV 12", "PTV#12", "-PTV12", "PTV_12", "PTV/12"}) {
            var r = crearConCodigos(conPtv, unidad, invalido, null);
            assertThat(r.estado()).as("PTV '%s'", invalido).isEqualTo(422);
            assertThat(r.campo()).as("campo de '%s'", invalido).isEqualTo("codigoPtv");
            assertThat(r.mensaje()).contains("letras, números y guiones");
        }

        // Un código numérico no sirve en una categoría con PTV.
        var numerico = crearConCodigos(conPtv, unidad, null, siguienteCodigo());
        assertThat(numerico.estado()).isEqualTo(422);

        // Código válido: se guarda en mayúsculas.
        var creado = crearConCodigos(conPtv, unidad, base.toLowerCase(Locale.ROOT), null).esperando(201);
        assertThat(creado.json().path("codigoPtv").asText()).isEqualTo(base);
        assertThat(creado.json().path("codigo").isNull()).isTrue();

        // Duplicado, igual o con otras mayúsculas: se rechaza e informa que el producto ya está ingresado.
        for (String repetido : new String[] {base, base.toLowerCase(Locale.ROOT)}) {
            var r = crearConCodigos(conPtv, unidad, repetido, null);
            assertThat(r.estado()).as("PTV repetido '%s'", repetido).isEqualTo(422);
            assertThat(r.campo()).isEqualTo("codigoPtv");
            assertThat(r.mensaje()).contains("Ya existe un producto con el código " + base);
        }
        assertThat(productosConPtv(base)).isEqualTo(1);

        // Categoría sin PTV: usa código numérico; el PTV no va, el texto no vale y el repetido se rechaza.
        String numeroValido = siguienteCodigo();
        crearConCodigos(sinPtv, unidad, null, numeroValido).esperando(201);
        var numeroRepetido = crearConCodigos(sinPtv, unidad, null, numeroValido);
        assertThat(numeroRepetido.estado()).isEqualTo(422);
        assertThat(numeroRepetido.campo()).isEqualTo("codigo");
        assertThat(numeroRepetido.mensaje()).contains("Ya existe un producto con el código " + numeroValido);
        var texto = crearConCodigos(sinPtv, unidad, null, "ABC123");
        assertThat(texto.estado()).isEqualTo(422);
        assertThat(texto.campo()).isEqualTo("codigo");
        var faltante = crearConCodigos(sinPtv, unidad, null, null);
        assertThat(faltante.estado()).isEqualTo(422);
        assertThat(faltante.campo()).isEqualTo("codigo");
        var ptvEnNumerica = crearConCodigos(sinPtv, unidad, "PTV-" + unico(), siguienteCodigo());
        assertThat(ptvEnNumerica.estado()).isEqualTo(422);
        assertThat(ptvEnNumerica.campo()).isEqualTo("codigoPtv");
    }

    @Test
    @DisplayName("HU-10 Editar producto: cambia sus datos, conserva su stock y no puede tomar el código de otro")
    void editarProducto() {
        Catalogo categoria = crearCategoriaPropia(false);
        Catalogo otraCategoria = crearCategoriaPropia(false);
        Catalogo otraUnidad = crearUnidadPropia();
        var producto = crearProductoEn(categoria.id(), false);
        var otro = crearProducto(false);
        entrada(producto.id(), 6, null, "L-ED").esperando(201);
        String nombreNuevo = "Editado " + unico();
        String codigoNuevo = siguienteCodigo();

        var editado = put("/api/productos/" + producto.id(), bodeguero().token(), objeto("codigo", codigoNuevo,
                "nombre", nombreNuevo, "categoriaId", otraCategoria.id(), "unidadMedidaId", otraUnidad.id(),
                "esPerecible", true, "stockMinimo", 30, "stockCritico", 10, "costoUnitario", 777))
                .esperando(200).json();

        assertThat(editado.path("nombre").asText()).isEqualTo(nombreNuevo);
        var leido = get("/api/productos/" + producto.id(), bodeguero().token()).esperando(200).json();
        assertThat(leido.path("codigo").asText()).isEqualTo(codigoNuevo);
        assertThat(leido.path("nombre").asText()).isEqualTo(nombreNuevo);
        assertThat(leido.path("categoriaNombre").asText()).isEqualTo(otraCategoria.nombre());
        assertThat(leido.path("unidadMedidaNombre").asText()).isEqualTo(otraUnidad.nombre());
        assertThat(leido.path("esPerecible").asBoolean()).isTrue();
        assertThat(leido.path("stockMinimo").asInt()).isEqualTo(30);
        assertThat(leido.path("stockCritico").asInt()).isEqualTo(10);
        assertThat(leido.path("costoUnitario").asInt()).isEqualTo(777);
        assertThat(porId(get("/api/productos", bodeguero().token()).esperando(200).json(), producto.id())
                .path("nombre").asText()).isEqualTo(nombreNuevo);

        // El stock y el historial no cambian al editar; los umbrales nuevos sí se aplican al estado.
        var stock = stock(producto.id());
        assertThat(stock.cantidad()).isEqualTo(6);
        assertThat(stock.estado()).isEqualTo("CRITICO");
        assertThat(movimientosDeTipo(producto.id(), "COMPRA")).hasSize(1);

        // Guardar de nuevo con su propio código no cuenta como duplicado.
        put("/api/productos/" + producto.id(), bodeguero().token(), objeto("codigo", codigoNuevo,
                "nombre", nombreNuevo, "categoriaId", otraCategoria.id(), "unidadMedidaId", otraUnidad.id(),
                "esPerecible", true, "stockMinimo", 30, "stockCritico", 10, "costoUnitario", 777)).esperando(200);

        // Tomar el código de otro producto se rechaza y no cambia nada.
        var choque = put("/api/productos/" + producto.id(), bodeguero().token(), objeto("codigo", otro.codigo(),
                "nombre", "No debe guardarse", "categoriaId", otraCategoria.id(), "unidadMedidaId", otraUnidad.id(),
                "esPerecible", true, "stockMinimo", 30, "stockCritico", 10, "costoUnitario", 777));
        assertThat(choque.estado()).isEqualTo(422);
        assertThat(choque.campo()).isEqualTo("codigo");
        assertThat(get("/api/productos/" + producto.id(), bodeguero().token()).esperando(200).json()
                .path("nombre").asText()).isEqualTo(nombreNuevo);

        // Un producto que no existe responde 404.
        assertThat(put("/api/productos/999999999", bodeguero().token(), objeto("codigo", siguienteCodigo(),
                "nombre", "Fantasma", "categoriaId", otraCategoria.id(), "unidadMedidaId", otraUnidad.id(),
                "esPerecible", false, "stockMinimo", 5, "stockCritico", 2, "costoUnitario", 1)).estado())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("HU-190 Deshabilitar producto: sale de la vista pública y del listado activo, conserva su historial; reactivar lo devuelve")
    void deshabilitarConservaElHistorialYReactivarLoDevuelve() {
        Cuenta empleado = crearEmpleado();
        var producto = productoConStock(10);
        pedirOk(empleado, producto.id(), 2);
        assertThat(salida(producto.id(), 1).estado()).isEqualTo(201);
        assertThat(publicoIncluye(producto.id())).isTrue();

        delete("/api/productos/" + producto.id(), bodeguero().token()).esperando(204);

        // Desaparece de la vista pública y del listado activo; sigue en el listado completo, inactivo.
        assertThat(publicoIncluye(producto.id())).isFalse();
        assertThat(porId(get("/api/productos", bodeguero().token()).esperando(200).json(), producto.id())
                .isMissingNode()).isTrue();
        var completo = porId(get("/api/productos?soloActivos=false", bodeguero().token()).esperando(200).json(),
                producto.id());
        assertThat(completo.path("activo").asBoolean()).isFalse();
        assertThat(completo.path("nombre").asText()).isEqualTo(producto.nombre());

        // El historial se conserva intacto: movimientos y stock.
        assertThat(movimientosDeTipo(producto.id(), "COMPRA")).hasSize(1);
        assertThat(movimientosDeTipo(producto.id(), "CONSUMO_INTERNO")).hasSize(1);
        assertThat(stockDe(producto.id())).isEqualTo(9);

        // No admite movimientos ni pedidos nuevos, y su código sigue reservado.
        var entradaNueva = entrada(producto.id(), 5, null, "L-NUEVO");
        assertThat(entradaNueva.estado()).isEqualTo(422);
        assertThat(entradaNueva.mensaje()).contains("deshabilitado");
        var pedidoNuevo = pedir(empleado, item(producto.id(), 1));
        assertThat(pedidoNuevo.estado()).isEqualTo(422);
        assertThat(pedidoNuevo.mensaje()).contains("deshabilitado");
        Catalogo categoria = crearCategoriaPropia(false);
        Catalogo unidad = crearUnidadPropia();
        var mismoCodigo = crearConCodigos(categoria, unidad, null, producto.codigo());
        assertThat(mismoCodigo.estado()).isEqualTo(422);
        assertThat(stockDe(producto.id())).isEqualTo(9);

        put("/api/productos/" + producto.id() + "/reactivar", bodeguero().token(), null).esperando(204);

        assertThat(publicoIncluye(producto.id())).isTrue();
        assertThat(porId(get("/api/productos", bodeguero().token()).esperando(200).json(), producto.id())
                .path("activo").asBoolean()).isTrue();
        assertThat(movimientos(producto.id())).hasSize(2);
        pedirOk(empleado, producto.id(), 1);
        entrada(producto.id(), 5, null, "L-VUELVE").esperando(201);
        assertThat(stockDe(producto.id())).isEqualTo(14);
    }

    // ------------------------------------------------------------------ ayudantes

    private Respuesta crearConCodigos(Catalogo categoria, Catalogo unidad, String ptv, String codigo) {
        Map<String, Object> cuerpo = objeto("codigoPtv", ptv, "codigo", codigo, "nombre", "Prod " + unico(),
                "categoriaId", categoria.id(), "unidadMedidaId", unidad.id(), "esPerecible", false,
                "stockMinimo", 5, "stockCritico", 2, "costoUnitario", 100);
        return post("/api/productos", bodeguero().token(), cuerpo);
    }

    private long productosConPtv(String ptv) {
        long total = 0;
        for (JsonNode p : get("/api/productos?soloActivos=false", bodeguero().token()).esperando(200).json()) {
            if (ptv.equals(p.path("codigoPtv").asText())) total++;
        }
        return total;
    }

    private boolean publicoIncluye(long productoId) {
        return !porId(get("/api/publico/productos", null).esperando(200).json(), productoId).isMissingNode();
    }

    private static JsonNode porId(JsonNode lista, long id) {
        for (JsonNode e : lista) {
            if (e.path("id").asLong() == id) return e;
        }
        return MissingNode.getInstance();
    }
}
