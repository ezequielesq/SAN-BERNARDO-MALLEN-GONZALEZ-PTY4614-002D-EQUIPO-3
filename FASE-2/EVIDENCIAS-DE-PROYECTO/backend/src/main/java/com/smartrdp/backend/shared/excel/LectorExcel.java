package com.smartrdp.backend.shared.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lectura tolerante de celdas Excel, común a las cargas masivas (productos, entradas de stock). */
public final class LectorExcel {

    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"));

    private LectorExcel() {}

    public static Object valor(Cell celda, FormulaEvaluator evaluador) {
        if (celda == null) return null;
        CellType tipo = celda.getCellType();
        if (tipo == CellType.FORMULA) {
            try {
                CellValue resultado = evaluador.evaluate(celda);
                return switch (resultado.getCellType()) {
                    case STRING -> resultado.getStringValue();
                    case NUMERIC -> resultado.getNumberValue();
                    case BOOLEAN -> resultado.getBooleanValue();
                    default -> null;
                };
            } catch (RuntimeException e) {
                return null;
            }
        }
        return switch (tipo) {
            case STRING -> celda.getStringCellValue();
            case NUMERIC -> celda.getNumericCellValue();
            case BOOLEAN -> celda.getBooleanCellValue();
            default -> null;
        };
    }

    /** Texto recortado; un número entero (por ejemplo un código pegado como número) pierde el «.0». */
    public static String texto(Object valor) {
        if (valor == null) return null;
        if (valor instanceof Double d) {
            return d == Math.rint(d) && Math.abs(d) < 1e15
                    ? Long.toString(d.longValue())
                    : BigDecimal.valueOf(d).toPlainString();
        }
        String recortado = valor.toString().trim();
        return recortado.isEmpty() ? null : recortado;
    }

    public static Integer entero(Object valor, String campo, Map<String, String> errores) {
        if (valor == null || (valor instanceof String s && s.isBlank())) return null;
        try {
            if (valor instanceof Double d) {
                if (d != Math.rint(d)) throw new NumberFormatException();
                return Math.toIntExact(d.longValue());
            }
            return Integer.parseInt(valor.toString().trim().replace(" ", ""));
        } catch (NumberFormatException | ArithmeticException e) {
            errores.put(campo, "Debe ser un número entero, sin decimales ni símbolos.");
            return null;
        }
    }

    /**
     * Fecha de una celda: numérica con formato de fecha, o texto {@code yyyy-MM-dd}, {@code dd-MM-yyyy} o
     * {@code dd/MM/yyyy}. Vacía da {@code null}; si no se entiende, registra el error del campo y da {@code null}.
     */
    public static LocalDate fecha(Cell celda, FormulaEvaluator evaluador, String campo, Map<String, String> errores) {
        Object valor = valor(celda, evaluador);
        if (valor == null || (valor instanceof String s && s.isBlank())) return null;
        if (valor instanceof Double d && DateUtil.isCellDateFormatted(celda)) {
            return DateUtil.getLocalDateTime(d).toLocalDate();
        }
        if (valor instanceof String s) {
            String recortado = s.trim();
            for (DateTimeFormatter formato : FORMATOS_FECHA) {
                try {
                    return LocalDate.parse(recortado, formato);
                } catch (DateTimeParseException e) {
                    // se prueba con el siguiente formato
                }
            }
        }
        errores.put(campo, "Fecha inválida. Usa dd-mm-aaaa.");
        return null;
    }

    /** Minúsculas sin tildes ni símbolos: «Código PTV» y «codigo_ptv» dan la misma clave. */
    public static String clave(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    public static String limpiar(String valor) {
        if (valor == null) return null;
        String recortado = valor.trim();
        return recortado.isEmpty() ? null : recortado;
    }
}
