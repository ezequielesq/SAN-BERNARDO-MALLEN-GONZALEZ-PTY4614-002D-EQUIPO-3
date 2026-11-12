package com.smartrdp.backend.security;

import com.smartrdp.backend.exception.BusinessException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Limita los intentos de autenticación por clave (en memoria).
 *
 * <p>El intento se reserva de forma atómica ANTES de comparar la contraseña: las peticiones concurrentes
 * no pueden adivinar más de {@code maxFallos} veces por ráfaga. Un éxito llama a {@link #liberar}. Los
 * intentos viejos (fuera de la ventana) se olvidan, y el mapa tiene un tope para que claves arbitrarias
 * enviadas por un atacante no agoten la memoria: superado el tope, las claves nuevas dejan de contarse
 * hasta que expiren las anteriores.
 */
public final class LimitadorIntentos<K> {

    private record Estado(int fallos, Instant ultimo, Instant bloqueadoHasta) {}

    private static final int MAX_ENTRADAS = 10_000;

    private final int maxFallos;
    private final Duration bloqueo;
    private final Duration ventana;
    private final Map<K, Estado> estados = new ConcurrentHashMap<>();

    public LimitadorIntentos(int maxFallos, Duration bloqueo, Duration ventana) {
        this.maxFallos = maxFallos;
        this.bloqueo = bloqueo;
        this.ventana = ventana;
    }

    /** Cuenta un intento para la clave; lanza {@link BusinessException} si está bloqueada. */
    public void reservar(K clave) {
        Instant ahora = Instant.now();
        AtomicReference<Estado> rechazado = new AtomicReference<>();

        if (estados.size() >= MAX_ENTRADAS) {
            purgar(ahora);
        }
        boolean hayCupo = estados.size() < MAX_ENTRADAS;

        estados.compute(clave, (k, previo) -> {
            if (previo == null && !hayCupo) {
                return null; // tope alcanzado: no se cuentan claves nuevas
            }
            if (previo != null && previo.bloqueadoHasta() != null) {
                if (ahora.isBefore(previo.bloqueadoHasta())) {
                    rechazado.set(previo);
                    return previo;
                }
                previo = null; // el bloqueo expiró: se reinicia el contador
            } else if (previo != null && ahora.isAfter(previo.ultimo().plus(ventana))) {
                previo = null; // los intentos viejos no cuentan
            }
            int fallos = (previo == null ? 0 : previo.fallos()) + 1;
            return new Estado(fallos, ahora, fallos >= maxFallos ? ahora.plus(bloqueo) : null);
        });

        Estado bloqueado = rechazado.get();
        if (bloqueado != null) {
            long segundos = Duration.between(ahora, bloqueado.bloqueadoHasta()).toSeconds();
            long minutos = Math.max(1, (long) Math.ceil(segundos / 60.0));
            throw new BusinessException("Demasiados intentos. Intenta de nuevo en " + minutos
                    + (minutos == 1 ? " minuto." : " minutos."));
        }
    }

    /** Autenticación correcta: se olvidan los intentos de la clave. */
    public void liberar(K clave) {
        estados.remove(clave);
    }

    private void purgar(Instant ahora) {
        estados.entrySet().removeIf(e -> {
            Estado s = e.getValue();
            return s.bloqueadoHasta() != null
                    ? ahora.isAfter(s.bloqueadoHasta())
                    : ahora.isAfter(s.ultimo().plus(ventana));
        });
    }
}
