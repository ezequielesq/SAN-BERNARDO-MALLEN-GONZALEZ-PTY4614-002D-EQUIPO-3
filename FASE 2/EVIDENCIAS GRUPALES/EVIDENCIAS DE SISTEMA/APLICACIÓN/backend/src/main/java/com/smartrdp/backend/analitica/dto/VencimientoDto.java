package com.smartrdp.backend.analitica.dto;

import java.time.LocalDate;

public record VencimientoDto(Long productoId, String nombre, Integer cantidadEnRiesgo,
                              LocalDate fechaVencimiento, boolean vencido) {}
