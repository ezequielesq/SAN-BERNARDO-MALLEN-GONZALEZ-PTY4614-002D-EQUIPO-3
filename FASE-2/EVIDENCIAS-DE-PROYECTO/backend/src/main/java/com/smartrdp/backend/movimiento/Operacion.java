package com.smartrdp.backend.movimiento;

import com.smartrdp.backend.shared.BaseEntity;
import com.smartrdp.backend.solicitud.Solicitud;
import com.smartrdp.backend.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.util.ArrayList;
import java.util.List;

/** Cabecera del libro: un hecho de bodega (qué tipo, quién, cuándo = createdAt) con sus líneas. */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Immutable
@Entity
@Table(name = "operaciones")
public class Operacion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_id", nullable = false)
    private TipoOperacion tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Solo en entrega de pedido y devolución. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_id")
    private Solicitud solicitud;

    /** Solo en correcciones: la operación que se contrarresta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operacion_corregida_id")
    private Operacion operacionCorregida;

    @Column(name = "nota", length = 300)
    private String nota;

    @OneToMany(mappedBy = "operacion", cascade = CascadeType.PERSIST)
    private List<Movimiento> movimientos = new ArrayList<>();

    public void agregarMovimiento(Lote lote, int cantidad) {
        Movimiento movimiento = new Movimiento();
        movimiento.setOperacion(this);
        movimiento.setLote(lote);
        movimiento.setCantidad(cantidad);
        movimientos.add(movimiento);
    }
}
