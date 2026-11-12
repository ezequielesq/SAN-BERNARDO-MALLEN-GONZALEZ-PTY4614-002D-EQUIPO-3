package com.smartrdp.backend.movimiento;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lecturas de las vistas del esquema (saldo_lote, stock_producto, solicitado_vs_entregado,
 * devolvible_lote). El stock nunca se guarda: se lee de aquí. Las escrituras que se hicieron en
 * la misma transacción deben estar volcadas (saveAndFlush) antes de llamar a estos métodos.
 */
@Component
@RequiredArgsConstructor
public class VistasInventario {

    public record SaldoLote(long loteId, long productoId, LocalDate fechaVencimiento,
                            Integer costoUnitario, int saldo) {}

    /** estado: AGOTADO | CRITICO | BAJO | NORMAL. valor: nulo si ningún lote tiene costo. */
    public record StockProducto(long productoId, int stock, Long valor, String estado) {}

    /** cantidadSolicitada es nula en un agregado; cantidadEntregada es nula si la solicitud no está aprobada. */
    public record EntregaProducto(long solicitudId, long productoId, Integer cantidadSolicitada,
                                  Integer cantidadEntregada, boolean agregado) {}

    public record DevolvibleLote(long pedidoId, long loteId, long productoId, long movimientoId,
                                 int entregado, int devuelto, int devolvible) {}

    public record DevolvibleProducto(long pedidoId, long productoId, int entregado, int devuelto, int devolvible) {}

    public record LoteConVencimiento(long loteId, long productoId, String productoNombre, String numeroLote,
                                     int saldo, LocalDate fechaVencimiento) {}

    private final JdbcClient jdbc;

    public Map<Long, Integer> saldosPorLote(Collection<Long> loteIds) {
        if (loteIds.isEmpty()) return Map.of();
        Map<Long, Integer> saldos = new HashMap<>();
        jdbc.sql("SELECT lote_id, saldo FROM saldo_lote WHERE lote_id IN (:ids)")
                .param("ids", loteIds)
                .query((rs, n) -> {
                    saldos.put(rs.getLong("lote_id"), rs.getInt("saldo"));
                    return null;
                }).list();
        return saldos;
    }

    public List<StockProducto> stocks() {
        return jdbc.sql("SELECT producto_id, stock, valor, estado FROM stock_producto")
                .query(StockProducto.class).list();
    }

    public Optional<StockProducto> stock(long productoId) {
        return jdbc.sql("SELECT producto_id, stock, valor, estado FROM stock_producto WHERE producto_id = :id")
                .param("id", productoId)
                .query(StockProducto.class).optional();
    }

    /** Fecha de vencimiento más próxima entre los lotes con saldo, por producto. */
    public Map<Long, LocalDate> proximosVencimientos() {
        Map<Long, LocalDate> proximos = new HashMap<>();
        jdbc.sql("""
                SELECT producto_id, MIN(fecha_vencimiento) AS fecha
                  FROM saldo_lote
                 WHERE saldo > 0 AND fecha_vencimiento IS NOT NULL
                 GROUP BY producto_id
                """)
                .query((rs, n) -> {
                    proximos.put(rs.getLong("producto_id"), rs.getObject("fecha", LocalDate.class));
                    return null;
                }).list();
        return proximos;
    }

    public Optional<LocalDate> proximoVencimiento(long productoId) {
        // MIN sin filas devuelve una fila con NULL: se filtra en lugar de usar single().
        return jdbc.sql("""
                SELECT MIN(fecha_vencimiento) AS fecha
                  FROM saldo_lote
                 WHERE producto_id = :id AND saldo > 0 AND fecha_vencimiento IS NOT NULL
                """)
                .param("id", productoId)
                .query((rs, n) -> rs.getObject("fecha", LocalDate.class))
                .list().stream().filter(java.util.Objects::nonNull).findFirst();
    }

