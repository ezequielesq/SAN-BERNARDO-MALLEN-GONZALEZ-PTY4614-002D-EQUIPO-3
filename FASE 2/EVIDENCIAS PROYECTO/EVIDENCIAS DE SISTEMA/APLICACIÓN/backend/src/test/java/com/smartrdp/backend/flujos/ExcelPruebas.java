package com.smartrdp.backend.flujos;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Arma y lee archivos Excel en memoria para las pruebas de importación y exportación. Los archivos que se
 * suben se construyen a partir de la plantilla real que entrega la API: se abre, se escriben las filas de
 * prueba debajo de los encabezados y se vuelve a serializar.
 */
final class ExcelPruebas {

    private ExcelPruebas() {}

    /**
     * Escribe {@code filas} en la hoja indicada de la plantilla, desde la fila 2 (la 1 son los encabezados).
     * Cada celda puede ser texto, número, {@link LocalDate} (celda con formato de fecha) o {@code null} (vacía).
     */
    static byte[] llenarPlantilla(byte[] plantilla, String hoja, List<List<Object>> filas) {
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(plantilla));
             var salida = new ByteArrayOutputStream()) {
            Sheet sheet = libro.getSheet(hoja);
            if (sheet == null) throw new IllegalStateException("La plantilla no tiene la hoja " + hoja);
            CellStyle estiloFecha = libro.createCellStyle();
            estiloFecha.setDataFormat(libro.getCreationHelper().createDataFormat().getFormat("dd-mm-yyyy"));

            int indice = 1;
            for (List<Object> fila : filas) {
                Row row = sheet.createRow(indice++);
                for (int c = 0; c < fila.size(); c++) {
                    Object valor = fila.get(c);
                    if (valor == null) continue;
                    Cell celda = row.createCell(c);
                    if (valor instanceof LocalDate fecha) {
                        celda.setCellValue(java.sql.Date.valueOf(fecha));
                        celda.setCellStyle(estiloFecha);
                    } else if (valor instanceof Number numero) {
                        celda.setCellValue(numero.doubleValue());
                    } else {
                        celda.setCellValue(valor.toString());
                    }
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo armar el Excel de prueba", e);
        }
    }

    /** Libro nuevo con una sola hoja que tiene solo esta fila de encabezados (sin datos). */
    static byte[] libroConEncabezados(String hoja, String... encabezados) {
        try (var libro = new XSSFWorkbook(); var salida = new ByteArrayOutputStream()) {
            Row row = libro.createSheet(hoja).createRow(0);
            for (int i = 0; i < encabezados.length; i++) row.createCell(i).setCellValue(encabezados[i]);
            libro.write(salida);
            return salida.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo armar el Excel de prueba", e);
        }
    }

    /** Atajo para escribir una fila: {@code fila("a", 1, null)}. */
    static List<Object> fila(Object... celdas) {
        return new ArrayList<>(Arrays.asList(celdas));
    }

    /** Nombres de las hojas del libro. */
    static List<String> hojas(byte[] xlsx) {
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            List<String> nombres = new ArrayList<>();
            for (int i = 0; i < libro.getNumberOfSheets(); i++) nombres.add(libro.getSheetName(i));
            return nombres;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("El archivo no es un Excel válido", e);
        }
    }

    /** Contenido de una hoja como texto, fila por fila (las celdas vacías quedan como cadena vacía). */
    static List<List<String>> leerHoja(byte[] xlsx, String hoja) {
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = libro.getSheet(hoja);
            if (sheet == null) throw new IllegalStateException("El archivo no tiene la hoja " + hoja);
            DataFormatter formato = new DataFormatter();
            List<List<String>> filas = new ArrayList<>();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> celdas = new ArrayList<>();
                if (row != null) {
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        Cell celda = row.getCell(c);
                        celdas.add(celda == null ? "" : formato.formatCellValue(celda));
                    }
                }
                filas.add(celdas);
            }
            return filas;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("El archivo no es un Excel válido", e);
        }
    }
}
