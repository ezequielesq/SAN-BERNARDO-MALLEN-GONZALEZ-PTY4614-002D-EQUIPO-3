package com.smartrdp.backend.evento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record EventoRequest(
        @NotBlank @Size(max = 120) String titulo,
        @NotNull LocalDate fecha,
        LocalTime hora,
        @Size(max = 500) String descripcion) {}
