package com.smartrdp.backend.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CatalogoRequest(@NotBlank @Size(max = 50) String nombre) {}
