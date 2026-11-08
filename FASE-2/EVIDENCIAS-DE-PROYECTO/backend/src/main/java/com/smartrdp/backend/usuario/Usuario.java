package com.smartrdp.backend.usuario;

import com.smartrdp.backend.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Audited
@Table(name = "usuarios")
public class Usuario extends BaseEntity {

    @EqualsAndHashCode.Include
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @NotAudited
    @Column(name = "password", nullable = false)
    private String password;

    // EAGER: es un catálogo de 3 filas y open-in-view está desactivado.
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;
}
