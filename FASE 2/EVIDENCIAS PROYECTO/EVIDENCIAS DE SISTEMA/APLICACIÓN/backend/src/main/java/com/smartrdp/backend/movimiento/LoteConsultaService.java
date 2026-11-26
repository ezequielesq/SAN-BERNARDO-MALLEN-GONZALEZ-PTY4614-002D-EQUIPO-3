package com.smartrdp.backend.movimiento;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.movimiento.dto.LoteDetalleResponse;
import com.smartrdp.backend.movimiento.dto.LoteResponse;
import com.smartrdp.backend.movimiento.dto.MovimientoLoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Lectura de lotes: el saldo sale de la vista saldo_lote y el estado se deriva; nada de esto se guarda. */
@Service
@RequiredArgsConstructor
public class LoteConsultaService {

    public static final int DIAS_POR_VENCER = 7;
    private static final Set<String> ESTADOS = Set.of("AGOTADO", "VENCIDO", "POR_VENCER", "VIGENTE");

    private static final String SELECT_LOTES = """
            SELECT l.id, p.id AS producto_id, p.nombre AS producto_nombre, c.nombre AS categoria_nombre,
                   l.numero_lote, pr.id AS proveedor_id, pr.nombre AS proveedor_nombre,
                   l.created_at AS fecha_ingreso, l.fecha_vencimiento, l.costo_unitario,
                   COALESCE(ent.cantidad_inicial, 0) AS cantidad_inicial, s.saldo
              FROM lotes l
              JOIN saldo_lote s ON s.lote_id = l.id
              JOIN productos p ON p.id = l.producto_id
              JOIN categorias c ON c.id = p.categoria_id
              LEFT JOIN proveedores pr ON pr.id = l.proveedor_id
              LEFT JOIN (
                    SELECT m.lote_id, SUM(m.cantidad)::integer AS cantidad_inicial
                      FROM movimientos m
                      JOIN operaciones o ON o.id = m.operacion_id
                      JOIN tipos_operacion t ON t.id = o.tipo_id
                     WHERE t.sentido = 1 AND t.seleccionable
                     GROUP BY m.lote_id) ent ON ent.lote_id = l.id
             WHERE (CAST(:loteId AS bigint) IS NULL OR l.id = :loteId)
               AND (CAST(:productoId AS bigint) IS NULL OR l.producto_id = :productoId)
               AND (CAST(:categoriaId AS bigint) IS NULL OR p.categoria_id = :categoriaId)
               AND (CAST(:proveedorId AS bigint) IS NULL OR l.proveedor_id = :proveedorId)
               AND (CAST(:texto AS text) IS NULL OR l.numero_lote ILIKE '%' || CAST(:texto AS text) || '%')
             ORDER BY l.fecha_vencimiento NULLS LAST, l.id
            """;

    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public List<LoteResponse> listar(Long productoId, Long categoriaId, Long proveedorId, String estado,
                                     String texto, int diasPorVencer, boolean incluirAgotados) {
        if (estado != null && !ESTADOS.contains(estado)) {
            throw new BusinessException("Estado de lote desconocido: " + estado + ".", "estado");
        }
        if (diasPorVencer < 0) {
            throw new BusinessException("Los días para «por vencer» no pueden ser negativos.", "diasPorVencer");
        }
        boolean verAgotados = incluirAgotados || "AGOTADO".equals(estado);
        String buscado = texto == null || texto.isBlank() ? null : texto.trim();
        return consultar(null, productoId, categoriaId, proveedorId, buscado, diasPorVencer).stream()
                .filter(l -> estado != null ? estado.equals(l.estado()) : verAgotados || !"AGOTADO".equals(l.estado()))
                .toList();
    }

    @Transactional(readOnly = true)
    public LoteDetalleResponse detalle(Long id, int diasPorVencer) {
        LoteResponse lote = consultar(id, null, null, null, null, diasPorVencer).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Lote", id));
        List<MovimientoLoteResponse> trazabilidad = new ArrayList<>();
        int[] saldo = {0};
        jdbc.sql("""
                SELECT m.id AS movimiento_id, o.id AS operacion_id, o.created_at AS fecha, t.codigo, t.nombre,
                       t.sentido, m.cantidad, u.email AS usuario_email, o.nota, o.solicitud_id,
                       su.nombre AS solicitante_nombre
                  FROM movimientos m
                  JOIN operaciones o ON o.id = m.operacion_id
                  JOIN tipos_operacion t ON t.id = o.tipo_id
                  JOIN usuarios u ON u.id = o.usuario_id
                  LEFT JOIN solicitudes s ON s.id = o.solicitud_id
                  LEFT JOIN usuarios su ON su.id = s.solicitante_id
                 WHERE m.lote_id = :id
                 ORDER BY o.created_at, m.id
                """)
                .param("id", id)
                .query((rs, n) -> {
                    int sentido = rs.getInt("sentido");
                    int cantidad = rs.getInt("cantidad");
                    saldo[0] += sentido * cantidad;
                    trazabilidad.add(new MovimientoLoteResponse(
                            rs.getLong("movimiento_id"), rs.getLong("operacion_id"),
                            rs.getTimestamp("fecha").toLocalDateTime(), rs.getString("codigo"),
                            rs.getString("nombre"), sentido, cantidad, saldo[0], rs.getString("usuario_email"),
                            rs.getString("nota"), rs.getObject("solicitud_id", Long.class),
                            rs.getString("solicitante_nombre")));
                    return null;
                }).list();
        return new LoteDetalleResponse(lote, trazabilidad);
    }

    /** Estado derivado: agotado, vencido (fecha pasada), por vencer (hasta N días, el día N incluido) o vigente. */
    static String estado(int saldo, LocalDate vencimiento, LocalDate hoy, int diasPorVencer) {
        if (saldo <= 0) return "AGOTADO";
        if (vencimiento == null) return "VIGENTE";
        if (vencimiento.isBefore(hoy)) return "VENCIDO";
        if (!vencimiento.isAfter(hoy.plusDays(diasPorVencer))) return "POR_VENCER";
        return "VIGENTE";
    }

    private List<LoteResponse> consultar(Long loteId, Long productoId, Long categoriaId, Long proveedorId,
                                         String texto, int diasPorVencer) {
        LocalDate hoy = LocalDate.now();
        return jdbc.sql(SELECT_LOTES)
                .param("loteId", loteId, Types.BIGINT)
                .param("productoId", productoId, Types.BIGINT)
                .param("categoriaId", categoriaId, Types.BIGINT)
                .param("proveedorId", proveedorId, Types.BIGINT)
                .param("texto", texto, Types.VARCHAR)
                .query((rs, n) -> {
                    LocalDate vencimiento = rs.getObject("fecha_vencimiento", LocalDate.class);
                    Integer costo = rs.getObject("costo_unitario", Integer.class);
                    int saldo = rs.getInt("saldo");
                    return new LoteResponse(
                            rs.getLong("id"), rs.getLong("producto_id"), rs.getString("producto_nombre"),
                            rs.getString("categoria_nombre"), rs.getString("numero_lote"),
                            rs.getObject("proveedor_id", Long.class), rs.getString("proveedor_nombre"),
                            rs.getTimestamp("fecha_ingreso").toLocalDateTime(), vencimiento,
                            vencimiento == null ? null
                                    : (int) ChronoUnit.DAYS.between(hoy, vencimiento),
                            costo, rs.getInt("cantidad_inicial"), saldo,
                            costo == null ? null : (long) saldo * costo,
                            estado(saldo, vencimiento, hoy, diasPorVencer));
                }).list();
    }
}
