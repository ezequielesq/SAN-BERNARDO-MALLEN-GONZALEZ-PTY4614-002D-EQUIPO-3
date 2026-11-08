package com.smartrdp.backend.movimiento;

import com.smartrdp.backend.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Catálogo único de tipos de operación: cada tipo fija el sentido (+1 entra, -1 sale) del stock. */
@Getter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Immutable
@Entity
@Table(name = "tipos_operacion")
public class TipoOperacion extends BaseEntity {

    public static final String ENTREGA_PEDIDO = "ENTREGA_PEDIDO";
    public static final String DEVOLUCION = "DEVOLUCION";
    public static final String CORRECCION_AUMENTA = "CORRECCION_AUMENTA";
    public static final String CORRECCION_DISMINUYE = "CORRECCION_DISMINUYE";

    @EqualsAndHashCode.Include
    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    /** +1 si suma stock, -1 si lo resta. */
    @Column(name = "sentido", nullable = false)
    private short sentido;

    /** PEDIDO o DEVOLUCION si la operación nace de una solicitud de esa clase; nulo si es manual. */
    @Column(name = "solicitud_requerida", length = 10)
    private String solicitudRequerida;

    /** true = el bodeguero puede elegirlo a mano; false = lo genera el sistema. */
    @Column(name = "seleccionable", nullable = false)
    private boolean seleccionable;

    public boolean esEntrada() {
        return sentido > 0;
    }
}
