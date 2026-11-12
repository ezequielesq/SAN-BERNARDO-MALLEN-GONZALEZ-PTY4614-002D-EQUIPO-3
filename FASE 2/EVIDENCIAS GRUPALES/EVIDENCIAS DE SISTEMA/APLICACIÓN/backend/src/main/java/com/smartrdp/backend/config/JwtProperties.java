package com.smartrdp.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    /** Secreto que estuvo escrito en el historial de Git hasta el 12-10-2026: ya no es secreto. */
    private static final String SECRETO_FILTRADO =
            "c21hcnRyZHAtc2VjcmV0LWtleS1mb3ItaHMtMjU2LXNpZ25pbmctMjAyNi1kdW9jdWM=";

    private String secret;
    private long expirationMs;

    /** Falla al arrancar si el secreto falta o es inseguro: sin esto el error aparecería recién al iniciar sesión. */
    @PostConstruct
    void validarSecreto() {
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalStateException(
                    "Define la variable de entorno SMARTRDP_JWT_SECRET (Base64, de al menos 32 bytes).");
        }
        if (SECRETO_FILTRADO.equals(secret)) {
            throw new IllegalStateException("SMARTRDP_JWT_SECRET es el secreto antiguo que quedó en el historial "
                    + "de Git: cámbialo. Genera uno nuevo con: openssl rand -base64 48");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("SMARTRDP_JWT_SECRET no es Base64 válido. "
                    + "Genera uno con: openssl rand -base64 48");
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("SMARTRDP_JWT_SECRET es demasiado corto: tiene " + bytes.length
                    + " bytes y se necesitan al menos 32. Genera uno con: openssl rand -base64 48");
        }
    }
}
