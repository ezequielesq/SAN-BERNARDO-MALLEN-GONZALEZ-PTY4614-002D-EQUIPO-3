package com.smartrdp.backend.solicitud;

import com.smartrdp.backend.solicitud.dto.SolicitudResponse;
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

    private final SolicitudService solicitudService;

    @GetMapping("/historial")
    public List<SolicitudResponse> historial(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return solicitudService.listarPorFecha(desde, hasta);
    }

    @GetMapping("/historial/pdf")
    public ResponseEntity<byte[]> exportarPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) throws IOException {
        var solicitudes = solicitudService.listarPorFecha(desde, hasta);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Historial");
            Row header = sheet.createRow(0);
            String[] cols = {"Solicitud ID", "Solicitante", "Estado", "Producto",
                             "Cant. Solicitada", "Cant. Entregada", "Fecha"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

            int rowIdx = 1;
            for (var s : solicitudes) {
                for (var d : s.detalles()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(s.id() != null ? s.id() : 0);
                    row.createCell(1).setCellValue(s.solicitanteNombre());
                    row.createCell(2).setCellValue(s.estado().name());
                    row.createCell(3).setCellValue(d.productoNombre());
                    row.createCell(4).setCellValue(d.cantidadSolicitada());
                    row.createCell(5).setCellValue(d.cantidadEntregada() != null ? d.cantidadEntregada() : 0);
                    row.createCell(6).setCellValue(s.fecha() != null ? s.fecha().toString() : "");
                }
            }
            wb.write(out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=historial-auditoria.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }
}
