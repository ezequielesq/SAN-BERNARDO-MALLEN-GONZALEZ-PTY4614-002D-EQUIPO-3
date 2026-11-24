package com.smartrdp.backend.analitica.dto;

import java.util.List;

/**
 * Precios de compra de un producto en un rango. {@code ultimo}, {@code anterior}, {@code variacionPct}, mínimo,
 * máximo y promedios son nulos con menos de dos compras. {@code promedioPonderado} pondera por cantidad.
 */
public record HistorialPreciosDto(
        Long productoId, String nombre, String unidad, List<CompraDto> compras,
        Integer ultimo, Integer anterior, Double variacionPct,
        Integer minimo, Integer maximo, Double promedio, Double promedioPonderado
) {}
