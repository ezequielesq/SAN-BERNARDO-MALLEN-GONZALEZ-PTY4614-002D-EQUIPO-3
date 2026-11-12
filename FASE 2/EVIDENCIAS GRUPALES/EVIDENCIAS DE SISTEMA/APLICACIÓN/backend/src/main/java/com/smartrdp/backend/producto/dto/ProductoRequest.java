package com.smartrdp.backend.producto.dto;

import jakarta.validation.constraints.*;

public record ProductoRequest(
    @Size(max = 20) String codigoPtv,
    @Size(max = 20) String codigo,
    @NotBlank @Size(max = 150) String nombre,
    @NotNull Long categoriaId,
    @NotNull Long unidadMedidaId,
    boolean esPerecible,
    @NotNull @Min(0) Integer stockMinimo,
    @NotNull @Min(0) Integer stockCritico,
    @NotNull @Min(0) Integer costoUnitario
) {}
