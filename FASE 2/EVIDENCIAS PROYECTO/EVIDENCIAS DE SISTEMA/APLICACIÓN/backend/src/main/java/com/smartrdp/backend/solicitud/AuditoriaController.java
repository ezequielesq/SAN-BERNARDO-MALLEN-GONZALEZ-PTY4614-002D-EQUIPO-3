package com.smartrdp.backend.solicitud;

import com.smartrdp.backend.auditoria.AuditoriaService;
import com.smartrdp.backend.auditoria.RevisionDto;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @GetMapping("/historial")
    public List<RevisionDto> historial(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return auditoriaService.getHistorialSolicitudes(desde, hasta);
    }

    /**
     * Historial de entregas en Excel: una fila por producto de cada revisión de una solicitud, con fecha,
     * responsable, estado, producto, cantidad solicitada y cantidad entregada.
     */
    @GetMapping("/historial/xlsx")
    public ResponseEntity<byte[]> exportarXlsx(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) throws IOException {
        List<RevisionDto> revisiones = auditoriaService.getHistorialSolicitudes(desde, hasta);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Historial de entregas");
            Row header = sheet.createRow(0);
            String[] cols = {"Solicitud", "Fecha", "Responsable", "Tipo de revisión", "Estado", "Tipo",
                    "Producto", "Cantidad solicitada", "Cantidad entregada"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

            int rowIdx = 1;
            for (var rev : revisiones) {
                var lineas = rev.lineas() == null || rev.lineas().isEmpty()
                        ? java.util.Collections.singletonList((RevisionDto.LineaRevisionDto) null)
                        : rev.lineas();
                for (var linea : lineas) {
                    Row row = sheet.createRow(rowIdx++);
                    if (rev.solicitudId() != null) row.createCell(0).setCellValue(rev.solicitudId());
                    row.createCell(1).setCellValue(rev.fecha() != null ? rev.fecha().toString() : "");
                    row.createCell(2).setCellValue(rev.usuarioEmail() != null ? rev.usuarioEmail() : "");
                    row.createCell(3).setCellValue(rev.tipoRevision() != null ? rev.tipoRevision() : "");
                    row.createCell(4).setCellValue(rev.estadoSolicitud() != null ? rev.estadoSolicitud() : "");
                    row.createCell(5).setCellValue(rev.tipoSolicitud() != null ? rev.tipoSolicitud() : "");
                    if (linea != null) {
                        row.createCell(6).setCellValue(linea.productoNombre() != null ? linea.productoNombre() : "");
                        if (linea.cantidadSolicitada() != null) row.createCell(7).setCellValue(linea.cantidadSolicitada());
                        if (linea.cantidadEntregada() != null) row.createCell(8).setCellValue(linea.cantidadEntregada());
                    }
                }
            }
            wb.write(out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=historial-de-entregas.xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }
}
