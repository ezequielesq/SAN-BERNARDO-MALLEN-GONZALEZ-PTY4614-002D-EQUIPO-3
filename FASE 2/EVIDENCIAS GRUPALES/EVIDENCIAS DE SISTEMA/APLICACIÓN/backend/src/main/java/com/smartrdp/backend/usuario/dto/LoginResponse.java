package com.smartrdp.backend.usuario.dto;

public record LoginResponse(String token, String email, String nombre, String rol) {}
