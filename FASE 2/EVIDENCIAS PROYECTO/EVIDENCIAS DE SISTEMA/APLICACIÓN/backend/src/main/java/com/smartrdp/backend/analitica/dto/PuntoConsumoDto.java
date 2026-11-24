package com.smartrdp.backend.analitica.dto;

import java.time.LocalDate;

/** Un período de la serie: {@code inicio} es el primer día del día, de la semana (lunes) o del mes. */
public record PuntoConsumoDto(LocalDate inicio, long consumoValor, Long consumoCantidad, long perdidasValor) {}
