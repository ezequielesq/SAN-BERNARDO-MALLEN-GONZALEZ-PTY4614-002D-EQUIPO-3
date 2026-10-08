package com.smartrdp.backend.usuario;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.security.JwtTokenProvider;
import com.smartrdp.backend.usuario.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    public LoginResponse login(LoginRequest request) {
        // Se resuelve el rol antes de autenticar: un empleado recibe siempre el mismo 422,
        // sin mirar la contraseña, para que el login no sirva de oráculo de contraseñas.
        var existente = usuarioRepository.findByEmailAndActivoTrue(request.email());
        if (existente.isPresent() && existente.get().getRol() == Rol.EMPLEADO) {
            throw new BusinessException(
                    "Los empleados no inician sesión. Usa la vista pública de solicitudes.");
        }
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        var usuario = usuarioRepository.findByEmailAndActivoTrue(request.email()).orElseThrow();
        var userDetails = userDetailsService.loadUserByUsername(request.email());
        var token = jwtTokenProvider.generateToken(userDetails);
        return new LoginResponse(token, usuario.getEmail(), usuario.getNombre(), usuario.getRol());
    }
}
