package com.smartrdp.backend.usuario.dto;

import com.smartrdp.backend.usuario.Rol;

public record LoginResponse(String token, String email, String nombre, Rol rol) {}
