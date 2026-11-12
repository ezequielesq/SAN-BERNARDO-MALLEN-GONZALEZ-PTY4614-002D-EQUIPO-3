package com.smartrdp.backend.flujos;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.smartrdp.backend.flujos.ExcelPruebas.fila;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flujos D-15, D-16 y E-22: cargas masivas desde Excel (productos y entradas de stock), plantillas y
 * exportaciones. Los archivos se arman en memoria a partir de la plantilla real que entrega la API.
 */
class ImportacionExcelFlujoTest extends FlujoBase {

    private static final String XLSX = "spreadsheetml.sheet";
    private static final DateTimeFormatter DD_MM_AAAA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final String PRODUCTOS_LEER = "/api/productos/importar/leer";
    private static final String ENTRADAS_LEER = "/api/movimientos/entradas/importar/leer";

    // ================================================================== plantilla y exportación de productos

    @Test
    @DisplayName("HU-200 La plantilla de productos se descarga con sus columnas y las categorías y unidades disponibles")
    void plantillaDeProductosSeDescargaConSusColumnas() {
        Catalogo categoria = crearCategoriaPropia(true);
        Catalogo unidad = crearUnidadPropia();

        var plantilla = descargar("/api/productos/plantilla/xlsx", bodeguero().token()).esperando(200);

        assertThat(plantilla.tipoContenido()).contains(XLSX);
        assertThat(plantilla.disposicion()).contains("attachment").contains("plantilla-productos.xlsx");
        assertThat(ExcelPruebas.hojas(plantilla.bytes())).containsExactly("Productos", "Instrucciones");
        assertThat(ExcelPruebas.leerHoja(plantilla.bytes(), "Productos").get(0)).containsExactly(
                "Código PTV", "Código", "Nombre", "Categoría", "Unidad", "Perecible", "Stock mínimo",
                "Stock crítico", "Costo unitario");

        // La hoja de instrucciones lista las categorías activas (con el tipo de código) y las unidades.
        var instrucciones = ExcelPruebas.leerHoja(plantilla.bytes(), "Instrucciones");
        assertThat(instrucciones).anyMatch(f -> f.size() >= 2
                && categoria.nombre().equals(f.get(0)) && "Código PTV".equals(f.get(1)));
        assertThat(instrucciones).anyMatch(f -> !f.isEmpty() && unidad.nombre().equals(f.get(0)));

        // Con una categoría desactivada, la plantilla ya no la ofrece.
        delete("/api/categorias/" + categoria.id(), bodeguero().token()).esperando(204);
        var sinCategoria = descargar("/api/productos/plantilla/xlsx", bodeguero().token()).esperando(200);
        assertThat(ExcelPruebas.leerHoja(sinCategoria.bytes(), "Instrucciones"))
                .noneMatch(f -> !f.isEmpty() && categoria.nombre().equals(f.get(0)));

        // Sin sesión no se descarga.
        assertThat(descargar("/api/productos/plantilla/xlsx", null).estado()).isEqualTo(401);
    }

