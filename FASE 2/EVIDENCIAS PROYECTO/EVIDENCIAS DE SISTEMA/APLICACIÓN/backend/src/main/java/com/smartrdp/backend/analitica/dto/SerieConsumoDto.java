package com.smartrdp.backend.analitica.dto;

import java.util.List;

public record SerieConsumoDto(String unidadComun, List<PuntoConsumoDto> puntos) {}
