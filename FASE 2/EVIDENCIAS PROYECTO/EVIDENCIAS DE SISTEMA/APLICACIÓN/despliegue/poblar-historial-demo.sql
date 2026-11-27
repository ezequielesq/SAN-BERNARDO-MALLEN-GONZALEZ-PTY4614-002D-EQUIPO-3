-- Historial de DEMOSTRACION para el panel de analítica (12 semanas hacia atrás desde hoy).
-- Uso (base LOCAL de desarrollo; no ejecutar en producción):
--   psql -h localhost -U <usuario> -d smartrdp -v ON_ERROR_STOP=1 -f poblar-historial-demo.sql
-- Es idempotente a medias: si la categoría «Demo analítica» ya existe, se detiene sin tocar nada.
-- Requiere un usuario con rol BODEGUERO y el esquema con la migración V5 (proveedores). Crea 4 proveedores de
-- ejemplo (si ya existen por RUT, se reutilizan) y asigna uno a cada lote de compra. Solo la harina se compra
-- a dos proveedores (alterna semana a semana) y, cuando la vende «Abarrotes del Sur», cuesta un 7 % menos, para que
-- el panel compare precios; los costos de los demás productos no cambian.
-- Los lotes de inventario inicial no llevan proveedor. Las fechas pasadas se fijan al INSERTAR (created_at):
-- el libro es inmutable por triggers y no admite UPDATE.

BEGIN;

DO $$
DECLARE
    v_bod        bigint;
    v_cat        bigint;
    v_kg         bigint;
    v_un         bigint;
    v_emp        bigint[] := '{}';
    v_prod       bigint[] := '{}';
    v_inicial    bigint[] := '{}';   -- lote grande de inventario inicial por producto (sirve las entregas)
    v_costo      integer[] := ARRAY[3500, 1800, 5200, 2400, 900, 1500];
    v_nombres    text[]    := ARRAY['Harina demo', 'Azúcar demo', 'Aceite demo', 'Arroz demo', 'Servilletas demo', 'Bolsas demo'];
    v_perecible  boolean[] := ARRAY[false, false, true, false, false, false];
    v_lunes      date;
    v_hoy        date := current_date;
    t_inicial    bigint; t_compra bigint; t_merma bigint; t_venc bigint; t_entrega bigint; t_devol bigint;
    e_pend       bigint; e_aprob bigint;
    v_semana     integer;
    v_i          integer;
    v_j          integer;
    v_p          integer;
    v_op         bigint;
    v_lote       bigint;
    v_sol        bigint;
    v_dev        bigint;
    v_fecha      timestamp;
    v_cant       integer;
    v_entregada  integer;
    v_pid        bigint;
    v_prov       bigint[] := '{}';   -- 1 Norte, 2 Andes, 3 Sur, 4 Carnes
    v_rut        text[]   := ARRAY['76123456-0', '77234567-4', '78345678-8', '79456789-1'];
    v_pnombre    text[]   := ARRAY['Distribuidora Norte (demo)', 'Frutas y Verduras Andes (demo)', 'Abarrotes del Sur (demo)', 'Carnes Selectas (demo)'];
    v_pv         bigint;
    v_factor     numeric;
