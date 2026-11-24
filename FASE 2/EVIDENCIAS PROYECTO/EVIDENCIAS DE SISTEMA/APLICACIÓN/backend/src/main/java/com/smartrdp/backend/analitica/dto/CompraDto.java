package com.smartrdp.backend.analitica.dto;

import java.time.LocalDate;

/** Una compra de un producto: el costo es el del lote que entró. */
public record CompraDto(LocalDate fecha, Integer costo, Integer cantidad) {}
