# Diseño: Frontend de Movimientos (HU-02, 03, 04a, 04b, 05, 06)

**Fecha:** 2026-09-28
**Autor:** sesión de brainstorming con Ezequiel
**Alcance:** arquitectural (subsistema nuevo, sin flujo existente que modificar)

## Por qué

Es el primer subsistema de bodega que Smart RDP todavía no cubre. El backend (`movimiento`, `Lote`, `EstadoStock`, FEFO) está completo desde hace varias tareas; el frontend no tiene ninguna pantalla. Reemplaza directamente los 4 Excel de control diario (Bar, Abarrotes, Frutas/Verduras, Aseo) — la marcha blanca solo cubre Bar por ahora, pero el modelo y las pantallas no distinguen categoría, así que sirven para las 4 por igual cuando se necesiten.

**Decisión ya tomada con el usuario:** el calendario semanal (pedido en la minuta) es una **vista secundaria** dentro de Movimientos, no la interfaz principal. La pantalla principal es un formulario+lista normal, igual al patrón ya usado en Productos.

## Hallazgo importante que resuelve una duda pendiente

La pregunta abierta de si un código PTV puede tener variantes (sugerido por el sufijo `-24` en `PTV1014030-24` del Excel del Bar) **no requiere ningún cambio de esquema en ninguna de las dos respuestas posibles**: el modelo actual ya es 1 `Producto` = 1 `codigoPtv`. Si Jorge confirma que existen variantes de tamaño/formato con PTV distintos, cada variante se modela como un `Producto` separado — ya funciona hoy sin tocar nada. Este hallazgo no bloquea este plan; la pregunta a Jorge sigue pendiente solo para saber si hay que *crear* esos productos-variante, no para decidir el modelo.

## Estado real del backend (verificado leyendo el código, no de memoria)

- `Movimiento` (`producto`, `tipo` ENTRADA/SALIDA, `cantidad`, `motivo`, `usuario`, `lote`), `Lote` (`producto`, `cantidadOriginal`, `cantidadDisponible`, `fechaVencimiento`, `numeroLote`) — completos.
- `MovimientoService.registrarEntrada/registrarSalida` — completos, con selección FEFO automática y validación de stock insuficiente ya implementadas.
- `MovimientoRepository` **ya tiene** los métodos de consulta necesarios para listar (`findByProductoIdOrderByCreatedAtDesc`, `findByCreatedAtBetween`, `findByCreatedAtBetweenAndTipo`, y un agregado de consumo por fechas ya usado por `AnaliticaService`) — el hueco real es que **ningún controller los expone**. No hace falta escribir queries nuevas desde cero, solo un método de servicio que las combine con filtros opcionales y un endpoint que lo exponga.
- `MovimientoResponse` (DTO actual): `id, productoId, productoNombre, tipo, cantidad, motivo, fecha` — **no incluye quién registró el movimiento**, aunque `Movimiento.usuario` sí se guarda. Se necesita agregar `usuarioEmail` (o nombre) a este DTO para la columna "Usuario" del historial — cambio aditivo, no rompe los dos usos actuales (`registrarEntrada`/`registrarSalida`).
- `getStockActual`/`getEstadoStock` funcionan por un solo `productoId` a la vez (usados por `GET /api/movimientos/productos/{id}/stock`). Para la vista de "Estado de stock" (todos los productos a la vez), llamar este endpoint N veces sería ineficiente — se necesita un endpoint nuevo que calcule el stock de todos los productos activos en una sola consulta agregada (agrupar por producto en vez de una suma por producto).
- `getAlertasVencimiento(int diasUmbral)` ya existe y funciona, reutilizable tal cual.

## Backend — cambios necesarios

