package com.smartrdp.backend.solicitud.dto;

import jakarta.validation.constraints.*;

import java.util.List;

public record SolicitudRequest(@NotEmpty List<Item> items) {
    public record Item(@NotNull Long productoId, @NotNull @Min(1) Integer cantidad) {}
}
