package com.smartrdp.backend.catalogo.dto;

import com.smartrdp.backend.catalogo.Proveedor;

public record ProveedorResponse(Long id, String rut, String nombre, boolean activo) {

    public static ProveedorResponse from(Proveedor p) {
        return new ProveedorResponse(p.getId(), p.getRut(), p.getNombre(), p.isActivo());
    }
}
