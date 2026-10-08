package com.smartrdp.backend.catalogo.dto;

import com.smartrdp.backend.catalogo.Categoria;

public record CategoriaResponse(Long id, String nombre, boolean activo, boolean requierePtv) {

    public static CategoriaResponse from(Categoria categoria) {
        return new CategoriaResponse(
                categoria.getId(), categoria.getNombre(), categoria.isActivo(), categoria.isRequierePtv());
    }
}
