package com.smartrdp.backend.analitica.dto;

public record ConsumoDto(
        Long id, String nombre,
        Long entradaCantidad, Long entradaValor,
        Long salidaCantidad, Long salidaValor
) {}
