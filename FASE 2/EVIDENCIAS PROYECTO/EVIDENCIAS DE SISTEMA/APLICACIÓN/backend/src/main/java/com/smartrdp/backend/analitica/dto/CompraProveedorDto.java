package com.smartrdp.backend.analitica.dto;

/**
 * Lo comprado a un proveedor en el rango: {@code valor} a costo de lote, {@code cantidad} y número de líneas de
 * compra. Los lotes sin proveedor se agrupan con {@code proveedorId} nulo y el nombre «Sin proveedor».
 */
public record CompraProveedorDto(Long proveedorId, String nombre, long valor, long cantidad, int compras) {}
