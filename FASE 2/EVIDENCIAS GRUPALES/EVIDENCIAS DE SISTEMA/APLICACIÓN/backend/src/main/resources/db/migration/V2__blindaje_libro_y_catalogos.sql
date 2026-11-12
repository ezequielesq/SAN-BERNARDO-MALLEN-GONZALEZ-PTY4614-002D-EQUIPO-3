-- V2 · Blindaje del libro y de los catálogos que le dan significado.
--
-- 1. tipos_operacion.sentido / codigo / solicitud_requerida definen cómo se lee TODO el historial
--    (cantidad * sentido). Cambiarlos reescribiría el stock pasado, así que quedan inmutables.
--    nombre y seleccionable siguen siendo editables.
-- 2. TRUNCATE no dispara los triggers por fila (INM), así que se guarda aparte. Solo se permite si la
--    sesión lo declara de forma explícita (SET smartrdp.permitir_truncate = 'on'), que es lo que hace
--    el script de repoblado de desarrollo.

CREATE FUNCTION fn_tipos_operacion_semantica_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.codigo IS DISTINCT FROM OLD.codigo
       OR NEW.sentido IS DISTINCT FROM OLD.sentido
       OR NEW.solicitud_requerida IS DISTINCT FROM OLD.solicitud_requerida THEN
        RAISE EXCEPTION 'El código, el sentido y la exigencia de solicitud del tipo de operación % no se modifican: cambiarían la lectura del historial.',
            OLD.codigo;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_tipos_operacion_semantica BEFORE UPDATE ON tipos_operacion
    FOR EACH ROW EXECUTE FUNCTION fn_tipos_operacion_semantica_inmutable();

CREATE FUNCTION fn_truncate_protegido() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF COALESCE(current_setting('smartrdp.permitir_truncate', true), 'off') <> 'on' THEN
        RAISE EXCEPTION 'TRUNCATE de % bloqueado: el libro y los catálogos no se vacían (solo el repoblado de desarrollo lo habilita).',
            TG_TABLE_NAME;
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_operaciones_truncate BEFORE TRUNCATE ON operaciones
    FOR EACH STATEMENT EXECUTE FUNCTION fn_truncate_protegido();
CREATE TRIGGER trg_movimientos_truncate BEFORE TRUNCATE ON movimientos
    FOR EACH STATEMENT EXECUTE FUNCTION fn_truncate_protegido();
CREATE TRIGGER trg_lotes_truncate BEFORE TRUNCATE ON lotes
    FOR EACH STATEMENT EXECUTE FUNCTION fn_truncate_protegido();
CREATE TRIGGER trg_solicitudes_truncate BEFORE TRUNCATE ON solicitudes
    FOR EACH STATEMENT EXECUTE FUNCTION fn_truncate_protegido();
CREATE TRIGGER trg_detalle_solicitudes_truncate BEFORE TRUNCATE ON detalle_solicitudes
    FOR EACH STATEMENT EXECUTE FUNCTION fn_truncate_protegido();
CREATE TRIGGER trg_tipos_operacion_truncate BEFORE TRUNCATE ON tipos_operacion
    FOR EACH STATEMENT EXECUTE FUNCTION fn_truncate_protegido();
CREATE TRIGGER trg_estados_solicitud_truncate BEFORE TRUNCATE ON estados_solicitud
    FOR EACH STATEMENT EXECUTE FUNCTION fn_truncate_protegido();
CREATE TRIGGER trg_roles_truncate BEFORE TRUNCATE ON roles
    FOR EACH STATEMENT EXECUTE FUNCTION fn_truncate_protegido();
