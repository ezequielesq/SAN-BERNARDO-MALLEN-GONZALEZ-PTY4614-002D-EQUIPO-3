package com.smartrdp.backend.analitica.dto;

import java.util.List;

/**
 * Precios de compra de un producto en un rango. {@code ultimo}, {@code anterior}, {@code variacionPct}, mínimo,
 * máximo y promedios son nulos con menos de dos compras. {@code promedioPonderado} pondera por cantidad. Con un
 * proveedor elegido, {@code compras} y los estadísticos son solo de ese proveedor; {@code porProveedor} compara
 * siempre a todos los proveedores del producto en el rango (los lotes sin proveedor no aparecen).
 */
public record HistorialPreciosDto(
        Long productoId, String nombre, String unidad, List<CompraDto> compras,
        Integer ultimo, Integer anterior, Double variacionPct,
        Integer minimo, Integer maximo, Double promedio, Double promedioPonderado,
        List<ProveedorPrecioDto> porProveedor
) {}
