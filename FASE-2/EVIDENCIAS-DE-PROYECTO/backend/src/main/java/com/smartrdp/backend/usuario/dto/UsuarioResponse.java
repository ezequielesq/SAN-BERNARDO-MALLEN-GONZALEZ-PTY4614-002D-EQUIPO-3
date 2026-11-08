package com.smartrdp.backend.usuario.dto;

import com.smartrdp.backend.usuario.Usuario;

public record UsuarioResponse(Long id, String nombre, String email, String rol, boolean activo) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(),
                usuario.getRol().getCodigo(), usuario.isActivo());
    }
}
