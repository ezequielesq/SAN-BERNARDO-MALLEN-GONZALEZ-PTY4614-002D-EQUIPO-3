package com.smartrdp.backend.analitica.dto;

/**
 * Precios de compra de un producto a un proveedor: el costo de su última compra del rango, el promedio ponderado
 * por cantidad (un decimal) y cuántas compras fueron.
 */
public record ProveedorPrecioDto(Long proveedorId, String nombre, Integer ultimo, Double promedioPonderado,
                                 int compras) {}
