package com.smartrdp.backend.solicitud.dto;

import java.util.List;

public record AprobarSolicitudRequest(List<ItemEntregado> items, List<ItemAgregado> agregados) {
    // cantidad entregada por detalle; si null usa la solicitada
    public record ItemEntregado(Long detalleId, Integer cantidadEntregada) {}

    // producto que el bodeguero entrega además (o en lugar) de lo solicitado; solo en pedidos
    public record ItemAgregado(Long productoId, Integer cantidad) {}
}
