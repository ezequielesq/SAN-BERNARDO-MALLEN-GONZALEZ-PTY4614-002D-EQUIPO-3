package com.smartrdp.backend.catalogo;

import com.smartrdp.backend.exception.BusinessException;

import java.util.Locale;

/**
 * RUT chileno: se acepta con o sin puntos y guion y se guarda normalizado como «12345678-5». Los ceros a la
 * izquierda del cuerpo se ignoran («01234567-4» es «1234567-4») y el cuerpo debe tener 7 u 8 dígitos sin contar
 * esos ceros, por lo que el todo ceros no es un RUT.
 */
public final class Rut {

    private Rut() {}

    /** Devuelve el RUT normalizado o lanza {@link BusinessException} sobre el campo {@code rut}. */
    public static String normalizar(String texto) {
        if (texto == null) throw invalido("Ingresa el RUT.");
        String limpio = texto.replace(".", "").replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (!limpio.matches("\\d+[\\dK]")) {
            throw invalido("El RUT no es válido: usa el formato 12.345.678-5.");
        }
        String cuerpo = limpio.substring(0, limpio.length() - 1).replaceFirst("^0+", "");
        if (!cuerpo.matches("[1-9]\\d{6,7}")) {
            throw invalido("El RUT no es válido: usa el formato 12.345.678-5.");
        }
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
