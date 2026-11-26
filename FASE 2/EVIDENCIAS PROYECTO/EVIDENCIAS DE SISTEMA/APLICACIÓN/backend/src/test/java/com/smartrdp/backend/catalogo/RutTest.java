package com.smartrdp.backend.catalogo;

import com.smartrdp.backend.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RutTest {

    @Test
    void normalizaConPuntosGuionYEspacios() {
        assertThat(Rut.normalizar("12.345.678-5")).isEqualTo("12345678-5");
        assertThat(Rut.normalizar(" 12345678-5 ")).isEqualTo("12345678-5");
        assertThat(Rut.normalizar("123456785")).isEqualTo("12345678-5");
    }

    @Test
    void aceptaLosTresDigitosVerificadoresPosibles() {
        assertThat(Rut.normalizar("11111111-1")).isEqualTo("11111111-1");
        assertThat(Rut.normalizar("1000005-k")).isEqualTo("1000005-K"); // K en mayúscula
        assertThat(Rut.normalizar("1000030-0")).isEqualTo("1000030-0"); // resto 11 → dígito 0
    }

    @Test
    void rechazaDigitoVerificadorIncorrecto() {
        assertThatThrownBy(() -> Rut.normalizar("12345678-9"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dígito verificador");
    }

    @Test
    void rechazaFormatosInvalidos() {
        for (String malo : new String[] {"", "   ", "abc", "1-9", "123456789012-3", "12.345.678-X", null}) {
            assertThatThrownBy(() -> Rut.normalizar(malo)).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void calculaElDigitoVerificador() {
        assertThat(Rut.digitoVerificador("12345678")).isEqualTo("5");
        assertThat(Rut.digitoVerificador("1000005")).isEqualTo("K");
        assertThat(Rut.digitoVerificador("1000030")).isEqualTo("0");
    }
}
