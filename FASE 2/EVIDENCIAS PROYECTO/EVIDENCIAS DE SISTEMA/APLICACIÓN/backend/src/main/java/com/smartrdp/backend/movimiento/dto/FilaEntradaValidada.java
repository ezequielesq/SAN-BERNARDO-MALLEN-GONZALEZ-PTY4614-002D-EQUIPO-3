package com.smartrdp.backend.movimiento.dto;

import java.util.Map;

/** Fila de entrada con sus errores por campo ({@code codigo}, {@code numeroLote}, {@code fechaVencimiento}, ...). */
public record FilaEntradaValidada(FilaEntrada datos, Map<String, String> errores) {}
