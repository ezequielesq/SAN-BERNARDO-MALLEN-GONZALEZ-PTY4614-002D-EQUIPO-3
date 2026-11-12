package com.smartrdp.backend.movimiento.dto;

import java.time.LocalDate;

/** Una fila de la carga de entradas: el producto se identifica por {@code codigo} (PTV o interno). */
public record FilaEntrada(
        Integer fila,
        String codigo,
        Long productoId,
        String productoNombre,
        String numeroLote,
        LocalDate fechaVencimiento,
        Integer cantidad,
        Integer costoUnitario
) {}
