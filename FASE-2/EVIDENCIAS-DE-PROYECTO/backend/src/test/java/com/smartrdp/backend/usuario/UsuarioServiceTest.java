package com.smartrdp.backend.usuario;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.security.JwtTokenProvider;
import com.smartrdp.backend.usuario.dto.LoginRequest;
import com.smartrdp.backend.usuario.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock UsuarioRepository usuarioRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    @InjectMocks UsuarioService usuarioService;

    @Test
    void register_whenEmailAlreadyExists_thenThrowsBusinessException() {
        when(usuarioRepository.existsByEmail("jorge@rdp.cl")).thenReturn(true);
        var request = new RegisterRequest("Jorge", "jorge@rdp.cl", "pass123", Rol.BODEGUERO);
        assertThatThrownBy(() -> usuarioService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email ya está registrado");
    }

    @Test
    void register_whenValidData_thenSavesUser() {
        when(usuarioRepository.existsByEmail("jorge@rdp.cl")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("hashed");
        var request = new RegisterRequest("Jorge", "jorge@rdp.cl", "pass123", Rol.BODEGUERO);
        usuarioService.register(request);
        verify(usuarioRepository).save(any(Usuario.class));
    }
}
