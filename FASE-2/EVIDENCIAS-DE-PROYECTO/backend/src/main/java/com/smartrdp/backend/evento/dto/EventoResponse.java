package com.smartrdp.backend.evento.dto;

import com.smartrdp.backend.evento.Evento;

import java.time.LocalDate;
import java.time.LocalTime;

public record EventoResponse(Long id, String titulo, LocalDate fecha, LocalTime hora,
                             String descripcion, String creadoPorNombre) {

    public static EventoResponse from(Evento evento) {
        return new EventoResponse(evento.getId(), evento.getTitulo(), evento.getFecha(), evento.getHora(),
                evento.getDescripcion(), evento.getCreadoPor().getNombre());
    }
}
