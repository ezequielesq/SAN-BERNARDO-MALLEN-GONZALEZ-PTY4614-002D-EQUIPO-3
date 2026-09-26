package com.smartrdp.backend.producto;

import com.smartrdp.backend.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Audited
@Entity
@Table(name = "productos")
public class Producto extends BaseEntity {

    @EqualsAndHashCode.Include
    @Column(name = "codigo_ptb", nullable = false, unique = true, length = 20)
    private String codigoPtb;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "categoria", nullable = false, length = 50)
    private String categoria;

    @Column(name = "unidad_medida", nullable = false, length = 20)
    private String unidadMedida;

    @Column(name = "es_perecible", nullable = false)
    private boolean esPerecible;

    @Column(name = "stock_minimo")
    private Integer stockMinimo = 0;

    @Column(name = "stock_critico")
    private Integer stockCritico = 0;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;
}
