package com.smartrdp.backend.analitica.dto;

import java.util.List;

public record PedidosPorPersonaDto(String unidadComun, List<FilaPersonaDto> filas) {}
