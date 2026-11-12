package com.smartrdp.backend.auditoria;

/**
 * Autor de la revisión cuando no hay usuario autenticado (peticiones públicas del empleado).
 * Debe establecerse FUERA de la transacción: Envers crea la revisión al confirmarla.
 */
public final class ActorAuditoria {

    private static final ThreadLocal<String> ACTUAL = new ThreadLocal<>();

    private ActorAuditoria() {}

    public static void establecer(String email) {
        ACTUAL.set(email);
    }

    public static String actual() {
        return ACTUAL.get();
    }

    public static void limpiar() {
        ACTUAL.remove();
    }
}
