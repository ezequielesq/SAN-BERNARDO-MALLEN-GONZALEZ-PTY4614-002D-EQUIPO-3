package com.smartrdp.backend.producto;

import com.smartrdp.backend.catalogo.CategoriaRepository;
import com.smartrdp.backend.catalogo.UnidadMedidaRepository;
import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.producto.dto.FilaImportacion;
import com.smartrdp.backend.producto.dto.FilaValidada;
import com.smartrdp.backend.producto.dto.ProductoRequest;
import com.smartrdp.backend.producto.dto.ProductoResponse;
import com.smartrdp.backend.shared.excel.LectorExcel;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Carga masiva de productos desde la plantilla Excel: lee y valida sin guardar, y crea todo o nada.
 * Las reglas de cada producto son las mismas de {@link ProductoService#crear}.
 */
@Service
@RequiredArgsConstructor
public class ProductoImportacionService {

    public static final int MAXIMO_FILAS = 500;
    private static final int FILAS_BUSQUEDA_ENCABEZADO = 20;

    private static final String[] COLUMNAS = {"Código PTV", "Código", "Nombre", "Categoría", "Unidad",
            "Perecible", "Stock mínimo", "Stock crítico", "Costo unitario"};

    /** Encabezado normalizado (sin tildes ni símbolos) → campo interno. */
    private static final Map<String, String> ALIAS = Map.ofEntries(
            Map.entry("codigoptv", "codigoPtv"), Map.entry("ptv", "codigoPtv"),
            Map.entry("codigo", "codigo"),
            Map.entry("nombre", "nombre"), Map.entry("producto", "nombre"),
            Map.entry("categoria", "categoria"),
            Map.entry("unidad", "unidad"), Map.entry("unidaddemedida", "unidad"),
            Map.entry("unidadmedida", "unidad"),
            Map.entry("perecible", "perecible"), Map.entry("esperecible", "perecible"),
            Map.entry("stockminimo", "stockMinimo"), Map.entry("minimo", "stockMinimo"),
            Map.entry("stockcritico", "stockCritico"), Map.entry("critico", "stockCritico"),
            Map.entry("costounitario", "costo"), Map.entry("costo", "costo"));

    private static final Map<String, String> ETIQUETA_OBLIGATORIA = new LinkedHashMap<>();

    static {
        ETIQUETA_OBLIGATORIA.put("nombre", "Nombre");
        ETIQUETA_OBLIGATORIA.put("categoria", "Categoría");
        ETIQUETA_OBLIGATORIA.put("unidad", "Unidad");
        ETIQUETA_OBLIGATORIA.put("stockMinimo", "Stock mínimo");
        ETIQUETA_OBLIGATORIA.put("stockCritico", "Stock crítico");
        ETIQUETA_OBLIGATORIA.put("costo", "Costo unitario");
    }

    private static final Set<String> SI = Set.of("si", "s", "x", "true", "1", "yes", "y");
    private static final Set<String> NO = Set.of("no", "n", "false", "0");

    private final ProductoService productoService;
    private final CategoriaRepository categoriaRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;

    /** Una fila leída del Excel junto con los problemas de formato que se detectaron al leerla. */
    private record FilaLeida(FilaImportacion datos, Map<String, String> errores) {}

    // ───────────────────────────── Plantilla ─────────────────────────────

    @Transactional(readOnly = true)
    public byte[] plantillaXlsx() {
        try (Workbook wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            Font negrita = wb.createFont();
            negrita.setBold(true);
            var estiloEncabezado = wb.createCellStyle();
            estiloEncabezado.setFont(negrita);

            Sheet productos = wb.createSheet("Productos");
            Row encabezado = productos.createRow(0);
            for (int i = 0; i < COLUMNAS.length; i++) {
                Cell celda = encabezado.createCell(i);
                celda.setCellValue(COLUMNAS[i]);
                celda.setCellStyle(estiloEncabezado);
                productos.setColumnWidth(i, (i == 2 ? 40 : 18) * 256);
            }
            productos.createFreezePane(0, 1);

            Sheet ayuda = wb.createSheet("Instrucciones");
            int fila = 0;
            fila = escribirTitulo(ayuda, fila, "Cómo cargar productos", estiloEncabezado);
            for (String linea : new String[] {
                    "1. Pega tus datos en la hoja «Productos», debajo de los encabezados. Puedes copiar columnas completas.",
                    "2. Los encabezados identifican cada columna: si pegas con ellos, el orden no importa.",
                    "3. Obligatorios: Nombre, Categoría, Unidad, Stock mínimo, Stock crítico y Costo unitario.",
                    "4. Código: las categorías marcadas «PTV» usan Código PTV (letras, números y guiones); las demás usan "
                            + "Código numérico. Llena solo uno de los dos.",
                    "5. Categoría y Unidad deben escribirse como en las listas de más abajo.",
                    "6. Perecible: escribe Sí o No (vacío = No).",
                    "7. Stock crítico debe ser menor o igual al Stock mínimo. Costo y stocks son números enteros.",
                    "8. Máximo " + MAXIMO_FILAS + " filas por archivo. Antes de crear nada verás una lista para revisar y corregir.",
            }) {
                ayuda.createRow(fila++).createCell(0).setCellValue(linea);
            }

            fila++;
            fila = escribirTitulo(ayuda, fila, "Categorías disponibles", estiloEncabezado);
            for (var categoria : categoriaRepository.findByActivoTrueOrderByNombreAsc()) {
                Row row = ayuda.createRow(fila++);
                row.createCell(0).setCellValue(categoria.getNombre());
                row.createCell(1).setCellValue(categoria.isRequierePtv() ? "Código PTV" : "Código numérico");
            }

            fila++;
            fila = escribirTitulo(ayuda, fila, "Unidades de medida disponibles", estiloEncabezado);
            for (var unidad : unidadMedidaRepository.findByActivoTrueOrderByNombreAsc()) {
                ayuda.createRow(fila++).createCell(0).setCellValue(unidad.getNombre());
            }
            ayuda.setColumnWidth(0, 60 * 256);
            ayuda.setColumnWidth(1, 22 * 256);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la plantilla de productos", e);
        }
    }

    private static int escribirTitulo(Sheet hoja, int fila, String titulo, org.apache.poi.ss.usermodel.CellStyle estilo) {
        Cell celda = hoja.createRow(fila).createCell(0);
        celda.setCellValue(titulo);
        celda.setCellStyle(estilo);
        return fila + 1;
    }

    // ───────────────────────────── Lectura ─────────────────────────────

    /** Lee el Excel y valida cada fila, sin guardar nada. */
    @Transactional(readOnly = true)
    public List<FilaValidada> leer(InputStream archivo) {
        List<FilaLeida> leidas = leerFilas(archivo);
        Map<Integer, Map<String, String>> erroresDeLectura = new HashMap<>();
        leidas.forEach(l -> erroresDeLectura.put(l.datos().fila(), l.errores()));
        return validarFilas(leidas.stream().map(FilaLeida::datos).toList(), erroresDeLectura);
    }

    private List<FilaLeida> leerFilas(InputStream archivo) {
        try (Workbook wb = new XSSFWorkbook(archivo)) {
            Sheet hoja = wb.getSheet("Productos");
            if (hoja == null) hoja = wb.getSheetAt(0);
            FormulaEvaluator evaluador = wb.getCreationHelper().createFormulaEvaluator();

            Row filaEncabezado = null;
            Map<String, Integer> columnas = new HashMap<>();
            int limite = Math.min(hoja.getLastRowNum(), FILAS_BUSQUEDA_ENCABEZADO);
            for (int r = hoja.getFirstRowNum(); r <= limite && filaEncabezado == null; r++) {
                Row row = hoja.getRow(r);
                if (row == null) continue;
                Map<String, Integer> candidatas = new HashMap<>();
                for (Cell celda : row) {
                    String campo = ALIAS.get(LectorExcel.clave(LectorExcel.texto(LectorExcel.valor(celda, evaluador))));
                    if (campo != null) candidatas.putIfAbsent(campo, celda.getColumnIndex());
                }
                if (candidatas.containsKey("nombre")) {
                    filaEncabezado = row;
                    columnas = candidatas;
                }
            }
            if (filaEncabezado == null) {
                throw new BusinessException("No se encontraron los encabezados de la plantilla. "
                        + "Descarga la plantilla y pega tus datos debajo de sus encabezados.");
            }
            List<String> faltantes = new ArrayList<>();
            for (var obligatoria : ETIQUETA_OBLIGATORIA.entrySet()) {
                if (!columnas.containsKey(obligatoria.getKey())) faltantes.add(obligatoria.getValue());
            }
            if (!faltantes.isEmpty()) {
                throw new BusinessException("Faltan columnas en el archivo: " + String.join(", ", faltantes)
                        + ". Revisa que coincidan con la plantilla.");
            }

            Map<String, Long> categorias = new HashMap<>();
            categoriaRepository.findAll().forEach(c -> categorias.put(LectorExcel.clave(c.getNombre()), c.getId()));
            Map<String, Long> unidades = new HashMap<>();
            unidadMedidaRepository.findAll().forEach(u -> unidades.put(LectorExcel.clave(u.getNombre()), u.getId()));

            List<FilaLeida> filas = new ArrayList<>();
            for (int r = filaEncabezado.getRowNum() + 1; r <= hoja.getLastRowNum(); r++) {
                Row row = hoja.getRow(r);
                if (row == null) continue;
                Map<String, Object> celdas = new HashMap<>();
                boolean vacia = true;
                for (var columna : columnas.entrySet()) {
                    Object v = LectorExcel.valor(row.getCell(columna.getValue()), evaluador);
                    celdas.put(columna.getKey(), v);
                    if (LectorExcel.texto(v) != null) vacia = false;
                }
                if (vacia) continue;
                if (filas.size() >= MAXIMO_FILAS) {
                    throw new BusinessException("El archivo tiene más de " + MAXIMO_FILAS
                            + " filas. Divídelo en archivos más pequeños.");
                }
                filas.add(construirFila(r + 1, celdas, categorias, unidades));
            }
            if (filas.isEmpty()) {
                throw new BusinessException("El archivo no tiene productos debajo de los encabezados.");
            }
            return filas;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("No se pudo leer el archivo. Verifica que sea un Excel .xlsx válido.");
        }
    }

    private FilaLeida construirFila(int numero, Map<String, Object> celdas, Map<String, Long> categorias,
                                    Map<String, Long> unidades) {
        Map<String, String> errores = new LinkedHashMap<>();

        String categoriaTexto = LectorExcel.texto(celdas.get("categoria"));
        Long categoriaId = null;
        if (categoriaTexto != null) {
            categoriaId = categorias.get(LectorExcel.clave(categoriaTexto));
            if (categoriaId == null) {
                errores.put("categoriaId", "No existe la categoría «" + categoriaTexto + "». Elige una de la lista.");
            }
        }
        String unidadTexto = LectorExcel.texto(celdas.get("unidad"));
        Long unidadId = null;
        if (unidadTexto != null) {
            unidadId = unidades.get(LectorExcel.clave(unidadTexto));
            if (unidadId == null) {
                errores.put("unidadMedidaId", "No existe la unidad «" + unidadTexto + "». Elige una de la lista.");
            }
        }

        var datos = new FilaImportacion(
                numero,
                LectorExcel.texto(celdas.get("codigoPtv")),
                LectorExcel.texto(celdas.get("codigo")),
                LectorExcel.texto(celdas.get("nombre")),
                categoriaId,
                unidadId,
                categoriaTexto,
                unidadTexto,
                perecible(celdas.get("perecible"), errores),
                LectorExcel.entero(celdas.get("stockMinimo"), "stockMinimo", errores),
                LectorExcel.entero(celdas.get("stockCritico"), "stockCritico", errores),
                LectorExcel.entero(celdas.get("costo"), "costoUnitario", errores));
        return new FilaLeida(datos, errores);
    }

    // ───────────────────────────── Validación y alta ─────────────────────────────

    /** Revalida filas ya editadas (sin Excel): los códigos repetidos entre filas también cuentan. */
    @Transactional(readOnly = true)
    public List<FilaValidada> validar(List<FilaImportacion> filas) {
        return validarFilas(filas, Map.of());
    }

    /** Crea todos los productos o ninguno: una fila con error cancela la importación completa. */
    @Transactional
    public List<ProductoResponse> importar(List<FilaImportacion> filas) {
        List<FilaValidada> validadas = validarFilas(filas, Map.of());
        long conError = validadas.stream().filter(f -> !f.errores().isEmpty()).count();
        if (conError > 0) {
            throw new BusinessException("Hay " + conError + (conError == 1 ? " fila con errores" : " filas con errores")
                    + ". Corrígelas antes de crear los productos.");
        }
        return filas.stream().map(f -> productoService.crear(aSolicitud(f))).toList();
    }

    private List<FilaValidada> validarFilas(List<FilaImportacion> filas,
                                            Map<Integer, Map<String, String>> erroresDeLectura) {
        List<FilaImportacion> numeradas = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            FilaImportacion f = filas.get(i);
            numeradas.add(f.fila() != null ? f : new FilaImportacion(i + 1, f.codigoPtv(), f.codigo(), f.nombre(),
                    f.categoriaId(), f.unidadMedidaId(), f.categoriaTexto(), f.unidadTexto(), f.esPerecible(),
                    f.stockMinimo(), f.stockCritico(), f.costoUnitario()));
        }

        List<Map<String, String>> errores = new ArrayList<>();
        for (FilaImportacion f : numeradas) {
            Map<String, String> deFila = new LinkedHashMap<>(erroresDeLectura.getOrDefault(f.fila(), Map.of()));
            productoService.diagnosticar(aSolicitud(f)).forEach(deFila::putIfAbsent);
            errores.add(deFila);
        }
        marcarCodigosRepetidos(numeradas, errores);

        List<FilaValidada> resultado = new ArrayList<>();
        for (int i = 0; i < numeradas.size(); i++) resultado.add(new FilaValidada(numeradas.get(i), errores.get(i)));
        return resultado;
    }

    /** Un mismo código en dos filas del archivo es un error en todas ellas: la base solo lo vería al guardar. */
    private void marcarCodigosRepetidos(List<FilaImportacion> filas, List<Map<String, String>> errores) {
        Map<String, List<Integer>> porCodigo = new LinkedHashMap<>();
        for (int i = 0; i < filas.size(); i++) {
            FilaImportacion f = filas.get(i);
            String ptv = LectorExcel.limpiar(f.codigoPtv());
            String codigo = LectorExcel.limpiar(f.codigo());
            if (ptv != null) porCodigo.computeIfAbsent("ptv:" + ptv.toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(i);
            if (codigo != null) porCodigo.computeIfAbsent("num:" + codigo, k -> new ArrayList<>()).add(i);
        }
        for (var grupo : porCodigo.entrySet()) {
            if (grupo.getValue().size() < 2) continue;
            String campo = grupo.getKey().startsWith("ptv:") ? "codigoPtv" : "codigo";
            for (int indice : grupo.getValue()) {
                String otras = grupo.getValue().stream().filter(j -> j != indice)
                        .map(j -> String.valueOf(filas.get(j).fila())).reduce((a, b) -> a + ", " + b).orElse("");
                errores.get(indice).put(campo, "Código repetido en el archivo (también en la fila "
                        + otras + ").");
            }
        }
    }

    private static ProductoRequest aSolicitud(FilaImportacion f) {
        return new ProductoRequest(f.codigoPtv(), f.codigo(), f.nombre(), f.categoriaId(), f.unidadMedidaId(),
                f.esPerecible(), f.stockMinimo(), f.stockCritico(), f.costoUnitario());
    }

    // ───────────────────────────── Celdas ─────────────────────────────

    private static boolean perecible(Object valor, Map<String, String> errores) {
        if (valor == null) return false;
        if (valor instanceof Boolean b) return b;
        String normalizado = LectorExcel.clave(LectorExcel.texto(valor));
        if (normalizado.isEmpty() || NO.contains(normalizado)) return false;
        if (SI.contains(normalizado)) return true;
        errores.put("esPerecible", "Escribe Sí o No.");
        return false;
    }
}
