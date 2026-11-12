package com.smartrdp.backend.flujos;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Deja la base de pruebas limpia y con el esquema recién migrado cada vez que arranca el contexto.
 * Solo existe en las pruebas: producción nunca ejecuta {@code clean}.
 */
@TestConfiguration(proxyBeanMethods = false)
class ConfiguracionBdPruebas {

    @Bean
    FlywayMigrationStrategy limpiarYMigrar() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
