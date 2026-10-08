package com.smartrdp.backend.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ddl-auto=update no migra datos ni modifica las restricciones CHECK que Hibernate generó para los
 * enums. Este runner (idempotente) deja la base lista para los valores nuevos de Rol y
 * TipoMovimiento.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class MigracionesIniciales implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        jdbc.execute("ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_rol_check");
        int migrados = jdbc.update("UPDATE usuarios SET rol = 'EMPLEADO' WHERE rol = 'TRABAJADOR'");
        jdbc.execute("ALTER TABLE usuarios ADD CONSTRAINT usuarios_rol_check "
                + "CHECK (rol IN ('ADMIN', 'BODEGUERO', 'EMPLEADO'))");
        if (migrados > 0) {
            log.info("Usuarios migrados de TRABAJADOR a EMPLEADO: {}", migrados);
        }

        for (String tabla : new String[] {"movimientos", "movimientos_aud"}) {
            jdbc.execute("ALTER TABLE " + tabla + " DROP CONSTRAINT IF EXISTS " + tabla + "_tipo_check");
            jdbc.execute("ALTER TABLE " + tabla + " ADD CONSTRAINT " + tabla + "_tipo_check "
                    + "CHECK (tipo IN ('ENTRADA', 'SALIDA', 'SOLICITADO', 'DEVOLUCION'))");
        }

        // El código PTV pasa a ser opcional (los productos normales usan `codigo`); ddl-auto=update
        // no quita un NOT NULL ya existente.
        jdbc.execute("ALTER TABLE productos ALTER COLUMN codigo_ptv DROP NOT NULL");
    }
}
