package com.smartrdp.backend.usuario;

import com.smartrdp.backend.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Catálogo de roles. Los códigos son los que viajan en la API y en los tokens. */
@Getter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Immutable
@Entity
@Table(name = "roles")
public class Rol extends BaseEntity {

    public static final String ADMIN = "ADMIN";
    public static final String BODEGUERO = "BODEGUERO";
    public static final String EMPLEADO = "EMPLEADO";

    @EqualsAndHashCode.Include
    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 50)
    private String nombre;

    @Column(name = "activo", nullable = false)
    private boolean activo;
}
