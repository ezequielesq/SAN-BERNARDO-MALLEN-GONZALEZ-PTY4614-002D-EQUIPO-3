package com.smartrdp.backend.catalogo;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Entity
@Table(name = "unidades_medida")
public class UnidadMedida extends CatalogoEntity {

    public UnidadMedida(String nombre) {
        setNombre(nombre);
    }
}
