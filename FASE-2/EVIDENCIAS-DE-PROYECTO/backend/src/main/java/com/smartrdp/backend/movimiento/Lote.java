package com.smartrdp.backend.movimiento;

import com.smartrdp.backend.producto.Producto;
import com.smartrdp.backend.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;

/** Tanda física de un producto. Inmutable: su saldo se deriva de los movimientos. */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Immutable
@Entity
@Table(name = "lotes")
public class Lote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "numero_lote", length = 50)
    private String numeroLote;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "costo_unitario")
    private Integer costoUnitario;
}