1. **`GET /api/movimientos?productoId=&desde=&hasta=`** (todos opcionales; `desde`/`hasta` sin especificar → últimos 30 días por defecto, para no cargar todo el historial). Nuevo método en `MovimientoService.listar(...)`, reutilizando los métodos de repositorio existentes más, como máximo, 1-2 métodos derivados nuevos combinando `productoId` + rango de fechas (no una query desde cero).
2. **`GET /api/movimientos/stock`** → `List<StockStatusResponse>` con el stock/estado de **todos** los productos activos en una sola consulta agregada (agrupar por producto), no N llamadas al endpoint existente de un solo producto.
3. **Agregar `usuarioEmail: String` a `MovimientoResponse`** (nullable, ya que `Movimiento.usuario` es opcional).
4. Los 3 endpoints existentes (`entradas`, `salidas`, `productos/{id}/stock`, `alertas/vencimiento`) no cambian.

## Frontend — estructura

Nueva feature `frontend/src/app/features/movimientos/`, cargada de forma perezosa (`loadChildren`), rol ADMIN+BODEGUERO para registrar entradas/salidas (mismo patrón que Productos), TRABAJADOR solo puede ver. Rutas:

- `/movimientos` (historial + calendario, pantalla principal)
- `/movimientos/entradas/nueva`, `/movimientos/salidas/nueva` (formularios)
- `/movimientos/stock` (estado de stock)
- `/movimientos/alertas` (vencimientos)

Se agrega un grupo "Movimientos" a `NAVEGACION` en `core/layout/navegacion.ts`, siguiendo el mismo patrón que el grupo "Administración" ya existente.

### Vista 1: Historial (`movimiento-lista`)

Tabla: Fecha, Producto, Tipo (badge), Cantidad, Motivo, Usuario. Filtros: producto (select), rango de fechas (por defecto últimos 30 días). Botones "Registrar entrada" / "Registrar salida". Paginación cliente, mismo componente `Paginacion` ya construido para Productos. Pestaña secundaria: calendario semanal.

### Vista 2: Calendario semanal (pestaña de la Vista 1)

Grilla CSS de 7 columnas (adaptada del patrón `citacion-calendario-vista` de SDI, que es una grilla hecha a mano, sin librería — visto en la investigación de esa referencia), navegación semana anterior/siguiente. Cada día: punto verde si tuvo alguna entrada ese día, punto rojo si tuvo alguna salida (ambos si tuvo las dos). Clic en un día filtra la tabla de historial a esa fecha exacta. Reutiliza el mismo `GET /api/movimientos?desde=&hasta=` de la Vista 1 (la semana visible), sin endpoint nuevo.

### Vista 3: Formularios de registro (`movimiento-formulario-entrada`, `movimiento-formulario-salida`)

Entrada: producto (select), cantidad (entero positivo), fecha de vencimiento (solo visible/relevante si el producto seleccionado tiene `esPerecible = true`), N° de lote (opcional, texto libre), motivo (opcional). Salida: producto, cantidad, motivo (obligatorio). Ambos redirigen al historial al guardar, con notificación de éxito (mismo patrón que Productos: `NotificacionService.exito(...)`, alertify por debajo).

### Vista 4: Estado de stock (`stock-lista`)

Tabla: Producto, Categoría, Stock actual, Estado (`<app-estado-badge>`, componente ya existente con los 4 estados), Próximo vencimiento. Sin formulario — es de solo lectura, alimentada por `GET /api/movimientos/stock`.

### Vista 5: Alertas de vencimiento (`alertas-lista`)

Lista simple de lotes por vencer, alimentada por `GET /api/movimientos/alertas/vencimiento?dias=` (ya existe), con el umbral de días como filtro (por defecto 7).

## Fuera de alcance de este plan

- HU-07/08 (Solicitudes) — depende de Movimientos (genera salidas), se planifica después.
- HU-09 a 13 (Analítica) — reportes de consumo; el agregado de consumo por fechas ya existe en el repositorio y quedó identificado, pero construir esas pantallas es un plan aparte.
- Confirmar con Jorge si existen variantes de PTV con sufijo (no bloquea este plan, ver hallazgo arriba).
- Cambiar el DTO `getAlertasVencimiento` (hoy sintetiza un `Movimiento` falso para reutilizar `MovimientoResponse` — funciona, pero un `AlertaVencimientoResponse` dedicado sería más limpio; no se toca en este plan).
