package com.smartrdp.backend.movimiento;

import com.smartrdp.backend.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

/** Línea del libro: cuánto de qué lote. Cantidad siempre positiva; el signo lo da el tipo de la operación. */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Immutable
@Entity
@Table(name = "movimientos")
public class Movimiento extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operacion_id", nullable = false)
    private Operacion operacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;
}
