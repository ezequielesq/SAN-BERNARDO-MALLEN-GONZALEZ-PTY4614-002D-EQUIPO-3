package com.smartrdp.backend.movimiento;

public enum TipoMovimiento {
    ENTRADA(1),
    SALIDA(-1),
    SOLICITADO(-1),
    DEVOLUCION(1);

    private final int sentido;

    TipoMovimiento(int sentido) {
        this.sentido = sentido;
    }

    /** +1 si suma stock, -1 si lo resta. */
    public int getSentido() {
        return sentido;
    }
}