BEGIN
    SELECT u.id INTO v_bod FROM usuarios u JOIN roles r ON r.id = u.rol_id
     WHERE r.codigo = 'BODEGUERO' AND u.activo ORDER BY u.id LIMIT 1;
    IF v_bod IS NULL THEN
        RAISE EXCEPTION 'Falta un usuario con rol BODEGUERO: créalo antes de poblar el historial.';
    END IF;
    IF EXISTS (SELECT 1 FROM categorias WHERE nombre = 'Demo analítica') THEN
        RAISE EXCEPTION 'El historial de demostración ya existe (categoría «Demo analítica»). No se hizo nada.';
    END IF;

    SELECT id INTO t_inicial FROM tipos_operacion WHERE codigo = 'INVENTARIO_INICIAL';
    SELECT id INTO t_compra  FROM tipos_operacion WHERE codigo = 'COMPRA';
    SELECT id INTO t_merma   FROM tipos_operacion WHERE codigo = 'MERMA';
    SELECT id INTO t_venc    FROM tipos_operacion WHERE codigo = 'VENCIMIENTO';
    SELECT id INTO t_entrega FROM tipos_operacion WHERE codigo = 'ENTREGA_PEDIDO';
    SELECT id INTO t_devol   FROM tipos_operacion WHERE codigo = 'DEVOLUCION';
    SELECT id INTO e_pend    FROM estados_solicitud WHERE codigo = 'PENDIENTE';
    SELECT id INTO e_aprob   FROM estados_solicitud WHERE codigo = 'APROBADA';

    -- Lunes de la primera semana: 12 semanas completas antes de la semana actual.
    v_lunes := date_trunc('week', v_hoy)::date - 7 * 12;

    -- Catálogos propios de la demostración
    INSERT INTO categorias (nombre, requiere_ptv) VALUES ('Demo analítica', false) RETURNING id INTO v_cat;
    INSERT INTO unidades_medida (nombre) VALUES ('Kilogramo demo') ON CONFLICT (nombre) DO NOTHING;
    INSERT INTO unidades_medida (nombre) VALUES ('Unidad demo')    ON CONFLICT (nombre) DO NOTHING;
    SELECT id INTO v_kg FROM unidades_medida WHERE nombre = 'Kilogramo demo';
    SELECT id INTO v_un FROM unidades_medida WHERE nombre = 'Unidad demo';

    -- Tres empleados de ejemplo. El valor de «password» no es un hash BCrypt: no pueden iniciar sesión.
    FOR v_i IN 1..3 LOOP
        INSERT INTO usuarios (rol_id, nombre, email, password)
        VALUES ((SELECT id FROM roles WHERE codigo = 'EMPLEADO'), 'Empleado demo ' || v_i,
                'empleado.demo' || v_i || '@example.test', '!sin-acceso')
        ON CONFLICT (email) DO NOTHING;
        v_emp := v_emp || (SELECT id FROM usuarios WHERE email = 'empleado.demo' || v_i || '@example.test');
    END LOOP;

    -- Proveedores de ejemplo (RUT ficticios con dígito verificador válido).
    FOR v_i IN 1..4 LOOP
        INSERT INTO proveedores (rut, nombre) VALUES (v_rut[v_i], v_pnombre[v_i]) ON CONFLICT (rut) DO NOTHING;
        v_prov := v_prov || (SELECT id FROM proveedores WHERE rut = v_rut[v_i]);
    END LOOP;

    -- Productos: los cuatro primeros en kg, los dos últimos por unidad; el aceite es perecible.
    FOR v_p IN 1..6 LOOP
        INSERT INTO productos (categoria_id, unidad_medida_id, nombre, es_perecible, stock_minimo, stock_critico, costo_unitario)
        VALUES (v_cat, CASE WHEN v_p <= 4 THEN v_kg ELSE v_un END, v_nombres[v_p], v_perecible[v_p], 100, 30, v_costo[v_p])
        RETURNING id INTO v_pid;
        v_prod := v_prod || v_pid;

        -- Inventario inicial grande, fechado antes de la primera semana.
        INSERT INTO operaciones (created_at, tipo_id, usuario_id) VALUES (v_lunes::timestamp - interval '1 day' + time '09:00', t_inicial, v_bod)
        RETURNING id INTO v_op;
        INSERT INTO lotes (created_at, producto_id, numero_lote, fecha_vencimiento, costo_unitario)
        VALUES (v_lunes::timestamp - interval '1 day' + time '09:00', v_prod[v_p], 'INI-' || v_p,
                CASE WHEN v_perecible[v_p] THEN v_hoy + 60 END, v_costo[v_p])
        RETURNING id INTO v_lote;
        INSERT INTO movimientos (created_at, operacion_id, lote_id, cantidad) VALUES (v_lunes::timestamp - interval '1 day' + time '09:00', v_op, v_lote, 5000);
        v_inicial := v_inicial || v_lote;
    END LOOP;

    FOR v_semana IN 0..11 LOOP
        -- Compras: una por producto cada semana, con un costo que sube un poco y se mueve según el producto.
        FOR v_p IN 1..6 LOOP
            -- Proveedor de la compra: la harina alterna Norte (semanas pares) y Sur (impares); solo en la harina, Sur cobra un 7 % menos.
            v_pv := CASE v_p WHEN 1 THEN CASE WHEN v_semana % 2 = 0 THEN v_prov[1] ELSE v_prov[3] END
                             WHEN 2 THEN v_prov[3] WHEN 3 THEN v_prov[2] WHEN 4 THEN v_prov[3]
                             WHEN 5 THEN v_prov[1] ELSE v_prov[4] END;
            v_factor := CASE WHEN v_p = 1 AND v_pv = v_prov[3] THEN 0.93 ELSE 1 END;
            v_fecha := (v_lunes + 7 * v_semana)::timestamp + interval '1 day' + time '10:00';
            INSERT INTO operaciones (created_at, tipo_id, usuario_id, nota) VALUES (v_fecha, t_compra, v_bod, 'Compra demo') RETURNING id INTO v_op;
            INSERT INTO lotes (created_at, producto_id, numero_lote, fecha_vencimiento, costo_unitario, proveedor_id)
            VALUES (v_fecha, v_prod[v_p], 'C' || v_semana || '-' || v_p,
                    CASE WHEN v_perecible[v_p] THEN v_fecha::date + 25 + (v_semana % 3) * 10 END,
                    round(v_costo[v_p] * (1 + 0.015 * v_semana + 0.04 * ((v_semana + v_p) % 3)) * v_factor)::integer,
                    v_pv)
            RETURNING id INTO v_lote;
            INSERT INTO movimientos (created_at, operacion_id, lote_id, cantidad) VALUES (v_fecha, v_op, v_lote, 120 + 10 * v_p);
        END LOOP;

        -- Pedidos: tres empleados por semana, cada uno pide dos productos; las entregas salen del lote inicial.
        FOR v_i IN 1..3 LOOP
            v_fecha := (v_lunes + 7 * v_semana)::timestamp + ((v_i + 1) || ' days')::interval + time '11:30';
            INSERT INTO solicitudes (created_at, estado_id, solicitante_id, observacion)
            VALUES (v_fecha, e_pend, v_emp[v_i], 'Pedido demo') RETURNING id INTO v_sol;

            FOR v_j IN 0..1 LOOP
                v_p    := 1 + ((v_semana + v_i + v_j * 2) % 6);
                v_cant := 8 + ((v_semana * 3 + v_i * 5 + v_j * 7) % 15);
                INSERT INTO detalle_solicitudes (created_at, solicitud_id, producto_id, cantidad_solicitada)
                VALUES (v_fecha, v_sol, v_prod[v_p], v_cant);
            END LOOP;

            INSERT INTO operaciones (created_at, tipo_id, usuario_id, solicitud_id)
            VALUES (v_fecha + interval '20 minutes', t_entrega, v_bod, v_sol) RETURNING id INTO v_op;
            FOR v_j IN 0..1 LOOP
                v_p    := 1 + ((v_semana + v_i + v_j * 2) % 6);
                v_cant := 8 + ((v_semana * 3 + v_i * 5 + v_j * 7) % 15);
                -- Cada cuatro pedidos, una entrega parcial (lo entregado es menos de lo pedido).
                v_entregada := CASE WHEN (v_semana + v_i + v_j) % 4 = 0 THEN GREATEST(1, v_cant - 4) ELSE v_cant END;
                INSERT INTO movimientos (created_at, operacion_id, lote_id, cantidad)
                VALUES (v_fecha + interval '20 minutes', v_op, v_inicial[v_p], v_entregada);
            END LOOP;
            UPDATE solicitudes SET estado_id = e_aprob, bodeguero_id = v_bod WHERE id = v_sol;

            -- Cada tres semanas, el primer empleado devuelve una parte de su primer producto.
            IF v_i = 1 AND v_semana % 3 = 0 THEN
                v_p := 1 + ((v_semana + v_i) % 6);
                INSERT INTO solicitudes (created_at, estado_id, solicitante_id, solicitud_origen_id, observacion)
                VALUES (v_fecha + interval '1 day', e_pend, v_emp[v_i], v_sol, 'Devolución demo') RETURNING id INTO v_dev;
                INSERT INTO detalle_solicitudes (created_at, solicitud_id, producto_id, cantidad_solicitada)
                VALUES (v_fecha + interval '1 day', v_dev, v_prod[v_p], 3);
                INSERT INTO operaciones (created_at, tipo_id, usuario_id, solicitud_id)
                VALUES (v_fecha + interval '1 day 15 minutes', t_devol, v_bod, v_dev) RETURNING id INTO v_op;
                INSERT INTO movimientos (created_at, operacion_id, lote_id, cantidad)
                VALUES (v_fecha + interval '1 day 15 minutes', v_op, v_inicial[v_p], 3);
                UPDATE solicitudes SET estado_id = e_aprob, bodeguero_id = v_bod WHERE id = v_dev;
            END IF;
        END LOOP;

        -- Pérdidas: una merma cada semana y un vencimiento cada cuatro, del lote inicial.
        v_fecha := (v_lunes + 7 * v_semana)::timestamp + interval '4 days' + time '16:00';
        v_p := 1 + (v_semana % 6);
        INSERT INTO operaciones (created_at, tipo_id, usuario_id, nota) VALUES (v_fecha, t_merma, v_bod, 'Merma demo') RETURNING id INTO v_op;
        INSERT INTO movimientos (created_at, operacion_id, lote_id, cantidad) VALUES (v_fecha, v_op, v_inicial[v_p], 2 + (v_semana % 4));
        IF v_semana % 4 = 3 THEN
            INSERT INTO operaciones (created_at, tipo_id, usuario_id, nota) VALUES (v_fecha + interval '1 hour', t_venc, v_bod, 'Vencimiento demo') RETURNING id INTO v_op;
            INSERT INTO movimientos (created_at, operacion_id, lote_id, cantidad) VALUES (v_fecha + interval '1 hour', v_op, v_inicial[1 + ((v_semana + 2) % 6)], 4);
        END IF;
    END LOOP;
END $$;

COMMIT;
