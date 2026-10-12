package com.smartrdp.backend.analitica.dto;

public record ConsumoDto(
        Long id, String nombre,
        Integer entradaCantidad, Integer entradaValor,
        Integer salidaCantidad, Integer salidaValor
) {}
