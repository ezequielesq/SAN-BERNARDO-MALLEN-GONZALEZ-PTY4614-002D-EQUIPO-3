package com.smartrdp.backend.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Entity
@Table(name = "categorias")
public class Categoria extends CatalogoEntity {

    /** Los productos de esta categoría se identifican con código PTV (alfanumérico). */
    @Getter
    @Setter
    @Column(name = "requiere_ptv", nullable = false, columnDefinition = "boolean default false")
    private boolean requierePtv;

    public Categoria(String nombre) {
        setNombre(nombre);
    }
}
