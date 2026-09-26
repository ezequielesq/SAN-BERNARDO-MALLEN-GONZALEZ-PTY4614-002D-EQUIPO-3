package com.smartrdp.backend.producto.dto;

import jakarta.validation.constraints.*;

public record ProductoRequest(
    @NotBlank @Size(max = 150) String nombre,
    @NotBlank String categoria,
    @NotBlank String unidadMedida,
    boolean esPerecible,
    @NotBlank @Size(max = 20) String codigoPtb
) {}
