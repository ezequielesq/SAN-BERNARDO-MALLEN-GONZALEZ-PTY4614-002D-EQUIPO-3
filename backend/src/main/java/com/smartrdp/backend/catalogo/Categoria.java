package com.smartrdp.backend.catalogo;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Entity
@Table(name = "categorias")
public class Categoria extends CatalogoEntity {

    public Categoria(String nombre) {
        setNombre(nombre);
    }
}
