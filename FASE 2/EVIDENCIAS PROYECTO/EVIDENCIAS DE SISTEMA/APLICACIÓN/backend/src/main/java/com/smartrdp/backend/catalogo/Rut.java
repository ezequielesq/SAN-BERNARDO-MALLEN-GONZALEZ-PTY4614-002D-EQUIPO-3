package com.smartrdp.backend.catalogo;

import com.smartrdp.backend.exception.BusinessException;

import java.util.Locale;

/** RUT chileno: se acepta con o sin puntos y guion y se guarda normalizado como «12345678-5». */
public final class Rut {

    private Rut() {}

    /** Devuelve el RUT normalizado o lanza {@link BusinessException} sobre el campo {@code rut}. */
    public static String normalizar(String texto) {
        if (texto == null) throw invalido("Ingresa el RUT.");
        String limpio = texto.replace(".", "").replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (!limpio.matches("\\d{7,8}[\\dK]")) {
            throw invalido("El RUT no es válido: usa el formato 12.345.678-5.");
        }
        String cuerpo = limpio.substring(0, limpio.length() - 1);
        String dv = limpio.substring(limpio.length() - 1);
        if (!digitoVerificador(cuerpo).equals(dv)) {
            throw invalido("El RUT no es válido: revisa el dígito verificador.");
        }
        return cuerpo + "-" + dv;
    }

    /** Dígito verificador (módulo 11) de un cuerpo numérico. */
    public static String digitoVerificador(String cuerpo) {
        int suma = 0;
        int peso = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += Character.digit(cuerpo.charAt(i), 10) * peso;
            peso = peso == 7 ? 2 : peso + 1;
        }
        int resto = 11 - (suma % 11);
        return resto == 11 ? "0" : resto == 10 ? "K" : String.valueOf(resto);
    }

    private static BusinessException invalido(String mensaje) {
        return new BusinessException(mensaje, "rut");
    }
}