    @Test
    @DisplayName("HU-120 Exportar productos a Excel y PDF: el archivo trae los productos activos y, si se pide, los deshabilitados")
    void exportarProductosAExcelYPdf() {
        Catalogo categoria = crearCategoriaPropia(false);
        Catalogo categoriaPtv = crearCategoriaPropia(true);
        Catalogo unidad = crearUnidadPropia();
        String sufijo = unico();
        String codigo = siguienteCodigo();
        String ptv = "EXP-" + sufijo.toUpperCase(Locale.ROOT);
        long activoId = crearProductoCon(categoria, unidad, null, codigo, "Exportable " + sufijo, true, 40, 15, 2500);
        crearProductoCon(categoriaPtv, unidad, ptv, null, "Con PTV " + sufijo, false, 9, 3, 100);
        long deshabilitadoId = crearProductoCon(categoria, unidad, null, siguienteCodigo(), "Baja " + sufijo,
                false, 5, 2, 1);
        delete("/api/productos/" + deshabilitadoId, bodeguero().token()).esperando(204);

        // Excel con los activos.
        var xlsx = descargar("/api/productos/exportar/xlsx", bodeguero().token()).esperando(200);
        assertThat(xlsx.tipoContenido()).contains(XLSX);
        assertThat(xlsx.disposicion()).contains("attachment").contains("productos.xlsx");
        var hoja = ExcelPruebas.leerHoja(xlsx.bytes(), "Productos");
        assertThat(hoja.get(0)).containsExactly("Código PTV", "Código", "Nombre", "Categoría", "Unidad",
                "Perecible", "Stock mínimo", "Stock crítico", "Costo unitario", "Activo");
        List<String> filaActivo = filaConNombre(hoja, "Exportable " + sufijo);
        assertThat(filaActivo).containsExactly("", codigo, "Exportable " + sufijo, categoria.nombre(),
                unidad.nombre(), "Sí", "40", "15", "2500", "Sí");
        List<String> filaPtv = filaConNombre(hoja, "Con PTV " + sufijo);
        assertThat(filaPtv.get(0)).isEqualTo(ptv);
        assertThat(filaPtv.get(1)).isEmpty();
        assertThat(filaPtv.get(5)).isEqualTo("No");
        assertThat(hoja).noneMatch(f -> f.size() > 2 && ("Baja " + sufijo).equals(f.get(2)));

        // Excel con todos: el deshabilitado figura como inactivo.
        var completo = ExcelPruebas.leerHoja(
                descargar("/api/productos/exportar/xlsx?soloActivos=false", bodeguero().token()).esperando(200).bytes(),
                "Productos");
        assertThat(filaConNombre(completo, "Baja " + sufijo).get(9)).isEqualTo("No");

        // PDF real: cabecera %PDF, tipo y nombre de archivo, y los productos dentro del texto.
        var pdf = descargar("/api/productos/exportar/pdf", bodeguero().token()).esperando(200);
        assertThat(pdf.tipoContenido()).contains("application/pdf");
        assertThat(pdf.disposicion()).contains("attachment").contains("productos.pdf");
        assertThat(new String(pdf.bytes(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        // El PDF parte las celdas largas en varias líneas: se compara sin espacios.
        String texto = textoDelPdf(pdf.bytes()).replace(" ", "");
        assertThat(texto).contains(("Exportable " + sufijo).replace(" ", ""))
                .contains(("Con PTV " + sufijo).replace(" ", "")).contains(ptv);
        assertThat(texto).doesNotContain(("Baja " + sufijo).replace(" ", ""));

        // El administrador también puede exportar; sin sesión no.
        descargar("/api/productos/exportar/xlsx", tokenAdmin()).esperando(200);
        assertThat(descargar("/api/productos/exportar/pdf", null).estado()).isEqualTo(401);
    }

    // ================================================================== importar productos (flujo D-15)

    @Test
    @DisplayName("HU-200 Importar productos desde Excel: se leen y validan todas las filas, se informan las inválidas y se guardan las válidas")
    void importarProductosInformaLasInvalidasYGuardaLasValidas() {
        Catalogo numerica = crearCategoriaPropia(false);
        Catalogo conPtv = crearCategoriaPropia(true);
        Catalogo unidad = crearUnidadPropia();
        String sufijo = unico();
        var yaExistente = crearProducto(false);
        String codigoA = siguienteCodigo();
        String ptvB = "imp-" + sufijo;
        long codigoC = Long.parseLong(siguienteCodigo());
        String codigoRepetido = siguienteCodigo();

        List<List<Object>> filas = new ArrayList<>();
        // Válidas.
        filas.add(fila(null, codigoA, "Imp A " + sufijo, numerica.nombre(), unidad.nombre(), "Sí", 10, 4, 1500)); // 2
        filas.add(fila(ptvB, null, "Imp B " + sufijo, conPtv.nombre().toUpperCase(Locale.ROOT), unidad.nombre(),
                "No", 5, 2, 300)); // 3
        filas.add(fila(null, codigoC, "Imp C " + sufijo, numerica.nombre(), unidad.nombre(), null, 8, 8, 0)); // 4
        filas.add(fila()); // 5: fila vacía, se ignora
        // Inválidas.
        filas.add(fila(null, yaExistente.codigo(), "Imp duplicado BD " + sufijo, numerica.nombre(), unidad.nombre(),
                "No", 5, 2, 10)); // 6
        filas.add(fila(null, siguienteCodigo(), "Imp número inválido " + sufijo, numerica.nombre(), unidad.nombre(),
                "No", "doce", 2, 10)); // 7
        filas.add(fila(null, siguienteCodigo(), "Imp decimal " + sufijo, numerica.nombre(), unidad.nombre(),
                "No", 10, 2.5, 10)); // 8
        filas.add(fila(null, siguienteCodigo(), "Imp categoría " + sufijo, "Categoría que no existe", unidad.nombre(),
                "No", 5, 2, 10)); // 9
        filas.add(fila("PTV 99", null, "Imp PTV malo " + sufijo, conPtv.nombre(), unidad.nombre(),
                "No", 5, 2, 10)); // 10
        filas.add(fila(null, siguienteCodigo(), "Imp umbrales " + sufijo, numerica.nombre(), unidad.nombre(),
                "No", 3, 9, 10)); // 11
        filas.add(fila(null, siguienteCodigo(), "Imp perecible " + sufijo, numerica.nombre(), unidad.nombre(),
                "tal vez", 5, 2, 10)); // 12
        filas.add(fila(null, siguienteCodigo(), null, numerica.nombre(), unidad.nombre(), "No", 5, 2, 10)); // 13
        filas.add(fila(null, codigoRepetido, "Imp repetido 1 " + sufijo, numerica.nombre(), unidad.nombre(),
                "No", 5, 2, 10)); // 14
        filas.add(fila(null, codigoRepetido, "Imp repetido 2 " + sufijo, numerica.nombre(), unidad.nombre(),
                "No", 5, 2, 10)); // 15
        byte[] archivo = productosXlsx(filas);

        // Leer: devuelve todas las filas validadas y no guarda nada.
        var leidas = subirArchivo(PRODUCTOS_LEER, bodeguero().token(), "archivo", "productos.xlsx", archivo)
                .esperando(200).json();
        assertThat(leidas).hasSize(13);
        assertThat(productosConSufijo(sufijo)).isZero();
        Map<Integer, JsonNode> porFila = porFila(leidas);
        assertThat(porFila.keySet()).doesNotContain(5);

        assertThat(errores(porFila.get(2))).isEmpty();
        assertThat(errores(porFila.get(3))).isEmpty();
        assertThat(errores(porFila.get(4))).isEmpty();
        assertThat(porFila.get(4).path("datos").path("codigo").asText()).isEqualTo(Long.toString(codigoC));
        assertThat(errores(porFila.get(6))).containsOnlyKeys("codigo");
        assertThat(errores(porFila.get(6)).get("codigo")).contains("Ya existe un producto con el código");
        assertThat(errores(porFila.get(7))).containsKey("stockMinimo");
        assertThat(errores(porFila.get(7)).get("stockMinimo")).contains("número entero");
        assertThat(errores(porFila.get(8))).containsKey("stockCritico");
        assertThat(errores(porFila.get(9))).containsKey("categoriaId");
        assertThat(errores(porFila.get(9)).get("categoriaId")).contains("Categoría que no existe");
        assertThat(errores(porFila.get(10))).containsKey("codigoPtv");
        assertThat(errores(porFila.get(11))).containsKey("stockCritico");
        assertThat(errores(porFila.get(12))).containsKey("esPerecible");
        assertThat(errores(porFila.get(13))).containsKey("nombre");
        // Un código repetido dentro del archivo se marca en todas las filas que lo usan.
        assertThat(errores(porFila.get(14)).get("codigo")).contains("Código repetido en el archivo").contains("15");
        assertThat(errores(porFila.get(15)).get("codigo")).contains("Código repetido en el archivo").contains("14");

        // Se guardan solo las válidas: las inválidas se informaron y no se pierde ninguna válida.
        List<JsonNode> validas = new ArrayList<>();
        for (JsonNode f : leidas) {
            if (f.path("errores").isEmpty()) validas.add(f.path("datos"));
        }
        assertThat(validas).hasSize(3);
        var creados = post("/api/productos/importar", bodeguero().token(), objeto("filas", validas))
                .esperando(201).json();
        assertThat(creados).hasSize(3);

        assertThat(productosConSufijo(sufijo)).isEqualTo(3);
        JsonNode a = productoPorNombre("Imp A " + sufijo);
        assertThat(a.path("codigo").asText()).isEqualTo(codigoA);
        assertThat(a.path("categoriaNombre").asText()).isEqualTo(numerica.nombre());
        assertThat(a.path("unidadMedidaNombre").asText()).isEqualTo(unidad.nombre());
        assertThat(a.path("esPerecible").asBoolean()).isTrue();
        assertThat(a.path("stockMinimo").asInt()).isEqualTo(10);
        assertThat(a.path("stockCritico").asInt()).isEqualTo(4);
        assertThat(a.path("costoUnitario").asInt()).isEqualTo(1500);
        JsonNode b = productoPorNombre("Imp B " + sufijo);
        assertThat(b.path("codigoPtv").asText()).isEqualTo(ptvB.toUpperCase(Locale.ROOT));
        assertThat(b.path("categoriaNombre").asText()).isEqualTo(conPtv.nombre());
        assertThat(b.path("esPerecible").asBoolean()).isFalse();
        JsonNode c = productoPorNombre("Imp C " + sufijo);
        assertThat(c.path("codigo").asText()).isEqualTo(Long.toString(codigoC));
        assertThat(c.path("esPerecible").asBoolean()).isFalse();
        assertThat(c.path("costoUnitario").asInt()).isZero();

        // Las inválidas no se crearon y el producto que ya existía sigue igual.
        assertThat(productoPorNombre("Imp duplicado BD " + sufijo).isMissingNode()).isTrue();
        assertThat(productoPorNombre("Imp número inválido " + sufijo).isMissingNode()).isTrue();
        assertThat(productoPorNombre("Imp repetido 1 " + sufijo).isMissingNode()).isTrue();
        assertThat(get("/api/productos/" + yaExistente.id(), bodeguero().token()).esperando(200).json()
                .path("nombre").asText()).isEqualTo(yaExistente.nombre());
    }

    @Test
    @DisplayName("HU-200 Importar es todo o nada: con una fila inválida no se guarda ninguna; corregida en la vista previa, se guardan todas")
    void importarEsTodoONadaYSePuedeCorregirEnLaVistaPrevia() {
        Catalogo categoria = crearCategoriaPropia(false);
        Catalogo unidad = crearUnidadPropia();
        String sufijo = unico();
        byte[] archivo = productosXlsx(List.of(
                fila(null, siguienteCodigo(), "Todo o nada 1 " + sufijo, categoria.nombre(), unidad.nombre(), "No", 5, 2, 10),
                fila(null, siguienteCodigo(), "Todo o nada 2 " + sufijo, categoria.nombre(), unidad.nombre(), "No", "x", 2, 10)));
        var leidas = subirArchivo(PRODUCTOS_LEER, bodeguero().token(), "archivo", "productos.xlsx", archivo)
                .esperando(200).json();
        List<JsonNode> todas = new ArrayList<>();
        leidas.forEach(f -> todas.add(f.path("datos")));

        // Mandar todas las filas, con la inválida, rechaza la importación completa y no guarda nada.
        var rechazada = post("/api/productos/importar", bodeguero().token(), objeto("filas", todas));
        assertThat(rechazada.estado()).isEqualTo(422);
        assertThat(rechazada.mensaje()).contains("1 fila con errores");
        assertThat(productosConSufijo(sufijo)).isZero();

        // La vista previa corrige la fila y la revalida (sin guardar).
        Map<String, Object> corregida = new HashMap<>();
        todas.get(1).propertyNames().forEach(campo -> corregida.put(campo, valorDe(todas.get(1).get(campo))));
        corregida.put("stockMinimo", 6);
        List<Object> filasCorregidas = List.of(todas.get(0), corregida);
        var revalidadas = post("/api/productos/importar/validar", bodeguero().token(), objeto("filas", filasCorregidas))
                .esperando(200).json();
        assertThat(revalidadas).hasSize(2);
        assertThat(revalidadas.get(0).path("errores")).isEmpty();
        assertThat(revalidadas.get(1).path("errores")).isEmpty();
        assertThat(productosConSufijo(sufijo)).isZero();

        // Con todas las filas correctas, se crean todas.
        post("/api/productos/importar", bodeguero().token(), objeto("filas", filasCorregidas))
                .esperando(201);
        assertThat(productosConSufijo(sufijo)).isEqualTo(2);
        assertThat(productoPorNombre("Todo o nada 2 " + sufijo).path("stockMinimo").asInt()).isEqualTo(6);

        // La lista vacía no es una importación válida.
        assertThat(post("/api/productos/importar", bodeguero().token(), objeto("filas", List.of())).estado())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("HU-200 Archivos que no son una plantilla válida se rechazan con un mensaje y sin guardar nada")
    void archivosInvalidosSeRechazan() {
        String token = bodeguero().token();
        byte[] plantilla = descargar("/api/productos/plantilla/xlsx", token).esperando(200).bytes();

        // Extensión equivocada, archivo vacío y bytes que no son un Excel.
        var texto = subirArchivo(PRODUCTOS_LEER, token, "archivo", "productos.txt", "hola".getBytes());
        assertThat(texto.estado()).isEqualTo(422);
        assertThat(texto.mensaje()).contains(".xlsx");
        assertThat(subirArchivo(PRODUCTOS_LEER, token, "archivo", "productos.xlsx", new byte[0]).estado())
                .isEqualTo(422);
        var corrupto = subirArchivo(PRODUCTOS_LEER, token, "archivo", "productos.xlsx", "no soy un excel".getBytes());
        assertThat(corrupto.estado()).isEqualTo(422);
        assertThat(corrupto.mensaje()).contains("No se pudo leer el archivo");

        // La plantilla sin filas, un libro sin encabezados y uno al que le faltan columnas obligatorias.
        var sinFilas = subirArchivo(PRODUCTOS_LEER, token, "archivo", "productos.xlsx", plantilla);
        assertThat(sinFilas.estado()).isEqualTo(422);
        assertThat(sinFilas.mensaje()).contains("no tiene productos");
        var sinEncabezados = subirArchivo(PRODUCTOS_LEER, token, "archivo", "productos.xlsx",
                ExcelPruebas.libroConEncabezados("Productos", "Uno", "Dos"));
        assertThat(sinEncabezados.estado()).isEqualTo(422);
        assertThat(sinEncabezados.mensaje()).contains("No se encontraron los encabezados");
        var faltan = subirArchivo(PRODUCTOS_LEER, token, "archivo", "productos.xlsx",
                ExcelPruebas.libroConEncabezados("Productos", "Nombre", "Categoría"));
        assertThat(faltan.estado()).isEqualTo(422);
        assertThat(faltan.mensaje()).contains("Faltan columnas").contains("Unidad").contains("Costo unitario");

        // Sin sesión o con el administrador (que no gestiona productos) no se puede importar.
        byte[] archivo = productosXlsx(List.of(fila(null, siguienteCodigo(), "No importa", "x", "y", "No", 5, 2, 1)));
        assertThat(subirArchivo(PRODUCTOS_LEER, null, "archivo", "productos.xlsx", archivo).estado()).isEqualTo(401);
        assertThat(subirArchivo(PRODUCTOS_LEER, tokenAdmin(), "archivo", "productos.xlsx", archivo).estado())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("HU-200 Subir el Excel sin la parte «archivo» responde un error del cliente, no del servidor")
    void subirSinLaParteArchivoRespondeErrorDelCliente() {
        byte[] archivo = productosXlsx(List.of(fila(null, siguienteCodigo(), "No importa", "x", "y", "No", 5, 2, 1)));

        var respuesta = subirArchivo(PRODUCTOS_LEER, bodeguero().token(), "otro-nombre", "productos.xlsx", archivo);

        assertThat(respuesta.estado()).isBetween(400, 499);
    }

    // ================================================================== cargar entradas desde Excel (flujo E-22)

    @Test
    @DisplayName("HU-200 Plantilla de entradas: se descarga con sus columnas")
    void plantillaDeEntradasSeDescarga() {
        var plantilla = descargar("/api/movimientos/entradas/plantilla/xlsx", bodeguero().token()).esperando(200);

        assertThat(plantilla.tipoContenido()).contains(XLSX);
        assertThat(plantilla.disposicion()).contains("attachment").contains("plantilla-entradas.xlsx");
        assertThat(ExcelPruebas.hojas(plantilla.bytes())).containsExactly("Entradas", "Instrucciones");
        assertThat(ExcelPruebas.leerHoja(plantilla.bytes(), "Entradas").get(0))
                .containsExactly("Código", "N° lote", "Fecha vencimiento", "Cantidad", "Costo unitario");
        assertThat(descargar("/api/movimientos/entradas/plantilla/xlsx", null).estado()).isEqualTo(401);
    }

    @Test
    @DisplayName("HU-200 Cargar entradas desde Excel: se leen y validan, se informan las filas inválidas y se importan las válidas")
    void cargarEntradasDesdeExcel() {
        var noPerecible = crearProducto(false);
        var perecibleB = crearProducto(true);
        var perecibleE = crearProducto(true);
        var sinMovimientos = crearProducto(false);
        var inactivo = crearProducto(false);
        delete("/api/productos/" + inactivo.id(), bodeguero().token()).esperando(204);
        LocalDate venceB = hoy().plusDays(30);
        LocalDate venceE = hoy().plusDays(60);

        List<List<Object>> filas = new ArrayList<>();
        // Columnas: Código, N° lote, Fecha vencimiento, Cantidad, Costo unitario.
        filas.add(fila(noPerecible.codigo(), "LA-1", null, 10, 500)); // 2 válida
        filas.add(fila(perecibleB.codigo(), "LB-1", venceB.format(DD_MM_AAAA), 8, null)); // 3 válida: fecha como texto
        filas.add(fila(Long.parseLong(perecibleE.codigo()), "LE-1", venceE, 6, null)); // 4 válida: código y fecha como celdas
        filas.add(fila(noPerecible.codigo(), "LA-1", null, 5, null)); // 5 válida: mismo producto y lote otra vez
        filas.add(fila(perecibleB.codigo(), "LB-2", "31-02-2027", 3, null)); // 6 fecha inexistente
        filas.add(fila(perecibleE.codigo(), "LE-2", "mañana", 3, null)); // 7 fecha mal escrita
        filas.add(fila(perecibleE.codigo(), "LE-3", null, 3, null)); // 8 perecible sin fecha
        filas.add(fila("NOEXISTE-" + unico(), "LX", null, 3, null)); // 9 código desconocido
        filas.add(fila(sinMovimientos.codigo(), "LC-1", null, "diez", null)); // 10 cantidad no numérica
        filas.add(fila(sinMovimientos.codigo(), "LC-2", null, 0, null)); // 11 cantidad cero
        filas.add(fila(sinMovimientos.codigo(), "LC-3", null, 2.5, null)); // 12 cantidad decimal
        filas.add(fila(sinMovimientos.codigo(), "LC-4", null, 4, -5)); // 13 costo negativo
        filas.add(fila(inactivo.codigo(), "LD-1", null, 4, null)); // 14 producto deshabilitado
        filas.add(fila()); // 15 vacía
        byte[] archivo = entradasXlsx(filas);

        var leidas = subirArchivo(ENTRADAS_LEER, bodeguero().token(), "archivo", "entradas.xlsx", archivo)
                .esperando(200).json();
        assertThat(leidas).hasSize(13);
        // Leer no guarda nada.
        assertThat(stockDe(noPerecible.id())).isZero();
        assertThat(stockDe(perecibleB.id())).isZero();
        Map<Integer, JsonNode> porFila = porFila(leidas);

        for (int valida : new int[] {2, 3, 4, 5}) assertThat(errores(porFila.get(valida))).as("fila %d", valida).isEmpty();
        // La fila trae el producto que resolvió por el código.
        assertThat(porFila.get(2).path("datos").path("productoId").asLong()).isEqualTo(noPerecible.id());
        assertThat(porFila.get(2).path("datos").path("productoNombre").asText()).isEqualTo(noPerecible.nombre());
        assertThat(porFila.get(3).path("datos").path("fechaVencimiento").asText()).isEqualTo(venceB.toString());
        assertThat(porFila.get(4).path("datos").path("fechaVencimiento").asText()).isEqualTo(venceE.toString());

        assertThat(errores(porFila.get(6))).containsOnlyKeys("fechaVencimiento");
        assertThat(errores(porFila.get(6)).get("fechaVencimiento")).contains("Fecha inválida");
        assertThat(errores(porFila.get(7))).containsOnlyKeys("fechaVencimiento");
        assertThat(errores(porFila.get(8)).get("fechaVencimiento")).contains("perecible");
        assertThat(errores(porFila.get(9))).containsOnlyKeys("codigo");
        assertThat(errores(porFila.get(9)).get("codigo")).contains("No existe un producto");
        assertThat(errores(porFila.get(10))).containsKey("cantidad");
        assertThat(errores(porFila.get(10)).get("cantidad")).contains("número entero");
        assertThat(errores(porFila.get(11))).containsKey("cantidad");
        assertThat(errores(porFila.get(12))).containsKey("cantidad");
        assertThat(errores(porFila.get(13))).containsKey("costoUnitario");
        assertThat(errores(porFila.get(14))).containsKey("codigo");
        assertThat(errores(porFila.get(14)).get("codigo")).contains("inactivo");

        // Se importan las filas válidas como una sola operación de compra.
        List<JsonNode> validas = new ArrayList<>();
        for (JsonNode f : leidas) {
            if (f.path("errores").isEmpty()) validas.add(f.path("datos"));
        }
        assertThat(validas).hasSize(4);
        var operacion = post("/api/movimientos/entradas/importar", bodeguero().token(),
                objeto("tipo", "COMPRA", "nota", "Carga inicial " + unico(), "filas", validas)).esperando(201).json();
        assertThat(operacion.path("tipo").asText()).isEqualTo("COMPRA");
        assertThat(operacion.path("nota").asText()).startsWith("Carga inicial");
        assertThat(operacion.path("lineas")).hasSize(4);

        // El stock sube solo en los productos de las filas válidas; las inválidas no dejaron rastro.
        assertThat(stockDe(noPerecible.id())).isEqualTo(15);
        var stockB = stock(perecibleB.id());
        assertThat(stockB.cantidad()).isEqualTo(8);
        assertThat(stockB.proximoVencimiento()).isEqualTo(venceB);
        var stockE = stock(perecibleE.id());
        assertThat(stockE.cantidad()).isEqualTo(6);
        assertThat(stockE.proximoVencimiento()).isEqualTo(venceE);
        assertThat(movimientos(sinMovimientos.id())).isEmpty();
        assertThat(movimientos(inactivo.id())).isEmpty();

        // Cada fila creó un lote: el mismo N° lote repetido son dos lotes, con el costo de la fila o el del producto.
        var compras = movimientosDeTipo(noPerecible.id(), "COMPRA");
        assertThat(compras).hasSize(2);
        assertThat(compras).allMatch(m -> "LA-1".equals(m.path("numeroLote").asText()));
        assertThat(compras.stream().map(m -> m.path("costoUnitario").asInt())).containsExactlyInAnyOrder(500, 1000);
        assertThat(compras.stream().map(m -> m.path("loteId").asLong()).distinct()).hasSize(2);
    }

    @Test
    @DisplayName("HU-200 Cargar entradas es todo o nada: con una fila inválida no se importa ninguna; corregida, se importan todas")
    void cargarEntradasEsTodoONada() {
        var comun = crearProducto(false);
        var perecible = crearProducto(true);
        byte[] archivo = entradasXlsx(List.of(
                fila(comun.codigo(), "L-1", null, 7, null),
                fila(perecible.codigo(), "L-2", "99-99-2027", 4, null)));
        var leidas = subirArchivo(ENTRADAS_LEER, bodeguero().token(), "archivo", "entradas.xlsx", archivo)
                .esperando(200).json();
        List<JsonNode> todas = new ArrayList<>();
        leidas.forEach(f -> todas.add(f.path("datos")));

        var rechazada = post("/api/movimientos/entradas/importar", bodeguero().token(),
                objeto("tipo", "COMPRA", "filas", todas));
        assertThat(rechazada.estado()).isEqualTo(422);
        assertThat(rechazada.mensaje()).contains("1 fila con errores");
        assertThat(stockDe(comun.id())).isZero();
        assertThat(movimientos(comun.id())).isEmpty();
        assertThat(movimientos(perecible.id())).isEmpty();

        // Se corrige la fecha en la vista previa y se revalida.
        Map<String, Object> corregida = new HashMap<>();
        todas.get(1).propertyNames().forEach(campo -> corregida.put(campo, valorDe(todas.get(1).get(campo))));
        corregida.put("fechaVencimiento", hoy().plusDays(90).toString());
        List<Object> filasCorregidas = List.of(todas.get(0), corregida);
        var revalidadas = post("/api/movimientos/entradas/importar/validar", bodeguero().token(),
                objeto("tipo", "COMPRA", "filas", filasCorregidas)).esperando(200).json();
        assertThat(revalidadas.get(0).path("errores")).isEmpty();
        assertThat(revalidadas.get(1).path("errores")).isEmpty();
        assertThat(stockDe(comun.id())).isZero();

        post("/api/movimientos/entradas/importar", bodeguero().token(),
                objeto("tipo", "COMPRA", "filas", filasCorregidas)).esperando(201);
        assertThat(stockDe(comun.id())).isEqualTo(7);
        assertThat(stockDe(perecible.id())).isEqualTo(4);

        // Un tipo de operación que no es una entrada se rechaza.
        var tipoMalo = post("/api/movimientos/entradas/importar", bodeguero().token(),
                objeto("tipo", "CONSUMO_INTERNO", "filas", filasCorregidas));
        assertThat(tipoMalo.estado()).isEqualTo(422);
        assertThat(tipoMalo.campo()).isEqualTo("tipo");
        assertThat(stockDe(comun.id())).isEqualTo(7);
    }

    @Test
    @DisplayName("HU-200 Un Excel de entradas que no es la plantilla se rechaza con un mensaje")
    void excelDeEntradasInvalidoSeRechaza() {
        String token = bodeguero().token();
        byte[] plantilla = descargar("/api/movimientos/entradas/plantilla/xlsx", token).esperando(200).bytes();

        var texto = subirArchivo(ENTRADAS_LEER, token, "archivo", "entradas.csv", "a,b".getBytes());
        assertThat(texto.estado()).isEqualTo(422);
        assertThat(texto.mensaje()).contains(".xlsx");
        var sinFilas = subirArchivo(ENTRADAS_LEER, token, "archivo", "entradas.xlsx", plantilla);
        assertThat(sinFilas.estado()).isEqualTo(422);
        assertThat(sinFilas.mensaje()).contains("no tiene entradas");
        var faltaCantidad = subirArchivo(ENTRADAS_LEER, token, "archivo", "entradas.xlsx",
                ExcelPruebas.libroConEncabezados("Entradas", "Código", "N° lote"));
        assertThat(faltaCantidad.estado()).isEqualTo(422);
        assertThat(faltaCantidad.mensaje()).contains("Faltan columnas").contains("Cantidad");
        var corrupto = subirArchivo(ENTRADAS_LEER, token, "archivo", "entradas.xlsx", "basura".getBytes());
        assertThat(corrupto.estado()).isEqualTo(422);

        byte[] archivo = entradasXlsx(List.of(fila("1", "L", null, 1, null)));
        assertThat(subirArchivo(ENTRADAS_LEER, null, "archivo", "entradas.xlsx", archivo).estado()).isEqualTo(401);
        assertThat(subirArchivo(ENTRADAS_LEER, tokenAdmin(), "archivo", "entradas.xlsx", archivo).estado())
                .isEqualTo(403);
    }

    // ------------------------------------------------------------------ ayudantes

    private byte[] productosXlsx(List<List<Object>> filas) {
        byte[] plantilla = descargar("/api/productos/plantilla/xlsx", bodeguero().token()).esperando(200).bytes();
        return ExcelPruebas.llenarPlantilla(plantilla, "Productos", filas);
    }

    private byte[] entradasXlsx(List<List<Object>> filas) {
        byte[] plantilla = descargar("/api/movimientos/entradas/plantilla/xlsx", bodeguero().token())
                .esperando(200).bytes();
        return ExcelPruebas.llenarPlantilla(plantilla, "Entradas", filas);
    }

    /** Crea un producto con los códigos indicados y devuelve su id. */
    private long crearProductoCon(Catalogo categoria, Catalogo unidad, String ptv, String codigo, String nombre,
                                  boolean perecible, int minimo, int critico, int costo) {
        return post("/api/productos", bodeguero().token(), objeto("codigoPtv", ptv, "codigo", codigo,
                "nombre", nombre, "categoriaId", categoria.id(), "unidadMedidaId", unidad.id(),
                "esPerecible", perecible, "stockMinimo", minimo, "stockCritico", critico, "costoUnitario", costo))
                .esperando(201).json().path("id").asLong();
    }

    /** Filas validadas por su número de fila en el Excel. */
    private static Map<Integer, JsonNode> porFila(JsonNode validadas) {
        Map<Integer, JsonNode> mapa = new HashMap<>();
        for (JsonNode f : validadas) mapa.put(f.path("datos").path("fila").asInt(), f);
        return mapa;
    }

    private static Map<String, String> errores(JsonNode filaValidada) {
        Map<String, String> errores = new HashMap<>();
        filaValidada.path("errores").propertyNames()
                .forEach(campo -> errores.put(campo, filaValidada.path("errores").path(campo).asText()));
        return errores;
    }

    /** Valor simple de un nodo JSON, para armar la fila corregida que la vista previa vuelve a enviar. */
    private static Object valorDe(JsonNode nodo) {
        if (nodo == null || nodo.isNull()) return null;
        if (nodo.isBoolean()) return nodo.asBoolean();
        if (nodo.isNumber()) return nodo.asLong();
        return nodo.asText();
    }

    private long productosConSufijo(String sufijo) {
        long total = 0;
        for (JsonNode p : get("/api/productos?soloActivos=false", bodeguero().token()).esperando(200).json()) {
            if (p.path("nombre").asText().contains(sufijo)) total++;
        }
        return total;
    }

    private JsonNode productoPorNombre(String nombre) {
        for (JsonNode p : get("/api/productos?soloActivos=false", bodeguero().token()).esperando(200).json()) {
            if (nombre.equals(p.path("nombre").asText())) return p;
        }
        return tools.jackson.databind.node.MissingNode.getInstance();
    }

    private static List<String> filaConNombre(List<List<String>> hoja, String nombre) {
        return hoja.stream().filter(f -> f.size() > 2 && nombre.equals(f.get(2))).findFirst()
                .orElseThrow(() -> new AssertionError("El Excel no tiene la fila de '" + nombre + "'"));
    }

    private static String textoDelPdf(byte[] pdf) {
        try {
            PdfReader lector = new PdfReader(pdf);
            PdfTextExtractor extractor = new PdfTextExtractor(lector);
            StringBuilder texto = new StringBuilder();
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                texto.append(extractor.getTextFromPage(pagina)).append('\n');
            }
            lector.close();
            // El PDF parte las celdas largas en varias líneas: se normalizan los espacios y saltos.
            return texto.toString().replaceAll("\\s+", " ");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo leer el PDF descargado", e);
        }
    }
}
