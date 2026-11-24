package com.smartrdp.backend.analitica;

import com.smartrdp.backend.exception.BusinessException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Filtro común de todos los bloques del panel. Las fechas se aplican a la fecha de la operación. */
public record FiltroPanel(LocalDate desde, LocalDate hasta, Agrupacion agrupar, Long categoriaId,
                          List<Long> productoIds, Long solicitanteId) {

    public static final int MAXIMO_DIAS = 366;
    public static final int MAXIMO_PRODUCTOS = 50;

    public static FiltroPanel de(LocalDate desde, LocalDate hasta, Agrupacion agrupar, Long categoriaId,
                                 List<Long> productoIds, Long solicitanteId) {
        if (desde.isAfter(hasta)) {
            throw new BusinessException("La fecha «desde» no puede ser posterior a «hasta».", "desde");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS) {
            throw new BusinessException("El rango no puede superar " + MAXIMO_DIAS + " días.", "hasta");
        }
        List<Long> ids = productoIds == null ? List.of() : productoIds.stream().distinct().toList();
        if (ids.size() > MAXIMO_PRODUCTOS) {
            throw new BusinessException("Elige como máximo " + MAXIMO_PRODUCTOS + " productos.", "productoIds");
        }
        return new FiltroPanel(desde, hasta, agrupar == null ? Agrupacion.SEMANA : agrupar, categoriaId, ids,
                solicitanteId);
    }

    public LocalDateTime desdeHora() {
        return desde.atStartOfDay();
    }

    /** Límite superior exclusivo: el día «hasta» entra completo. */
    public LocalDateTime hastaExclusivo() {
        return hasta.plusDays(1).atStartOfDay();
    }
}