    public List<EntregaProducto> solicitadoVsEntregado(Collection<Long> solicitudIds) {
        if (solicitudIds.isEmpty()) return List.of();
        return jdbc.sql("""
                SELECT solicitud_id, producto_id, cantidad_solicitada, cantidad_entregada, agregado
                  FROM solicitado_vs_entregado
                 WHERE solicitud_id IN (:ids)
                """)
                .param("ids", solicitudIds)
                .query(EntregaProducto.class).list();
    }

    /** Lotes de los que salió el producto en el pedido, el último consumido primero, con lo aún devolvible. */
    public List<DevolvibleLote> devolviblePorLote(long pedidoId, long productoId) {
        return jdbc.sql("""
                SELECT pedido_id, lote_id, producto_id, movimiento_id, entregado, devuelto, devolvible
                  FROM devolvible_lote
                 WHERE pedido_id = :pedido AND producto_id = :producto AND devolvible > 0
                 ORDER BY movimiento_id DESC
                """)
                .param("pedido", pedidoId)
                .param("producto", productoId)
                .query(DevolvibleLote.class).list();
    }

    public List<DevolvibleProducto> devolviblePorProducto(Collection<Long> pedidoIds) {
        if (pedidoIds.isEmpty()) return List.of();
        return jdbc.sql("""
                SELECT pedido_id, producto_id,
                       SUM(entregado)::integer AS entregado,
                       SUM(devuelto)::integer AS devuelto,
                       SUM(devolvible)::integer AS devolvible
                  FROM devolvible_lote
                 WHERE pedido_id IN (:ids)
                 GROUP BY pedido_id, producto_id
                """)
                .param("ids", pedidoIds)
                .query(DevolvibleProducto.class).list();
    }

    /** Cantidad ya pedida en devoluciones aún pendientes del pedido, por producto (no se puede pedir dos veces). */
    public Map<Long, Integer> pendienteDeDevolver(long pedidoId) {
        Map<Long, Integer> pendientes = new HashMap<>();
        jdbc.sql("""
                SELECT d.producto_id, SUM(d.cantidad_solicitada)::integer AS cantidad
                  FROM solicitudes s
                  JOIN estados_solicitud e ON e.id = s.estado_id AND e.codigo = 'PENDIENTE'
                  JOIN detalle_solicitudes d ON d.solicitud_id = s.id
                 WHERE s.solicitud_origen_id = :pedido
                 GROUP BY d.producto_id
                """)
                .param("pedido", pedidoId)
                .query((rs, n) -> {
                    pendientes.put(rs.getLong("producto_id"), rs.getInt("cantidad"));
                    return null;
                }).list();
        return pendientes;
    }

    /**
     * Lotes con saldo cuyo vencimiento cae en [desde, hastaExcl). {@code desde} y {@code categoriaId}
     * son opcionales (nulos = sin límite / todas las categorías).
     */
    public List<LoteConVencimiento> lotesConVencimiento(LocalDate desde, LocalDate hastaExcl, Long categoriaId) {
        return jdbc.sql("""
                SELECT s.lote_id, s.producto_id, p.nombre AS producto_nombre, l.numero_lote,
                       s.saldo, s.fecha_vencimiento
                  FROM saldo_lote s
                  JOIN lotes l ON l.id = s.lote_id
                  JOIN productos p ON p.id = s.producto_id
                 WHERE s.saldo > 0
                   AND s.fecha_vencimiento IS NOT NULL
                   AND s.fecha_vencimiento < :hasta
                   AND (CAST(:desde AS date) IS NULL OR s.fecha_vencimiento >= :desde)
                   AND (CAST(:categoria AS bigint) IS NULL OR p.categoria_id = :categoria)
                 ORDER BY s.fecha_vencimiento, s.lote_id
                """)
                .param("hasta", hastaExcl)
                .param("desde", desde, Types.DATE)
                .param("categoria", categoriaId, Types.BIGINT)
                .query(LoteConVencimiento.class).list();
    }
}
