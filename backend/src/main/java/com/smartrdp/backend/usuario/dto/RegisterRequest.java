package com.smartrdp.backend.usuario.dto;

import com.smartrdp.backend.usuario.Rol;
import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank @Size(max = 100) String nombre,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 6) String password,
    @NotNull Rol rol
) {}
