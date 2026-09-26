// src/main/java/com/smartrdp/backend/solicitud/AuditoriaController.java
package com.smartrdp.backend.solicitud;

import com.smartrdp.backend.solicitud.dto.SolicitudResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.time.*;
import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final SolicitudRepository solicitudRepository;

    @Transactional(readOnly = true)
    @GetMapping("/historial")
    public List<SolicitudResponse> historial(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return solicitudRepository
                .findByCreatedAtBetweenOrderByCreatedAtDesc(
                        desde.atStartOfDay(), hasta.atTime(23, 59, 59))
                .stream()
                .map(s -> {
                    var detalles = s.getDetalles().stream()
                            .map(d -> new SolicitudResponse.DetalleDto(
                                    d.getProducto().getId(),
                                    d.getProducto().getNombre(),
                                    d.getCantidadSolicitada(),
                                    d.getCantidadEntregada()))
                            .toList();
                    return new SolicitudResponse(
                            s.getId(),
                            s.getSolicitante() != null ? s.getSolicitante().getNombre() : "-",
                            s.getEstado(),
                            s.getCreatedAt(),
                            detalles);
                }).toList();
    }

    @Transactional(readOnly = true)
    @GetMapping("/historial/pdf")
    public ResponseEntity<byte[]> exportarPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) throws IOException {
        var solicitudes = solicitudRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                desde.atStartOfDay(), hasta.atTime(23, 59, 59));

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Historial");
            Row header = sheet.createRow(0);
            String[] cols = {"Solicitud ID", "Solicitante", "Estado", "Producto",
                             "Cant. Solicitada", "Cant. Entregada", "Fecha"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

            int rowIdx = 1;
            for (var s : solicitudes) {
                for (var d : s.getDetalles()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(s.getId() != null ? s.getId() : 0);
                    row.createCell(1).setCellValue(s.getSolicitante() != null ? s.getSolicitante().getNombre() : "-");
                    row.createCell(2).setCellValue(s.getEstado().name());
                    row.createCell(3).setCellValue(d.getProducto().getNombre());
                    row.createCell(4).setCellValue(d.getCantidadSolicitada());
                    row.createCell(5).setCellValue(d.getCantidadEntregada() != null ? d.getCantidadEntregada() : 0);
                    row.createCell(6).setCellValue(s.getCreatedAt() != null ? s.getCreatedAt().toString() : "");
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
