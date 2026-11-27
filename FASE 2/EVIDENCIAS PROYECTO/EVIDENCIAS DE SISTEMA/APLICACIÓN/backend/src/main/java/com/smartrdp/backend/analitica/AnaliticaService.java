package com.smartrdp.backend.analitica;

import com.smartrdp.backend.analitica.dto.VencimientoDto;
import com.smartrdp.backend.movimiento.MovimientoRepository;
import com.smartrdp.backend.movimiento.VistasInventario;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Vencimientos y exportación a Excel del libro. Los cálculos del panel (consumo, productos más usados,
 * pedidos por persona) viven en {@link PanelService}.
 */
@Service
@RequiredArgsConstructor
public class AnaliticaService {

    private final MovimientoRepository movimientoRepository;
    private final VistasInventario vistas;

    @Transactional(readOnly = true)
    public List<VencimientoDto> getVencimientos(int diasUmbral, Long categoriaId, List<Long> productoIds,
                                                Long proveedorId) {
        // El límite de la consulta es exclusivo: se suma un día para que el lote que vence justo en N días entre.
        LocalDate limiteExclusivo = LocalDate.now().plusDays(diasUmbral + 1L);
        return vistas.lotesConVencimiento(null, limiteExclusivo, categoriaId, productoIds, proveedorId).stream()
                .map(this::aVencimiento)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VencimientoDto> getVencimientosPorRango(LocalDate desde, LocalDate hasta, Long categoriaId,
                                                        List<Long> productoIds, Long proveedorId) {
        return vistas.lotesConVencimiento(desde, hasta.plusDays(1), categoriaId, productoIds, proveedorId).stream()
                .map(this::aVencimiento)
                .toList();
    }

    private VencimientoDto aVencimiento(VistasInventario.LoteConVencimiento l) {
        return new VencimientoDto(l.productoId(), l.productoNombre(), l.saldo(), l.fechaVencimiento(),
                l.fechaVencimiento().isBefore(LocalDate.now()));
    }

    @Transactional(readOnly = true)
    public byte[] exportarMovimientosExcel(LocalDate desde, LocalDate hasta, Long categoriaId,
                                           List<Long> productoIds, Long solicitanteId, Long proveedorId)
            throws IOException {
        boolean todos = productoIds == null || productoIds.isEmpty();
        var movimientos = new ArrayList<>(movimientoRepository.buscarParaExportar(desde.atStartOfDay(),
                hasta.plusDays(1).atStartOfDay(), categoriaId, todos, todos ? List.of(-1L) : productoIds,
                solicitanteId, proveedorId));
        Collections.reverse(movimientos); // la consulta viene de más reciente a más antigua

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Movimientos");
            Row header = sheet.createRow(0);
            String[] cols = {"ID", "Operación", "Producto", "Tipo", "Sentido", "Cantidad", "Lote",
                    "Costo unitario", "Costo total", "Nota", "Fecha", "Usuario", "Proveedor"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

            int rowIdx = 1;
            for (var m : movimientos) {
                var operacion = m.getOperacion();
                var lote = m.getLote();
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(m.getId());
                row.createCell(1).setCellValue(operacion.getId());
                row.createCell(2).setCellValue(lote.getProducto().getNombre());
                row.createCell(3).setCellValue(operacion.getTipo().getNombre());
                row.createCell(4).setCellValue(operacion.getTipo().esEntrada() ? "Entrada" : "Salida");
                row.createCell(5).setCellValue(m.getCantidad());
                row.createCell(6).setCellValue(lote.getNumeroLote() != null ? lote.getNumeroLote() : "");
                if (lote.getCostoUnitario() != null) {
                    row.createCell(7).setCellValue(lote.getCostoUnitario());
                    row.createCell(8).setCellValue((double) lote.getCostoUnitario() * m.getCantidad());
                }
                row.createCell(9).setCellValue(operacion.getNota() != null ? operacion.getNota() : "");
                row.createCell(10).setCellValue(operacion.getCreatedAt() != null ? operacion.getCreatedAt().toString() : "");
                row.createCell(11).setCellValue(operacion.getUsuario().getEmail());
                row.createCell(12).setCellValue(lote.getProveedor() != null ? lote.getProveedor().getNombre() : "");
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
