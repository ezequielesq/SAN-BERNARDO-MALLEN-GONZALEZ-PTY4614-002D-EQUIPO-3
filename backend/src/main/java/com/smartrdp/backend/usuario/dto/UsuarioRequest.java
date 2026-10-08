package com.smartrdp.backend.usuario.dto;

import com.smartrdp.backend.usuario.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioRequest(
        @NotBlank(message = "Ingresa el nombre.") @Size(max = 100) String nombre,
        @NotBlank(message = "Ingresa un correo válido.")
        @Email(message = "Ingresa un correo válido.")
        @Size(max = 150) String email,
        @NotBlank(message = "Ingresa una contraseña de al menos 6 caracteres.")
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres.") String password,
        @NotNull(message = "Elige un rol.") Rol rol
) {}
