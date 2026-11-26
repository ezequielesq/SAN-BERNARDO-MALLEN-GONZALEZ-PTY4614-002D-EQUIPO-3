package com.smartrdp.backend.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProveedorRequest(@NotBlank @Size(max = 20) String rut, @NotBlank @Size(max = 100) String nombre) {}
