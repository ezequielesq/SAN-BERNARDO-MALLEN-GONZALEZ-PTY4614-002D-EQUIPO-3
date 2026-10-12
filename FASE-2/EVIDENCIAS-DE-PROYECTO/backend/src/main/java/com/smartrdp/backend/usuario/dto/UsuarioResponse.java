package com.smartrdp.backend.usuario.dto;

import com.smartrdp.backend.usuario.Rol;
import com.smartrdp.backend.usuario.Usuario;

public record UsuarioResponse(Long id, String nombre, String email, Rol rol, boolean activo) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol(), usuario.isActivo());
    }
}
