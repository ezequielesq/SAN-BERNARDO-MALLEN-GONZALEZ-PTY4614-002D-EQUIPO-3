package com.smartrdp.backend.catalogo.dto;

import com.smartrdp.backend.catalogo.CatalogoEntity;

public record CatalogoResponse(Long id, String nombre, boolean activo) {

    public static CatalogoResponse from(CatalogoEntity entidad) {
        return new CatalogoResponse(entidad.getId(), entidad.getNombre(), entidad.isActivo());
    }
}
