package com.smartrdp.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String secret;
    private long expirationMs;

    /** Falla al arrancar si el secreto no se definio: sin esto el error apareceria recien al iniciar sesion. */
    @PostConstruct
    void validarSecreto() {
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalStateException(
                    "Define la variable de entorno SMARTRDP_JWT_SECRET (Base64, de al menos 32 bytes).");
        }
    }
}
