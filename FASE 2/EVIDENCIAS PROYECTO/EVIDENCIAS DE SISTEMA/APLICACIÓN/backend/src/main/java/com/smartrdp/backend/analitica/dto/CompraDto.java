package com.smartrdp.backend.analitica.dto;

import java.time.LocalDate;

/** Una compra de un producto: el costo es el del lote que entró y el proveedor, aquel al que se compró. */
public record CompraDto(LocalDate fecha, Integer costo, Integer cantidad, String proveedorNombre) {}
