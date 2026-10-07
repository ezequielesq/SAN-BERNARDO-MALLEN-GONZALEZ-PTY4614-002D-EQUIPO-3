package com.smartrdp.backend.solicitud;

import com.smartrdp.backend.movimiento.Movimiento;
import com.smartrdp.backend.producto.Producto;
import com.smartrdp.backend.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Entity
@Table(name = "detalle_solicitudes")
public class DetalleSolicitud extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false)
    private Solicitud solicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "cantidad_solicitada", nullable = false)
    private Integer cantidadSolicitada;

    // Bodeguero puede modificar antes de aprobar (HU-16)
    @Column(name = "cantidad_entregada")
    private Integer cantidadEntregada;

    /** Solo en devoluciones: el detalle del pedido original que se está devolviendo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detalle_origen_id")
    private DetalleSolicitud detalleOrigen;

    /** Movimiento SOLICITADO (pedido) o DEVOLUCION que generó la aprobación de este detalle. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimiento_id")
    private Movimiento movimiento;
}
