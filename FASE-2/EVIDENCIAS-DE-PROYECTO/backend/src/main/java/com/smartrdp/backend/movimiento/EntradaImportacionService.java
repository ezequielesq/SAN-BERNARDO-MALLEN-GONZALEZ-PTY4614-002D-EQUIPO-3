package com.smartrdp.backend.movimiento;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.movimiento.dto.EntradaRequest;
import com.smartrdp.backend.movimiento.dto.FilaEntrada;
import com.smartrdp.backend.movimiento.dto.FilaEntradaValidada;
import com.smartrdp.backend.movimiento.dto.ImportacionEntradaRequest;
import com.smartrdp.backend.movimiento.dto.OperacionResponse;
import com.smartrdp.backend.producto.Producto;
import com.smartrdp.backend.producto.ProductoRepository;
import com.smartrdp.backend.shared.excel.LectorExcel;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Carga masiva de entradas de stock desde la plantilla Excel: lee y valida sin guardar, y registra todo o nada.
 * Una carga es una sola operación (el tipo y la nota vienen de la pantalla) y cada fila crea un lote nuevo
 * mediante {@link MovimientoService#registrarIngreso}.
 */
@Service
@RequiredArgsConstructor
public class EntradaImportacionService {

    public static final int MAXIMO_FILAS = 200;
    private static final int FILAS_BUSQUEDA_ENCABEZADO = 20;
    private static final int CANTIDAD_MAXIMA = 1_000_000;
    private static final int LOTE_MAXIMO = 50;

    private static final String[] COLUMNAS = {"Código", "N° lote", "Fecha vencimiento", "Cantidad", "Costo unitario"};

    /** Encabezado normalizado (sin tildes ni símbolos) → campo interno. */
    private static final Map<String, String> ALIAS = Map.ofEntries(
            Map.entry("codigo", "codigo"), Map.entry("codigoptv", "codigo"),
            Map.entry("ptv", "codigo"), Map.entry("producto", "codigo"),
            Map.entry("nlote", "numeroLote"), Map.entry("numerolote", "numeroLote"),
            Map.entry("lote", "numeroLote"),
            Map.entry("fechavencimiento", "fechaVencimiento"), Map.entry("vencimiento", "fechaVencimiento"),
            Map.entry("cantidad", "cantidad"),
            Map.entry("costounitario", "costoUnitario"), Map.entry("costo", "costoUnitario"));

    private static final Map<String, String> ETIQUETA_OBLIGATORIA = new LinkedHashMap<>();

    static {
        ETIQUETA_OBLIGATORIA.put("codigo", "Código");
        ETIQUETA_OBLIGATORIA.put("cantidad", "Cantidad");
    }

    private final ProductoRepository productoRepository;
    private final MovimientoService movimientoService;

    /** Una fila leída del Excel junto con los problemas de formato que se detectaron al leerla. */
    private record FilaLeida(FilaEntrada datos, Map<String, String> errores) {}

    // ───────────────────────────── Plantilla ─────────────────────────────

    public byte[] plantillaXlsx() {
        try (Workbook wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            Font negrita = wb.createFont();
            negrita.setBold(true);
            CellStyle estiloEncabezado = wb.createCellStyle();
            estiloEncabezado.setFont(negrita);

            Sheet entradas = wb.createSheet("Entradas");
            Row encabezado = entradas.createRow(0);
            for (int i = 0; i < COLUMNAS.length; i++) {
                Cell celda = encabezado.createCell(i);
                celda.setCellValue(COLUMNAS[i]);
                celda.setCellStyle(estiloEncabezado);
                entradas.setColumnWidth(i, 20 * 256);
            }
            entradas.createFreezePane(0, 1);

            Sheet ayuda = wb.createSheet("Instrucciones");
            Cell titulo = ayuda.createRow(0).createCell(0);
            titulo.setCellValue("Cómo cargar entradas de stock");
            titulo.setCellStyle(estiloEncabezado);
            int fila = 1;
            for (String linea : new String[] {
                    "1. Pega tus datos en la hoja «Entradas», debajo de los encabezados. Puedes copiar columnas completas.",
                    "2. Los encabezados identifican cada columna: si pegas con ellos, el orden no importa.",
                    "3. Código: el código PTV o el código interno del producto. Es obligatorio.",
                    "4. N° lote: opcional.",
                    "5. Fecha vencimiento: obligatoria para los productos perecibles. Usa el formato dd-mm-aaaa.",
                    "6. Cantidad: número entero mayor o igual a 1. Es obligatoria.",
                    "7. Costo unitario: opcional. Si no lo indicas, se usa el costo de referencia del producto.",
                    "8. Cada fila crea un lote nuevo. Máximo " + MAXIMO_FILAS + " filas por archivo.",
                    "9. El tipo de entrada (por ejemplo, compra) y la nota se eligen en pantalla, no en el archivo.",
                    "10. Todo o nada: antes de cargar verás una lista para revisar y corregir; si una fila tiene errores, "
                            + "no se carga ninguna.",
            }) {
                ayuda.createRow(fila++).createCell(0).setCellValue(linea);
            }
            ayuda.setColumnWidth(0, 110 * 256);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la plantilla de entradas", e);
        }
    }

    // ───────────────────────────── Lectura ─────────────────────────────

    /** Lee el Excel y valida cada fila, sin guardar nada. */
    @Transactional(readOnly = true)
    public List<FilaEntradaValidada> leer(InputStream archivo) {
        List<FilaLeida> leidas = leerFilas(archivo);
        Map<Integer, Map<String, String>> erroresDeLectura = new HashMap<>();
        leidas.forEach(l -> erroresDeLectura.put(l.datos().fila(), l.errores()));
        return validarFilas(leidas.stream().map(FilaLeida::datos).toList(), erroresDeLectura);
    }

    private List<FilaLeida> leerFilas(InputStream archivo) {
        try (Workbook wb = new XSSFWorkbook(archivo)) {
            Sheet hoja = wb.getSheet("Entradas");
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
                if (candidatas.containsKey("codigo")) {
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
                Map<String, String> errores = new LinkedHashMap<>();
                Integer columnaFecha = columnas.get("fechaVencimiento");
                LocalDate fechaVencimiento = columnaFecha == null ? null
                        : LectorExcel.fecha(row.getCell(columnaFecha), evaluador, "fechaVencimiento", errores);
                var datos = new FilaEntrada(
                        r + 1,
                        LectorExcel.texto(celdas.get("codigo")),
                        null,
                        null,
                        LectorExcel.texto(celdas.get("numeroLote")),
                        fechaVencimiento,
                        LectorExcel.entero(celdas.get("cantidad"), "cantidad", errores),
                        LectorExcel.entero(celdas.get("costoUnitario"), "costoUnitario", errores));
                filas.add(new FilaLeida(datos, errores));
            }
            if (filas.isEmpty()) {
                throw new BusinessException("El archivo no tiene entradas debajo de los encabezados.");
            }
            return filas;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("No se pudo leer el archivo. Verifica que sea un Excel .xlsx válido.");
        }
    }

    // ───────────────────────────── Validación y alta ─────────────────────────────

    /** Revalida filas ya editadas (sin Excel). */
    @Transactional(readOnly = true)
    public List<FilaEntradaValidada> validar(List<FilaEntrada> filas) {
        return validarFilas(filas, Map.of());
    }

    /** Registra todas las entradas o ninguna: una fila con error cancela la carga completa. */
    @Transactional
    public OperacionResponse importar(ImportacionEntradaRequest request) {
        List<FilaEntradaValidada> validadas = validarFilas(request.filas(), Map.of());
        long conError = validadas.stream().filter(f -> !f.errores().isEmpty()).count();
        if (conError > 0) {
            throw new BusinessException("Hay " + conError + (conError == 1 ? " fila con errores" : " filas con errores")
                    + ". Corrígelas antes de cargar las entradas.");
        }
        List<EntradaRequest.Linea> lineas = validadas.stream()
                .map(FilaEntradaValidada::datos)
                .map(f -> new EntradaRequest.Linea(f.productoId(), f.cantidad(), f.fechaVencimiento(),
                        f.numeroLote(), f.costoUnitario()))
                .toList();
        return movimientoService.registrarIngreso(new EntradaRequest(request.tipo(), request.nota(), lineas));
    }

    private List<FilaEntradaValidada> validarFilas(List<FilaEntrada> filas,
                                                   Map<Integer, Map<String, String>> erroresDeLectura) {
        Map<String, Optional<Producto>> porCodigo = new HashMap<>();
        List<FilaEntradaValidada> resultado = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            FilaEntrada f = filas.get(i);
            int numero = f.fila() != null ? f.fila() : i + 1;
            Map<String, String> errores = new LinkedHashMap<>(erroresDeLectura.getOrDefault(numero, Map.of()));

            String codigo = LectorExcel.limpiar(f.codigo());
            String numeroLote = LectorExcel.limpiar(f.numeroLote());

            // El código manda: si el usuario lo cambió en la vista previa, se vuelve a resolver el producto.
            Producto producto = null;
            if (codigo == null) {
                errores.putIfAbsent("codigo", "Ingresa el código del producto.");
            } else {
                producto = porCodigo.computeIfAbsent(codigo.toUpperCase(Locale.ROOT), c -> buscarProducto(codigo))
                        .orElse(null);
                if (producto == null) {
                    errores.putIfAbsent("codigo", "No existe un producto con ese código.");
                } else if (!producto.isActivo()) {
                    errores.putIfAbsent("codigo", "El producto está inactivo.");
                }
            }

            if (f.cantidad() == null || f.cantidad() < 1 || f.cantidad() > CANTIDAD_MAXIMA) {
                errores.putIfAbsent("cantidad", "Ingresa una cantidad entre 1 y 1.000.000.");
            }
            if (numeroLote != null && numeroLote.length() > LOTE_MAXIMO) {
                errores.putIfAbsent("numeroLote",
                        "El número de lote no puede superar los " + LOTE_MAXIMO + " caracteres.");
            }
            if (f.costoUnitario() != null && f.costoUnitario() < 0) {
                errores.putIfAbsent("costoUnitario", "El costo no puede ser negativo.");
            }
            if (producto != null && producto.isEsPerecible() && f.fechaVencimiento() == null) {
                errores.putIfAbsent("fechaVencimiento", "El producto es perecible: indica su fecha de vencimiento.");
            }

            var datos = new FilaEntrada(numero, codigo,
                    producto != null ? producto.getId() : null,
                    producto != null ? producto.getNombre() : null,
                    numeroLote, f.fechaVencimiento(), f.cantidad(), f.costoUnitario());
            resultado.add(new FilaEntradaValidada(datos, errores));
        }
        return resultado;
    }

    /** Código PTV (sin distinguir mayúsculas) y, si no hay, código interno. */
    private Optional<Producto> buscarProducto(String codigo) {
        Optional<Producto> porPtv = productoRepository.findByCodigoPtvIgnoreCase(codigo);
        return porPtv.isPresent() ? porPtv : productoRepository.findByCodigo(codigo);
    }
}
