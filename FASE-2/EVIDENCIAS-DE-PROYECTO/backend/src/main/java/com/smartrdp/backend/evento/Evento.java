package com.smartrdp.backend.evento;

import com.smartrdp.backend.shared.BaseEntity;
import com.smartrdp.backend.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/** Evento de la agenda compartida de los bodegueros. Hora nula = todo el día. */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Entity
@Table(name = "eventos")
public class Evento extends BaseEntity {

    @Column(name = "titulo", nullable = false, length = 120)
    private String titulo;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora")
    private LocalTime hora;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por", nullable = false, updatable = false)
    private Usuario creadoPor;
}
