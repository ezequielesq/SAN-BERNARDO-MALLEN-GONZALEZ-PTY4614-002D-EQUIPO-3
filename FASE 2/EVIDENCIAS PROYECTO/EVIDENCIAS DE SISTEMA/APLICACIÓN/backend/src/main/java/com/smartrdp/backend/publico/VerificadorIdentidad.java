package com.smartrdp.backend.publico;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.security.LimitadorIntentos;
import com.smartrdp.backend.usuario.Rol;
import com.smartrdp.backend.usuario.Usuario;
import com.smartrdp.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Valida usuario + contraseña de un empleado y limita los intentos fallidos. */
@Component
@RequiredArgsConstructor
public class VerificadorIdentidad {

    static final String MENSAJE_INVALIDO = "Usuario o contraseña incorrectos.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final LimitadorIntentos<Long> limitador =
            new LimitadorIntentos<>(5, Duration.ofMinutes(5), Duration.ofMinutes(15));

    public Usuario verificar(Long empleadoId, String password) {
        Usuario usuario = usuarioRepository.findById(empleadoId)
                .filter(u -> u.isActivo() && Rol.EMPLEADO.equals(u.getRol().getCodigo()))
                .orElseThrow(() -> new BusinessException(MENSAJE_INVALIDO));

        limitador.reservar(empleadoId);

        if (!passwordEncoder.matches(password, usuario.getPassword())) {
            throw new BusinessException(MENSAJE_INVALIDO);
        }
        limitador.liberar(empleadoId);
        return usuario;
    }
}
