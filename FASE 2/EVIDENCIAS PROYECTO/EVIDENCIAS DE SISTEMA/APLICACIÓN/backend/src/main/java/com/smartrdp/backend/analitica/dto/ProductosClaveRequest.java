package com.smartrdp.backend.analitica.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Lista completa de productos clave del usuario: reemplaza la anterior. Vacía = borrarla. */
public record ProductosClaveRequest(@NotNull List<@NotNull Long> productoIds) {}
