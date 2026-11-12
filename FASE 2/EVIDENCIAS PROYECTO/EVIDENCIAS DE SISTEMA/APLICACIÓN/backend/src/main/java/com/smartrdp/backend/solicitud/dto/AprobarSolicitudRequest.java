package com.smartrdp.backend.solicitud.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Lo que el bodeguero entrega (pedido) o acepta (devolución), por producto. Un producto que no está
 * en la solicitud es un agregado (solo en pedidos). Un producto de la solicitud que no se menciona
 * se entrega/acepta completo. Con cantidad 0 no se entrega.
 */
public record AprobarSolicitudRequest(@Size(max = 200) List<@NotNull @Valid Item> items) {
    /** Las cantidades negativas las rechaza el servicio con su mensaje de negocio (422). */
    public record Item(@NotNull Long productoId, @NotNull @Max(1_000_000) Integer cantidad) {}
}
