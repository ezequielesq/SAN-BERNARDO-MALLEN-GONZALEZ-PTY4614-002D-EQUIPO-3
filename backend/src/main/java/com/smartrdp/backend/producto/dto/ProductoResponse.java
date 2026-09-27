package com.smartrdp.backend.producto.dto;

public record ProductoResponse(
    Long id,
    String codigoPtb,
    String nombre,
    Long categoriaId,
    String categoriaNombre,
    Long unidadMedidaId,
    String unidadMedidaNombre,
    boolean esPerecible,
    Integer stockMinimo,
    Integer stockCritico,
    boolean activo
) {}
