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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Valida usuario + contraseña de un empleado y limita los intentos fallidos (en memoria). */
@Component
@RequiredArgsConstructor
public class VerificadorIdentidad {

    static final int MAX_FALLOS = 5;
    static final Duration BLOQUEO = Duration.ofMinutes(5);
    static final String MENSAJE_INVALIDO = "Usuario o contraseña incorrectos.";

    private record Intentos(int fallos, Instant bloqueadoHasta) {}

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Map<Long, Intentos> intentos = new ConcurrentHashMap<>();

    public Usuario verificar(Long empleadoId, String password) {
        Instant ahora = Instant.now();

        Usuario usuario = usuarioRepository.findById(empleadoId)
                .filter(u -> u.isActivo() && u.getRol() == Rol.EMPLEADO)
                .orElseThrow(() -> new BusinessException(MENSAJE_INVALIDO));

        // Se reserva el intento de forma atómica ANTES de comparar la contraseña, así las
        // peticiones concurrentes no pueden adivinar más de MAX_FALLOS veces por ráfaga.
        AtomicReference<Intentos> rechazado = new AtomicReference<>();
        intentos.compute(empleadoId, (id, previo) -> {
            if (previo != null && previo.bloqueadoHasta() != null) {
                if (ahora.isBefore(previo.bloqueadoHasta())) {
                    rechazado.set(previo);
                    return previo;
                }
                previo = null; // el bloqueo expiró: se reinicia el contador
            }
            int fallos = (previo == null ? 0 : previo.fallos()) + 1;
            return new Intentos(fallos, fallos >= MAX_FALLOS ? ahora.plus(BLOQUEO) : null);
        });

        Intentos bloqueo = rechazado.get();
        if (bloqueo != null) {
            long segundos = Duration.between(ahora, bloqueo.bloqueadoHasta()).toSeconds();
            long minutos = Math.max(1, (long) Math.ceil(segundos / 60.0));
            throw new BusinessException("Demasiados intentos. Intenta de nuevo en " + minutos
                    + (minutos == 1 ? " minuto." : " minutos."));
        }

        if (!passwordEncoder.matches(password, usuario.getPassword())) {
            throw new BusinessException(MENSAJE_INVALIDO);
        }
        intentos.remove(empleadoId);
        return usuario;
    }
}
