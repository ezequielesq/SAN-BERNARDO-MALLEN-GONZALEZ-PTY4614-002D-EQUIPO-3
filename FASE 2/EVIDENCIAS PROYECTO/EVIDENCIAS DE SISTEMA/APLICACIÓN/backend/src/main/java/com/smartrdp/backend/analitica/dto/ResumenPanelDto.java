package com.smartrdp.backend.analitica.dto;

/**
 * Las cinco cifras del panel (la quinta, lotes por vencer, sale de /vencimientos). Los importes son pesos.
 * {@code unidadComun} y las cantidades vienen vacías cuando los productos filtrados mezclan unidades.
 */
public record ResumenPanelDto(
        long consumoNetoValor, Long cantidadNeta, String unidadComun,
        long pedidos, long personas,
        long solicitadoValor, long entregadoValor, Long solicitadoCantidad, Long entregadoCantidad,
        long perdidasValor, long lineasSinCosto
) {}
