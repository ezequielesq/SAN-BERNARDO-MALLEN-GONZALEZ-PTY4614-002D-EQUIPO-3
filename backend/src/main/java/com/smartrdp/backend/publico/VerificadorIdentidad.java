package com.smartrdp.backend.publico;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.usuario.Rol;
import com.smartrdp.backend.usuario.Usuario;
import com.smartrdp.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Valida usuario + contraseña de un empleado y limita los intentos fallidos (en memoria). */
@Component
@RequiredArgsConstructor
public class VerificadorIdentidad {

    static final int MAX_FALLOS = 5;
    static final Duration BLOQUEO = Duration.ofMinutes(5);
    static final String MENSAJE_INVALIDO = "Usuario o contraseña incorrectos.";

    private static final class Intentos {
        int fallos;
        Instant bloqueadoHasta;
    }

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Map<Long, Intentos> intentos = new ConcurrentHashMap<>();

    public Usuario verificar(Long empleadoId, String password) {
        Instant ahora = Instant.now();
        Intentos actual = intentos.get(empleadoId);
        if (actual != null && actual.bloqueadoHasta != null && ahora.isBefore(actual.bloqueadoHasta)) {
            long segundos = Duration.between(ahora, actual.bloqueadoHasta).toSeconds();
            long minutos = Math.max(1, (long) Math.ceil(segundos / 60.0));
            throw new BusinessException("Demasiados intentos. Intenta de nuevo en " + minutos
                    + (minutos == 1 ? " minuto." : " minutos."));
        }

        Optional<Usuario> usuario = usuarioRepository.findById(empleadoId)
                .filter(u -> u.isActivo() && u.getRol() == Rol.EMPLEADO);
        if (usuario.isEmpty()) {
            throw new BusinessException(MENSAJE_INVALIDO);
        }
        if (!passwordEncoder.matches(password, usuario.get().getPassword())) {
            registrarFallo(empleadoId, ahora);
            throw new BusinessException(MENSAJE_INVALIDO);
        }
        intentos.remove(empleadoId);
        return usuario.get();
    }

    private void registrarFallo(Long empleadoId, Instant ahora) {
        intentos.compute(empleadoId, (id, previo) -> {
            Intentos n = previo;
            if (n == null || (n.bloqueadoHasta != null && !ahora.isBefore(n.bloqueadoHasta))) {
                n = new Intentos();
            }
            n.fallos++;
            if (n.fallos >= MAX_FALLOS) {
                n.bloqueadoHasta = ahora.plus(BLOQUEO);
            }
            return n;
        });
    }
}
