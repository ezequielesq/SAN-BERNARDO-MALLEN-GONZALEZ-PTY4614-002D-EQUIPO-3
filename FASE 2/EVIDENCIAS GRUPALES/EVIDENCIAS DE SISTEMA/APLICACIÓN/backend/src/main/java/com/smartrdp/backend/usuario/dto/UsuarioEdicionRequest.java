package com.smartrdp.backend.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioEdicionRequest(
        @NotBlank(message = "Ingresa el nombre.") @Size(max = 100) String nombre,
        @NotBlank(message = "Ingresa un correo válido.")
        @Email(message = "Ingresa un correo válido.")
        @Size(max = 150) String email,
        @NotBlank(message = "Elige un rol.") String rol,
        String password
) {}
