package com.smartrdp.backend.solicitud.dto;

import java.util.List;

public record AprobarSolicitudRequest(List<ItemEntregado> items) {
    // cantidad entregada por detalle; si null usa la solicitada
    public record ItemEntregado(Long detalleId, Integer cantidadEntregada) {}
}
