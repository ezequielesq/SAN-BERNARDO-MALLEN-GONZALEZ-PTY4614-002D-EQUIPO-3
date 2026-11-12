package com.smartrdp.backend.producto.dto;

public record ProductoResponse(
    Long id,
    String codigoPtv,
    String codigo,
    String nombre,
    Long categoriaId,
    String categoriaNombre,
    Long unidadMedidaId,
    String unidadMedidaNombre,
    boolean esPerecible,
    Integer stockMinimo,
    Integer stockCritico,
    Integer costoUnitario,
    boolean activo
) {}
