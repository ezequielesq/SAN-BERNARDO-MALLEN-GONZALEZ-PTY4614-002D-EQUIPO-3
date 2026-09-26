package com.smartrdp.backend.movimiento;

import com.smartrdp.backend.producto.Producto;
import com.smartrdp.backend.shared.BaseEntity;
import com.smartrdp.backend.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Audited
@Entity
@Table(name = "movimientos")
public class Movimiento extends BaseEntity {

    @EqualsAndHashCode.Include
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoMovimiento tipo;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "motivo", length = 200)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    private Lote lote;
}
