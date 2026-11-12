package com.smartrdp.backend.analitica;

import com.smartrdp.backend.analitica.dto.ConsumoDto;
import com.smartrdp.backend.analitica.dto.ProductoMasUsadoDto;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Analítica sobre el libro. Las "entradas" y "salidas" se miden por el sentido del tipo de operación (incluye
 * compras, entregas de pedidos, mermas, etc.); las correcciones y las operaciones corregidas no cuentan.
 */
@Service
@RequiredArgsConstructor
public class AnaliticaService {

    private final MovimientoRepository movimientoRepository;
    private final VistasInventario vistas;

    @Transactional(readOnly = true)
    public List<ProductoMasUsadoDto> getProductosMasUsados(LocalDate desde, LocalDate hasta, Long categoriaId) {
        LocalDateTime desdeTime = desde.atStartOfDay();
        LocalDateTime hastaTime = hasta.atTime(23, 59, 59);
        return movimientoRepository.findConsumoEntreFechas(desdeTime, hastaTime, categoriaId).stream()
                .map(row -> new ProductoMasUsadoDto(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        (String) row[2],
                        ((Number) row[3]).longValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VencimientoDto> getVencimientos(int diasUmbral, Long categoriaId) {
        // El límite de la consulta es exclusivo: se suma un día para que el lote que vence justo en N días entre.
        LocalDate limiteExclusivo = LocalDate.now().plusDays(diasUmbral + 1L);
        return vistas.lotesConVencimiento(null, limiteExclusivo, categoriaId).stream()
                .map(this::aVencimiento)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VencimientoDto> getVencimientosPorRango(LocalDate desde, LocalDate hasta, Long categoriaId) {
        return vistas.lotesConVencimiento(desde, hasta.plusDays(1), categoriaId).stream()
                .map(this::aVencimiento)
                .toList();
    }

    private VencimientoDto aVencimiento(VistasInventario.LoteConVencimiento l) {
        return new VencimientoDto(l.productoId(), l.productoNombre(), l.saldo(), l.fechaVencimiento(),
                l.fechaVencimiento().isBefore(LocalDate.now()));
    }

    @Transactional(readOnly = true)
    public byte[] exportarMovimientosExcel(LocalDate desde, LocalDate hasta, Long categoriaId) throws IOException {
        LocalDateTime desdeTime = desde.atStartOfDay();
        LocalDateTime hastaTime = hasta.atTime(23, 59, 59);
        var movimientos = new ArrayList<>(movimientoRepository.buscar(desdeTime, hastaTime, null, categoriaId));
        Collections.reverse(movimientos); // la consulta viene de más reciente a más antigua

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Movimientos");
            Row header = sheet.createRow(0);
            String[] cols = {"ID", "Operación", "Producto", "Tipo", "Sentido", "Cantidad", "Lote",
                    "Costo unitario", "Costo total", "Nota", "Fecha", "Usuario"};
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
        // Sumas en long: costo × cantidad acumulado puede superar el rango de int.
        long entradaCantidad = ((Number) fila[2]).longValue();
        long entradaValorSuma = ((Number) fila[3]).longValue();
        long entradaConCosto = ((Number) fila[4]).longValue();
        long salidaCantidad = ((Number) fila[5]).longValue();
        long salidaValorSuma = ((Number) fila[6]).longValue();
        long salidaConCosto = ((Number) fila[7]).longValue();
        Long entradaValor = entradaConCosto == 0 ? null : entradaValorSuma;
        Long salidaValor = salidaConCosto == 0 ? null : salidaValorSuma;
        return new ConsumoDto(id, nombre, entradaCantidad, entradaValor, salidaCantidad, salidaValor);
    }
}
