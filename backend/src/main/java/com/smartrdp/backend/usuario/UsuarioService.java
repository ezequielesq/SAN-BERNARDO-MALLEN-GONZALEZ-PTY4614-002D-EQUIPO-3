package com.smartrdp.backend.usuario;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.security.JwtTokenProvider;
import com.smartrdp.backend.usuario.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Transactional
    public void register(RegisterRequest request) {
        if (request.rol() == Rol.ADMIN) {
            throw new BusinessException("No se puede registrar un usuario con rol ADMIN");
        }
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new BusinessException("El email ya está registrado");
        }
        var usuario = new Usuario();
        usuario.setNombre(request.nombre());
        usuario.setEmail(request.email());
        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setRol(request.rol());
        usuarioRepository.save(usuario);
    }

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        var usuario = usuarioRepository.findByEmailAndActivoTrue(request.email()).orElseThrow();
        if (usuario.getRol() == Rol.EMPLEADO) {
            throw new BusinessException(
                    "Los empleados no inician sesión. Usa la vista pública de solicitudes.");
        }
        var userDetails = userDetailsService.loadUserByUsername(request.email());
        var token = jwtTokenProvider.generateToken(userDetails);
        return new LoginResponse(token, usuario.getEmail(), usuario.getNombre(), usuario.getRol());
    }
}
