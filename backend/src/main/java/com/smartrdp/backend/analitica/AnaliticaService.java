package com.smartrdp.backend.analitica;

import com.smartrdp.backend.analitica.dto.ConsumoDto;
import com.smartrdp.backend.analitica.dto.*;
import com.smartrdp.backend.movimiento.LoteRepository;
import com.smartrdp.backend.movimiento.MovimientoRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnaliticaService {

    private final MovimientoRepository movimientoRepository;
    private final LoteRepository loteRepository;

    @Transactional(readOnly = true)
    public List<ProductoMasUsadoDto> getProductosMasUsados(LocalDate desde, LocalDate hasta) {
        LocalDateTime desdeTime = desde.atStartOfDay();
        LocalDateTime hastaTime = hasta.atTime(23, 59, 59);
        return movimientoRepository.findConsumoEntreFechas(desdeTime, hastaTime).stream()
                .map(row -> new ProductoMasUsadoDto(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        ((Number) row[2]).intValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VencimientoDto> getVencimientos(int diasUmbral) {
        LocalDate umbral = LocalDate.now().plusDays(diasUmbral);
        return loteRepository.findByFechaVencimientoBeforeAndCantidadDisponibleGreaterThan(umbral, 0)
                .stream()
                .map(l -> new VencimientoDto(
                        l.getProducto().getId(),
                        l.getProducto().getNombre(),
                        l.getCantidadDisponible(),
                        l.getFechaVencimiento(),
                        l.getFechaVencimiento().isBefore(LocalDate.now())))
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] exportarMovimientosExcel(LocalDate desde, LocalDate hasta) throws IOException {
        LocalDateTime desdeTime = desde.atStartOfDay();
        LocalDateTime hastaTime = hasta.atTime(23, 59, 59);
        var movimientos = movimientoRepository.findByCreatedAtBetween(desdeTime, hastaTime);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Movimientos");
            Row header = sheet.createRow(0);
            String[] cols = {"ID", "Producto", "Tipo", "Cantidad", "Motivo", "Fecha"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

            int rowIdx = 1;
            for (var m : movimientos) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(m.getId() != null ? m.getId() : 0);
                row.createCell(1).setCellValue(m.getProducto().getNombre());
                row.createCell(2).setCellValue(m.getTipo().name());
                row.createCell(3).setCellValue(m.getCantidad());
                row.createCell(4).setCellValue(m.getMotivo() != null ? m.getMotivo() : "");
                row.createCell(5).setCellValue(m.getCreatedAt() != null ? m.getCreatedAt().toString() : "");
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Transactional(readOnly = true)
    public List<ConsumoDto> getConsumo(LocalDate desde, LocalDate hasta, AgruparPor agruparPor, Long categoriaId) {
        LocalDateTime desdeTime = desde.atStartOfDay();
        LocalDateTime hastaTime = hasta.atTime(23, 59, 59);
        List<Object[]> filas = agruparPor == AgruparPor.CATEGORIA
                ? movimientoRepository.findConsumoPorCategoria(desdeTime, hastaTime, categoriaId)
                : movimientoRepository.findConsumoPorProducto(desdeTime, hastaTime, categoriaId);
        return filas.stream().map(this::mapearFilaConsumo).toList();
    }

    private ConsumoDto mapearFilaConsumo(Object[] fila) {
        Long id = ((Number) fila[0]).longValue();
        String nombre = (String) fila[1];
        int entradaCantidad = ((Number) fila[2]).intValue();
        int entradaValorSuma = ((Number) fila[3]).intValue();
        int entradaConCosto = ((Number) fila[4]).intValue();
        int salidaCantidad = ((Number) fila[5]).intValue();
        int salidaValorSuma = ((Number) fila[6]).intValue();
        int salidaConCosto = ((Number) fila[7]).intValue();
        Integer entradaValor = entradaConCosto == 0 ? null : entradaValorSuma;
        Integer salidaValor = salidaConCosto == 0 ? null : salidaValorSuma;
        return new ConsumoDto(id, nombre, entradaCantidad, entradaValor, salidaCantidad, salidaValor);
    }
}
