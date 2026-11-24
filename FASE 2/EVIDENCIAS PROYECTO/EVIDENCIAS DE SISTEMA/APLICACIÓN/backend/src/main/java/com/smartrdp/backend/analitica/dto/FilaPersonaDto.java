package com.smartrdp.backend.analitica.dto;

public record FilaPersonaDto(Long usuarioId, String nombre, long pedidos, ImporteDto solicitado,
                             ImporteDto entregado, ImporteDto devuelto, ImporteDto neto) {}
