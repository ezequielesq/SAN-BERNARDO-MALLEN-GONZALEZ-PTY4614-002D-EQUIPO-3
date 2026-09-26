package com.smartrdp.backend.producto.dto;

public record ProductoResponse(
    Long id,
    String codigoPtb,
    String nombre,
    String categoria,
    String unidadMedida,
    boolean esPerecible,
    Integer stockMinimo,
    Integer stockCritico,
    boolean activo
) {}
