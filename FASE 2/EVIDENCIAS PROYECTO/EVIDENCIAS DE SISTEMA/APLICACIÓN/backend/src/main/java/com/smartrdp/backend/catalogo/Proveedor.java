package com.smartrdp.backend.catalogo;

import com.smartrdp.backend.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A quién se le compra. El RUT se guarda normalizado («12345678-5»). Se deshabilita, nunca se borra. */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Entity
@Table(name = "proveedores")
public class Proveedor extends BaseEntity {

    @Column(name = "rut", nullable = false, unique = true, length = 12)
    private String rut;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    public Proveedor(String rut, String nombre) {
        this.rut = rut;
        this.nombre = nombre;
    }
}
