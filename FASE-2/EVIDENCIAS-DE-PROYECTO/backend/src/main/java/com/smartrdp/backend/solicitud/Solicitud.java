package com.smartrdp.backend.solicitud;

import com.smartrdp.backend.shared.BaseEntity;
import com.smartrdp.backend.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.util.ArrayList;
import java.util.List;

/** Pedido (solicitudOrigen nulo) o devolución (solicitudOrigen = el pedido del que se devuelve). */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Audited
@Entity
@Table(name = "solicitudes")
public class Solicitud extends BaseEntity {

    public static final String PEDIDO = "PEDIDO";
    public static final String DEVOLUCION = "DEVOLUCION";

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estado_id", nullable = false)
    private EstadoSolicitud estado;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false)
    private Usuario solicitante;

    /** Quien la resolvió (aprobó o rechazó). Nulo mientras está pendiente. */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bodeguero_id")
    private Usuario bodeguero;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_origen_id")
    private Solicitud solicitudOrigen;

    @Column(name = "observacion", length = 300)
    private String observacion;

    @NotAudited
    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleSolicitud> detalles = new ArrayList<>();

    public boolean esDevolucion() {
        return solicitudOrigen != null;
    }

    public String tipoCodigo() {
        return esDevolucion() ? DEVOLUCION : PEDIDO;
    }
}
