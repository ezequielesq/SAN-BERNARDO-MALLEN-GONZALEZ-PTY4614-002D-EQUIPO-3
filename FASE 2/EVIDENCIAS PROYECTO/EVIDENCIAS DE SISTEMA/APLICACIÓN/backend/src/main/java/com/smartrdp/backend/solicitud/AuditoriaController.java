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

    @GetMapping("/historial/pdf")
    public ResponseEntity<byte[]> exportarPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) throws IOException {
        List<RevisionDto> revisiones = auditoriaService.getHistorialSolicitudes(desde, hasta);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Historial Auditoría");
            Row header = sheet.createRow(0);
            String[] cols = {"Solicitud ID", "Fecha", "Usuario", "Tipo Revisión", "Estado", "Tipo"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

            int rowIdx = 1;
            for (var rev : revisiones) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(rev.solicitudId() != null ? rev.solicitudId() : 0);
                row.createCell(1).setCellValue(rev.fecha() != null ? rev.fecha().toString() : "");
                row.createCell(2).setCellValue(rev.usuarioEmail() != null ? rev.usuarioEmail() : "");
                row.createCell(3).setCellValue(rev.tipoRevision() != null ? rev.tipoRevision() : "");
                row.createCell(4).setCellValue(rev.estadoSolicitud() != null ? rev.estadoSolicitud() : "");
                row.createCell(5).setCellValue(rev.tipoSolicitud() != null ? rev.tipoSolicitud() : "");
            }
            wb.write(out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=historial-auditoria.xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }
}
