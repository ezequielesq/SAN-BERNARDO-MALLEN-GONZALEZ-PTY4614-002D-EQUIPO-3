package com.smartrdp.backend.analitica.dto;

/** Importe en pesos y, si todos los productos comparten unidad, la cantidad. */
public record ImporteDto(long valor, Long cantidad) {}
