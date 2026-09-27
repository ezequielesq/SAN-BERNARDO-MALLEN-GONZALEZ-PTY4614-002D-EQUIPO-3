package com.smartrdp.backend.producto;

import com.smartrdp.backend.catalogo.Categoria;
import com.smartrdp.backend.catalogo.UnidadMedida;
import com.smartrdp.backend.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "productos")
public class Producto extends BaseEntity {

    @EqualsAndHashCode.Include
    @Column(name = "codigo_ptb", nullable = false, unique = true, length = 20)
    private String codigoPtb;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_medida_id", nullable = false)
    private UnidadMedida unidadMedida;

    @Column(name = "es_perecible", nullable = false)
    private boolean esPerecible;

    @Column(name = "stock_minimo")
    private Integer stockMinimo = 0;

    @Column(name = "stock_critico")
    private Integer stockCritico = 0;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;
}
