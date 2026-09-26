package com.smartrdp.backend.usuario;

import com.smartrdp.backend.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "usuarios")
public class Usuario extends BaseEntity {

    @EqualsAndHashCode.Include
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "password", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;
}
