# Frontend de Movimientos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir el frontend completo del subsistema de Movimientos (HU-02, 03, 04a, 04b, 05, 06): registrar entradas/salidas de stock, ver el historial con un calendario semanal, ver el estado de stock de todos los productos y las alertas de vencimiento — reemplazando los 4 Excel de control diario del restaurante.

**Architecture:** El backend de Movimientos ya existe casi completo (entidades, FEFO, validaciones); se agregan 2 endpoints de lectura que faltan (listado con filtros, stock agregado de todos los productos) reutilizando queries de repositorio que ya existían. El frontend es una nueva feature `features/movimientos/` con 5 componentes standalone, siguiendo exactamente los mismos patrones ya usados en `features/productos/` (signals, `httpResource` para lecturas, `HttpClient` para escrituras, `NotificacionService`/`ConfirmacionService` para feedback, `EstadoBadge`/`Paginacion`/`EstadoVista` reutilizados sin cambios).

**Tech Stack:** Spring Boot 4.1.1 / Java 21 (backend), Angular 22 standalone + signals + W3.CSS 5.01 (frontend) — mismo stack que el resto del proyecto, sin dependencias nuevas.

**Spec:** `docs/superpowers/specs/2026-09-28-movimientos-frontend-design.md`

## Global Constraints

- Roles: ADMIN y BODEGUERO pueden registrar entradas/salidas; TRABAJADOR solo puede ver (mismo patrón que Productos, `rolGuard(['ADMIN', 'BODEGUERO'])` en las rutas de escritura).
- Sin dependencias nuevas, ni backend ni frontend.
- Sin tests automatizados (decisión ya establecida en el proyecto) — cada tarea se verifica con `npm run build`/`npx eslint` (frontend) y `./mvnw -q compile` (backend) más verificación manual.
- Iconos siempre con texto visible, nunca icono solo.
- Paleta de color ya establecida: `w3-cobalt` (primario), `w3-crimson` (peligro/salida), `w3-emerald` (éxito/entrada), `w3-asphalt` (barras), `w3-warning` (edición), `w3-info` (exportar/utilidad) — reutilizar estos significados ya definidos, no inventar nuevos.
- Commits en `develop`, sin trailer `Co-Authored-By`.
- `angular-guidelines`: `OnPush` en todo componente, `input()`/`output()`, `inject()`, sin `any`, control de flujo nativo con `track`.

---

## Task 1: Backend — endpoint de listado de movimientos con filtros

**Files:**
- Modify: `backend/src/main/java/com/smartrdp/backend/movimiento/dto/MovimientoResponse.java`
- Modify: `backend/src/main/java/com/smartrdp/backend/movimiento/MovimientoRepository.java`
- Modify: `backend/src/main/java/com/smartrdp/backend/movimiento/MovimientoService.java`
- Modify: `backend/src/main/java/com/smartrdp/backend/movimiento/MovimientoController.java`

**Interfaces:**
- Produces: `MovimientoService.listar(Long productoId, LocalDate desde, LocalDate hasta): List<MovimientoResponse>`.
- Produces: `GET /api/movimientos?productoId=&desde=&hasta=` (todos los parámetros opcionales).
- Consumes (Task 4): el frontend llama a este endpoint tanto para el historial como para el calendario semanal.

- [ ] **Paso 1: Agregar `usuarioEmail` a `MovimientoResponse`**

Reemplazar el contenido completo de `backend/src/main/java/com/smartrdp/backend/movimiento/dto/MovimientoResponse.java`:

```java
package com.smartrdp.backend.movimiento.dto;

import com.smartrdp.backend.movimiento.TipoMovimiento;

import java.time.LocalDateTime;

public record MovimientoResponse(
        Long id,
        Long productoId,
        String productoNombre,
        TipoMovimiento tipo,
        Integer cantidad,
        String motivo,
        LocalDateTime fecha,
        String usuarioEmail
) {}
```

- [ ] **Paso 2: Actualizar `toResponse` en `MovimientoService`**

En `backend/src/main/java/com/smartrdp/backend/movimiento/MovimientoService.java`, reemplazar el método privado `toResponse` (al final de la clase):

```java
    private MovimientoResponse toResponse(Movimiento m) {
        return new MovimientoResponse(
                m.getId(),
                m.getProducto().getId(),
                m.getProducto().getNombre(),
                m.getTipo(),
                m.getCantidad(),
                m.getMotivo(),
                m.getCreatedAt(),
                m.getUsuario() != null ? m.getUsuario().getEmail() : null
        );
    }
```

- [ ] **Paso 3: Agregar 2 métodos de repositorio**

En `backend/src/main/java/com/smartrdp/backend/movimiento/MovimientoRepository.java`, agregar (después de `findByProductoIdOrderByCreatedAtDesc`):

```java
    List<Movimiento> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime desde, LocalDateTime hasta);

    List<Movimiento> findByProductoIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long productoId, LocalDateTime desde, LocalDateTime hasta);
```

- [ ] **Paso 4: Agregar `MovimientoService.listar(...)`**

En `backend/src/main/java/com/smartrdp/backend/movimiento/MovimientoService.java`, agregar el import `java.time.LocalDate` (ya está importado) y este método público (después de `registrarSalida`, antes de `getStockActual`):

```java
    @Transactional(readOnly = true)
    public List<MovimientoResponse> listar(Long productoId, LocalDate desde, LocalDate hasta) {
        LocalDateTime desdeDt = (desde != null ? desde : LocalDate.now().minusDays(30)).atStartOfDay();
        LocalDateTime hastaDt = (hasta != null ? hasta : LocalDate.now()).atTime(23, 59, 59);
        List<Movimiento> movimientos = productoId != null
                ? movimientoRepository.findByProductoIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        productoId, desdeDt, hastaDt)
                : movimientoRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(desdeDt, hastaDt);
        return movimientos.stream().map(this::toResponse).toList();
    }
```

Agregar el import `java.time.LocalDateTime` si no está ya presente en el archivo (verificar antes de agregar, puede que ya esté importado por otro método).

- [ ] **Paso 5: Agregar el endpoint en `MovimientoController`**

En `backend/src/main/java/com/smartrdp/backend/movimiento/MovimientoController.java`, agregar los imports `org.springframework.format.annotation.DateTimeFormat` y `java.time.LocalDate`, y este método (después de `entrada`/`salida`, antes de `stock`):

```java
    @GetMapping
    public List<MovimientoResponse> listar(
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return movimientoService.listar(productoId, desde, hasta);
    }
```

- [ ] **Paso 6: Verificar**

```bash
cd backend
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./mvnw -q compile
```

Manual (con el backend corriendo, un token JWT válido, y al menos una entrada/salida ya registrada de una sesión anterior):

```bash
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/movimientos"
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/movimientos?desde=2026-01-01&hasta=2026-12-31"
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/movimientos?productoId=1"
```

Confirmar que cada respuesta incluye el campo `usuarioEmail` (puede ser `null` si el movimiento no tiene usuario asociado) y que el filtro por fecha/producto realmente reduce los resultados.

- [ ] **Paso 7: Commit**

```bash
git add backend/src/main/java/com/smartrdp/backend/movimiento
git commit -m "Agrega endpoint de listado de movimientos con filtros de producto y fecha"
```

---

## Task 2: Backend — endpoint de stock agregado para todos los productos

**Files:**
- Modify: `backend/src/main/java/com/smartrdp/backend/movimiento/MovimientoRepository.java`
- Modify: `backend/src/main/java/com/smartrdp/backend/movimiento/MovimientoService.java`
- Modify: `backend/src/main/java/com/smartrdp/backend/movimiento/MovimientoController.java`

**Interfaces:**
- Consumes: `ProductoRepository.findByActivoTrue(): List<Producto>` (ya existente).
- Produces: `MovimientoService.listarStock(): List<StockStatusResponse>`.
- Produces: `GET /api/movimientos/stock`.
- Consumes (Task 6): el frontend llama a este endpoint para la vista de Estado de stock.

- [ ] **Paso 1: Agregar la consulta agregada de stock por producto**

En `backend/src/main/java/com/smartrdp/backend/movimiento/MovimientoRepository.java`, agregar el import `org.springframework.data.jpa.repository.Query` (ya está importado) y este método:

```java
    @Query("""
        SELECT m.producto.id,
               SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.cantidad ELSE -m.cantidad END)
        FROM Movimiento m
        GROUP BY m.producto.id
        """)
    List<Object[]> sumStockPorProducto();
```

- [ ] **Paso 2: Agregar `MovimientoService.listarStock()`**

En `backend/src/main/java/com/smartrdp/backend/movimiento/MovimientoService.java`, inyectar `ProductoRepository` si no está ya inyectado (verificar el bloque de campos al inicio de la clase — ya está inyectado, se usa en `registrarEntrada`/`registrarSalida`), y agregar este método (después de `getEstadoStock`):

```java
    @Transactional(readOnly = true)
    public List<StockStatusResponse> listarStock() {
        Map<Long, Integer> stockPorProducto = movimientoRepository.sumStockPorProducto().stream()
                .collect(Collectors.toMap(
                        fila -> (Long) fila[0],
                        fila -> ((Number) fila[1]).intValue()));

        return productoRepository.findByActivoTrue().stream()
                .map(producto -> {
                    int stock = stockPorProducto.getOrDefault(producto.getId(), 0);
                    EstadoStock estado;
                    if (stock <= 0) estado = EstadoStock.AGOTADO;
                    else if (stock <= producto.getStockCritico()) estado = EstadoStock.CRITICO;
                    else if (stock <= producto.getStockMinimo()) estado = EstadoStock.BAJO;
                    else estado = EstadoStock.NORMAL;

                    LocalDate proximoVencimiento = loteRepository
                            .findByProductoIdAndCantidadDisponibleGreaterThanOrderByFechaVencimientoAsc(
                                    producto.getId(), 0)
                            .stream().findFirst().map(Lote::getFechaVencimiento).orElse(null);

                    return new StockStatusResponse(
                            producto.getId(), producto.getNombre(), stock, estado, proximoVencimiento);
                })
                .toList();
    }
```

Agregar los imports que falten al inicio del archivo: `java.util.Map` y `java.util.stream.Collectors`.

- [ ] **Paso 3: Agregar el endpoint**

En `backend/src/main/java/com/smartrdp/backend/movimiento/MovimientoController.java`, agregar (después del endpoint `listar` de la Task 1, antes de `stock`):

```java
    @GetMapping("/stock")
    public List<StockStatusResponse> listarStock() {
        return movimientoService.listarStock();
    }
```

- [ ] **Paso 4: Verificar**

```bash
cd backend
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./mvnw -q compile
```

Manual:

```bash
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/movimientos/stock"
```

Confirmar que devuelve un elemento por cada producto activo (comparar el largo del arreglo contra `GET /api/productos?soloActivos=true`), que un producto sin ningún movimiento aparece con `stockActual: 0` y `estado: "AGOTADO"`, y que un producto con entradas registradas muestra el estado correcto según sus umbrales.

- [ ] **Paso 5: Commit**

```bash
git add backend/src/main/java/com/smartrdp/backend/movimiento
git commit -m "Agrega endpoint de stock agregado de todos los productos activos"
```

---

## Task 3: Frontend — modelo, servicio, rutas y navegación de Movimientos

**Files:**
- Create: `frontend/src/app/features/movimientos/movimiento.modelo.ts`
- Create: `frontend/src/app/features/movimientos/movimiento.service.ts`
- Create: `frontend/src/app/features/movimientos/movimientos.routes.ts`
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/core/layout/navegacion.ts`

**Interfaces:**
- Produces: interfaces `Movimiento`, `EntradaRequest`, `SalidaRequest`, `StockStatus` (nombres exactos que consumen las Tasks 4-7).
- Produces: `MovimientoService` con los métodos `listar`, `registrarEntrada`, `registrarSalida`, `listarStock`, `alertasVencimiento` (firmas exactas abajo).
- Consumes: `environment.apiUrl` (ya existente, mismo patrón que `ProductoService`).

- [ ] **Paso 1: Modelo**

Crear `frontend/src/app/features/movimientos/movimiento.modelo.ts`:

```typescript
export type TipoMovimiento = 'ENTRADA' | 'SALIDA';

export interface Movimiento {
  id: number;
  productoId: number;
  productoNombre: string;
  tipo: TipoMovimiento;
  cantidad: number;
  motivo: string | null;
  fecha: string;
  usuarioEmail: string | null;
}

export interface EntradaRequest {
  productoId: number;
  cantidad: number;
  fechaVencimiento: string | null;
  numeroLote: string | null;
  motivo: string | null;
}

export interface SalidaRequest {
  productoId: number;
  cantidad: number;
  motivo: string;
}

export type EstadoStock = 'NORMAL' | 'BAJO' | 'CRITICO' | 'AGOTADO';

export interface StockStatus {
  productoId: number;
  productoNombre: string;
  stockActual: number;
  estado: EstadoStock;
  fechaVencimientoProximo: string | null;
}
```

- [ ] **Paso 2: Servicio**

Crear `frontend/src/app/features/movimientos/movimiento.service.ts`:

```typescript
import { HttpClient, HttpResourceRef, httpResource } from '@angular/common/http';
import { Injectable, Signal, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  EntradaRequest,
  Movimiento,
  SalidaRequest,
  StockStatus,
} from './movimiento.modelo';

export interface FiltroMovimientos {
  productoId: number | null;
  desde: string | null;
  hasta: string | null;
}

@Injectable({ providedIn: 'root' })
export class MovimientoService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/movimientos`;

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  listar(filtro: Signal<FiltroMovimientos>): HttpResourceRef<Movimiento[]> {
    return httpResource<Movimiento[]>(
      () => {
        const f = filtro();
        const params: Record<string, string | number> = {};
        if (f.productoId !== null) params['productoId'] = f.productoId;
        if (f.desde !== null) params['desde'] = f.desde;
        if (f.hasta !== null) params['hasta'] = f.hasta;
        return { url: this.url, params };
      },
      { defaultValue: [] },
    );
  }

  registrarEntrada(request: EntradaRequest): Observable<Movimiento> {
    return this.http.post<Movimiento>(`${this.url}/entradas`, request);
  }

  registrarSalida(request: SalidaRequest): Observable<Movimiento> {
    return this.http.post<Movimiento>(`${this.url}/salidas`, request);
  }

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  listarStock(): HttpResourceRef<StockStatus[]> {
    return httpResource<StockStatus[]>(() => `${this.url}/stock`, { defaultValue: [] });
  }

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  alertasVencimiento(dias: Signal<number>): HttpResourceRef<Movimiento[]> {
    return httpResource<Movimiento[]>(
      () => ({ url: `${this.url}/alertas/vencimiento`, params: { dias: dias() } }),
      { defaultValue: [] },
    );
  }
}
```

- [ ] **Paso 3: Rutas**

Crear `frontend/src/app/features/movimientos/movimientos.routes.ts`:

```typescript
import { Routes } from '@angular/router';
import { rolGuard } from '../../core/auth/auth.guards';

export const MOVIMIENTOS_ROUTES: Routes = [
  {
    path: '',
    title: 'Movimientos · Smart RDP',
    loadComponent: () => import('./movimiento-lista').then((m) => m.MovimientoLista),
  },
  {
    path: 'entradas/nueva',
    title: 'Registrar entrada · Smart RDP',
    canActivate: [rolGuard(['ADMIN', 'BODEGUERO'])],
    loadComponent: () =>
      import('./movimiento-formulario-entrada').then((m) => m.MovimientoFormularioEntrada),
  },
  {
    path: 'salidas/nueva',
    title: 'Registrar salida · Smart RDP',
    canActivate: [rolGuard(['ADMIN', 'BODEGUERO'])],
    loadComponent: () =>
      import('./movimiento-formulario-salida').then((m) => m.MovimientoFormularioSalida),
  },
  {
    path: 'stock',
    title: 'Estado de stock · Smart RDP',
    loadComponent: () => import('./stock-lista').then((m) => m.StockLista),
  },
  {
    path: 'alertas',
    title: 'Alertas de vencimiento · Smart RDP',
    loadComponent: () => import('./alertas-lista').then((m) => m.AlertasLista),
  },
];
```

- [ ] **Paso 4: Registrar la ruta en `app.routes.ts`**

En `frontend/src/app/app.routes.ts`, agregar dentro del arreglo `children` del `Shell` (junto a `productos` y `administracion`):

```typescript
      {
        path: 'movimientos',
        loadChildren: () => import('./features/movimientos/movimientos.routes').then(m => m.MOVIMIENTOS_ROUTES),
      },
```

- [ ] **Paso 5: Agregar el grupo "Movimientos" a la navegación**

En `frontend/src/app/core/layout/navegacion.ts`, agregar al arreglo `NAVEGACION` (después de `Productos`, antes de `Categorías`):

```typescript
  { etiqueta: 'Movimientos', ruta: '/movimientos', roles: ['ADMIN', 'BODEGUERO', 'TRABAJADOR'] },
  { etiqueta: 'Estado de stock', ruta: '/movimientos/stock', roles: ['ADMIN', 'BODEGUERO', 'TRABAJADOR'] },
  { etiqueta: 'Alertas de vencimiento', ruta: '/movimientos/alertas', roles: ['ADMIN', 'BODEGUERO', 'TRABAJADOR'] },
```

- [ ] **Paso 6: Verificar**

```bash
cd frontend && npx ng build && npx eslint "src/**/*.ts" "src/**/*.html"
```

Este paso fallará hasta que las Tasks 4-7 creen los componentes que las rutas importan (`MovimientoLista`, `MovimientoFormularioEntrada`, `MovimientoFormularioSalida`, `StockLista`, `AlertasLista`) — es esperado. Confirmar en su lugar que `movimiento.modelo.ts` y `movimiento.service.ts` no tienen errores de TypeScript propios ejecutando `npx tsc --noEmit -p tsconfig.app.json` (o revisando que el único error de build sea "no se puede encontrar el módulo" para los 5 archivos que aún no existen, no un error de sintaxis en los archivos de esta tarea).

- [ ] **Paso 7: Commit**

```bash
git add frontend/src/app/features/movimientos frontend/src/app/app.routes.ts frontend/src/app/core/layout/navegacion.ts
git commit -m "Agrega modelo, servicio, rutas y navegación de Movimientos"
```

---

## Task 4: Frontend — Historial de movimientos + calendario semanal

**Files:**
- Create: `frontend/src/app/features/movimientos/movimiento-lista.ts`
- Create: `frontend/src/app/features/movimientos/movimiento-lista.html`
- Create: `frontend/src/app/features/movimientos/movimiento-calendario.ts`
- Create: `frontend/src/app/features/movimientos/movimiento-calendario.html`
- Create: `frontend/src/app/features/movimientos/movimiento-calendario.css`

**Interfaces:**
- Consumes: `MovimientoService.listar(filtro): HttpResourceRef<Movimiento[]>` (Task 3), `Paginacion` (`shared/paginacion/paginacion.ts`, ya existente), `EstadoVista` (`shared/estado-vista/estado-vista.ts`, ya existente), `ProductoService.listar(soloActivos): HttpResourceRef<Producto[]>` (`features/productos/producto.service.ts`, ya existente — para poblar el select de filtro por producto).
- Produces: componente `MovimientoCalendario` (`selector: 'app-movimiento-calendario'`) — `diaSeleccionado = output<string | null>()`.

- [ ] **Paso 1: Componente de calendario semanal**

Crear `frontend/src/app/features/movimientos/movimiento-calendario.ts`:

```typescript
import { ChangeDetectionStrategy, Component, computed, inject, output, signal } from '@angular/core';
import { MovimientoService } from './movimiento.service';

interface DiaSemana {
  fecha: string;
  dia: number;
  etiqueta: string;
}

const DIAS_ES = ['Lu', 'Ma', 'Mi', 'Ju', 'Vi', 'Sa', 'Do'];

function formatearFecha(fecha: Date): string {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

function inicioDeSemana(fecha: Date): Date {
  const copia = new Date(fecha);
  const dow = copia.getDay();
  const offset = dow === 0 ? -6 : 1 - dow;
  copia.setDate(copia.getDate() + offset);
  return copia;
}

@Component({
  selector: 'app-movimiento-calendario',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimiento-calendario.html',
  styleUrl: './movimiento-calendario.css',
})
export class MovimientoCalendario {
  private readonly movimientoService = inject(MovimientoService);

  readonly diaSeleccionado = output<string | null>();

  protected readonly inicioSemana = signal(inicioDeSemana(new Date()));
  protected readonly diaActivo = signal<string | null>(null);

  protected readonly diasSemana = computed<DiaSemana[]>(() => {
    const inicio = this.inicioSemana();
    return DIAS_ES.map((etiqueta, i) => {
      const fecha = new Date(inicio);
      fecha.setDate(fecha.getDate() + i);
      return { fecha: formatearFecha(fecha), dia: fecha.getDate(), etiqueta };
    });
  });

  protected readonly etiquetaSemana = computed(() => {
    const dias = this.diasSemana();
    return `${dias[0].dia} — ${dias[6].dia}`;
  });

  private readonly filtroSemana = computed(() => {
    const dias = this.diasSemana();
    return { productoId: null, desde: dias[0].fecha, hasta: dias[6].fecha };
  });

  protected readonly movimientos = this.movimientoService.listar(this.filtroSemana);

  protected readonly resumenPorDia = computed(() => {
    const mapa = new Map<string, { entrada: boolean; salida: boolean }>();
    if (!this.movimientos.hasValue()) return mapa;
    for (const m of this.movimientos.value()) {
      const fecha = m.fecha.slice(0, 10);
      const actual = mapa.get(fecha) ?? { entrada: false, salida: false };
      if (m.tipo === 'ENTRADA') actual.entrada = true;
      else actual.salida = true;
      mapa.set(fecha, actual);
    }
    return mapa;
  });

  protected cambiarSemana(delta: number): void {
    const nueva = new Date(this.inicioSemana());
    nueva.setDate(nueva.getDate() + delta * 7);
    this.inicioSemana.set(nueva);
  }

  protected seleccionarDia(fecha: string): void {
    const nueva = this.diaActivo() === fecha ? null : fecha;
    this.diaActivo.set(nueva);
    this.diaSeleccionado.emit(nueva);
  }
}
```

- [ ] **Paso 2: Plantilla del calendario**

Crear `frontend/src/app/features/movimientos/movimiento-calendario.html`:

```html
<div class="w3-bar w3-light-grey w3-border" style="display: flex; align-items: center">
  <button
    type="button"
    class="w3-button w3-border w3-white w3-margin w3-round"
    (click)="cambiarSemana(-1)"
  >
    <i class="fa fa-chevron-left" aria-hidden="true"></i> Semana anterior
  </button>
  <span class="w3-center" style="flex: 1; font-weight: bold">Semana del {{ etiquetaSemana() }}</span>
  <button
    type="button"
    class="w3-button w3-border w3-white w3-margin w3-round"
    (click)="cambiarSemana(1)"
  >
    Semana siguiente <i class="fa fa-chevron-right" aria-hidden="true"></i>
  </button>
</div>

<div class="w3-white w3-border w3-padding-small">
  @if (movimientos.status() === 'loading') {
    <p role="status" class="w3-center w3-small w3-text-grey">Cargando…</p>
  }

  <div class="calendario-semana-grilla">
    @for (dia of diasSemana(); track dia.fecha) {
      <button
        type="button"
        class="calendario-semana-dia"
        [class.seleccionado]="diaActivo() === dia.fecha"
        [attr.aria-pressed]="diaActivo() === dia.fecha"
        (click)="seleccionarDia(dia.fecha)"
      >
        <span class="w3-small w3-text-grey">{{ dia.etiqueta }}</span>
        <span class="numero-dia">{{ dia.dia }}</span>
        <span class="puntos-dia">
          @if (resumenPorDia().get(dia.fecha)?.entrada) {
            <span class="punto punto-entrada" aria-hidden="true"></span>
          }
          @if (resumenPorDia().get(dia.fecha)?.salida) {
            <span class="punto punto-salida" aria-hidden="true"></span>
          }
        </span>
      </button>
    }
  </div>

  <div class="w3-bar w3-small w3-text-grey w3-margin-top w3-border-top w3-padding-small">
    <span class="w3-bar-item"><span class="punto punto-entrada" aria-hidden="true"></span> Entrada</span>
    <span class="w3-bar-item"><span class="punto punto-salida" aria-hidden="true"></span> Salida</span>
  </div>
</div>
```

- [ ] **Paso 3: Estilos del calendario**

Crear `frontend/src/app/features/movimientos/movimiento-calendario.css`:

```css
.calendario-semana-grilla {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}
.calendario-semana-dia {
  background: #fff;
  border: 2px solid transparent;
  border-radius: 4px;
  padding: 8px 4px;
  min-height: 64px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}
.calendario-semana-dia:hover {
  background: #f0f0f0;
}
.calendario-semana-dia.seleccionado {
  background-color: #0050ef;
  border-color: #0050ef;
}
.calendario-semana-dia.seleccionado .w3-text-grey {
  color: #fff !important;
}
.numero-dia {
  font-weight: bold;
}
.calendario-semana-dia.seleccionado .numero-dia {
  color: #fff;
}
.puntos-dia {
  display: flex;
  gap: 3px;
  min-height: 8px;
}
.punto {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.punto-entrada { background: #008a00; }
.punto-salida { background: #a20025; }
```

Nota para quien implemente: `#0050ef` es exactamente `w3-cobalt` (ya usado como variable `--w3-cobalt` en `frontend/src/styles.css` desde el plan de reskin) — usar `var(--w3-cobalt)` en vez del hex si se quiere mantener esa convención, no es obligatorio para esta tarea pero es preferible.

- [ ] **Paso 4: Componente de historial**

Crear `frontend/src/app/features/movimientos/movimiento-lista.ts`:

```typescript
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { EstadoBadge } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { ProductoService } from '../productos/producto.service';
import { MovimientoCalendario } from './movimiento-calendario';
import { MovimientoService } from './movimiento.service';

type Vista = 'historial' | 'calendario';

@Component({
  selector: 'app-movimiento-lista',
  imports: [RouterLink, EstadoVista, EstadoBadge, Paginacion, MovimientoCalendario],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimiento-lista.html',
})
export class MovimientoLista {
  private readonly movimientoService = inject(MovimientoService);
  private readonly productoService = inject(ProductoService);
  protected readonly auth = inject(AuthService);

  protected readonly puedeGestionar = computed(() => {
    const rol = this.auth.rol();
    return rol === 'ADMIN' || rol === 'BODEGUERO';
  });

  protected readonly vista = signal<Vista>('historial');
  protected readonly productoIdFiltro = signal<number | null>(null);
  protected readonly desdeFiltro = signal<string | null>(null);
  protected readonly hastaFiltro = signal<string | null>(null);
  protected readonly paginaActual = signal(1);
  protected readonly tamanoPagina = signal(10);

  protected readonly productos = this.productoService.listar(signal(true));
  protected readonly opcionesProducto = computed(() =>
    this.productos.hasValue() ? this.productos.value() : [],
  );

  protected readonly filtro = computed(() => ({
    productoId: this.productoIdFiltro(),
    desde: this.desdeFiltro(),
    hasta: this.hastaFiltro(),
  }));

  protected readonly movimientos = this.movimientoService.listar(this.filtro);
  protected readonly todos = computed(() =>
    this.movimientos.hasValue() ? this.movimientos.value() : [],
  );

  protected readonly paginados = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.tamanoPagina();
    return this.todos().slice(inicio, inicio + this.tamanoPagina());
  });

  protected cambiarProductoFiltro(valor: string): void {
    this.productoIdFiltro.set(valor === '' ? null : Number(valor));
    this.paginaActual.set(1);
  }

  protected cambiarDesde(valor: string): void {
    this.desdeFiltro.set(valor === '' ? null : valor);
    this.paginaActual.set(1);
  }

  protected cambiarHasta(valor: string): void {
    this.hastaFiltro.set(valor === '' ? null : valor);
    this.paginaActual.set(1);
  }

  protected cambiarPagina(pagina: number): void {
    this.paginaActual.set(pagina);
  }

  protected cambiarTamanoPagina(tamano: number): void {
    this.tamanoPagina.set(tamano);
    this.paginaActual.set(1);
  }

  protected alSeleccionarDiaCalendario(fecha: string | null): void {
    this.desdeFiltro.set(fecha);
    this.hastaFiltro.set(fecha);
    this.paginaActual.set(1);
    this.vista.set('historial');
  }
}
```

- [ ] **Paso 5: Plantilla del historial**

Crear `frontend/src/app/features/movimientos/movimiento-lista.html`:

```html
<h1 tabindex="-1">Movimientos</h1>

@if (puedeGestionar()) {
  <p>
    <a routerLink="/movimientos/entradas/nueva" class="w3-button w3-emerald w3-round">
      <span aria-hidden="true">+ </span>Registrar entrada
    </a>
    <a routerLink="/movimientos/salidas/nueva" class="w3-button w3-crimson w3-round w3-margin-left">
      <span aria-hidden="true">+ </span>Registrar salida
    </a>
  </p>
}

<div class="w3-bar w3-margin-bottom" role="tablist" aria-label="Vista de movimientos">
  <button
    type="button"
    class="w3-bar-item w3-button"
    [class.w3-cobalt]="vista() === 'historial'"
    role="tab"
    [attr.aria-selected]="vista() === 'historial'"
    (click)="vista.set('historial')"
  >
    Historial
  </button>
  <button
    type="button"
    class="w3-bar-item w3-button"
    [class.w3-cobalt]="vista() === 'calendario'"
    role="tab"
    [attr.aria-selected]="vista() === 'calendario'"
    (click)="vista.set('calendario')"
  >
    Calendario
  </button>
</div>

@if (vista() === 'calendario') {
  <app-movimiento-calendario (diaSeleccionado)="alSeleccionarDiaCalendario($event)" />
} @else {
  <div class="w3-row w3-section w3-container w3-border w3-padding w3-light-grey">
    <div class="w3-col m4 w3-padding-small">
      <label for="filtro-producto-movimiento"><b>Producto</b></label>
      <select
        #selectProducto
        id="filtro-producto-movimiento"
        class="w3-select"
        (change)="cambiarProductoFiltro(selectProducto.value)"
      >
        <option value="" [selected]="productoIdFiltro() === null">Todos</option>
        @for (p of opcionesProducto(); track p.id) {
          <option [value]="p.id" [selected]="productoIdFiltro() === p.id">{{ p.nombre }}</option>
        }
      </select>
    </div>
    <div class="w3-col m4 w3-padding-small">
      <label for="filtro-desde"><b>Desde</b></label>
      <input
        #campoDesde
        id="filtro-desde"
        class="w3-input"
        type="date"
        [value]="desdeFiltro() ?? ''"
        (change)="cambiarDesde(campoDesde.value)"
      />
    </div>
    <div class="w3-col m4 w3-padding-small">
      <label for="filtro-hasta"><b>Hasta</b></label>
      <input
        #campoHasta
        id="filtro-hasta"
        class="w3-input"
        type="date"
        [value]="hastaFiltro() ?? ''"
        (change)="cambiarHasta(campoHasta.value)"
      />
    </div>
  </div>

  <app-estado-vista
    [cargando]="movimientos.status() === 'loading'"
    [error]="movimientos.error()"
    [vacio]="todos().length === 0"
    mensajeCarga="Cargando movimientos…"
    mensajeVacio="No hay movimientos en el rango seleccionado."
    (reintentar)="movimientos.reload()"
  >
    <div class="w3-responsive">
      <table class="w3-table w3-bordered w3-white">
        <caption class="visualmente-oculto">Historial de movimientos</caption>
        <thead>
          <tr class="w3-light-grey">
            <th scope="col">Fecha</th>
            <th scope="col">Producto</th>
            <th scope="col">Tipo</th>
            <th scope="col">Cantidad</th>
            <th scope="col">Motivo</th>
            <th scope="col">Usuario</th>
          </tr>
        </thead>
        <tbody>
          @for (m of paginados(); track m.id) {
            <tr>
              <td>{{ m.fecha | date: 'dd-MM-yyyy HH:mm' }}</td>
              <td>{{ m.productoNombre }}</td>
              <td>
                @if (m.tipo === 'ENTRADA') {
                  <app-estado-badge estado="normal" />
                } @else {
                  <app-estado-badge estado="critico" />
                }
              </td>
              <td>{{ m.cantidad }}</td>
              <td>{{ m.motivo || '—' }}</td>
              <td>{{ m.usuarioEmail || '—' }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>

    <app-paginacion
      [pagina]="paginaActual()"
      [totalItems]="todos().length"
      [tamanoPagina]="tamanoPagina()"
      (paginaCambiada)="cambiarPagina($event)"
      (tamanoPaginaCambiado)="cambiarTamanoPagina($event)"
    />
  </app-estado-vista>
}
```

Nota para quien implemente: `<app-estado-badge estado="normal">`/`estado="critico"` se reutilizan aquí solo por su color (verde/rojo), no por su significado original de stock — es una reutilización visual válida del componente ya existente (acepta cualquiera de sus 9 estados), no requiere agregar un estado nuevo a `EstadoBadge`. Si al revisar se ve confuso semánticamente, es una decisión a evaluar en la revisión de la tarea, no un error de por sí.

Se necesita importar `DatePipe` — agregar `import { DatePipe } from '@angular/common';` en `movimiento-lista.ts` y sumarlo al arreglo `imports` del `@Component`, ya que la plantilla usa el pipe `date`.

- [ ] **Paso 6: Verificar**

```bash
cd frontend && npx ng build && npx eslint "src/**/*.ts" "src/**/*.html"
```

Este paso todavía puede fallar por los componentes de las Tasks 5-7 (formularios, stock, alertas) que aún no existen — confirmar que los únicos errores restantes son "no se puede encontrar el módulo" para esos 3 archivos, no errores en los archivos de esta tarea.

Manual (con backend + datos de prueba): navegar a `/movimientos`, confirmar que la tabla muestra movimientos existentes, que los filtros de producto/fecha funcionan, que la pestaña "Calendario" muestra la semana actual con puntos verdes/rojos en los días con movimiento, y que hacer clic en un día del calendario cambia a la pestaña Historial filtrada a esa fecha exacta.

- [ ] **Paso 7: Commit**

```bash
git add frontend/src/app/features/movimientos/movimiento-lista.ts frontend/src/app/features/movimientos/movimiento-lista.html frontend/src/app/features/movimientos/movimiento-calendario.ts frontend/src/app/features/movimientos/movimiento-calendario.html frontend/src/app/features/movimientos/movimiento-calendario.css
git commit -m "Agrega historial de movimientos con filtros y calendario semanal"
```

---

## Task 5: Frontend — Formularios de registro de entrada y salida

**Files:**
- Create: `frontend/src/app/features/movimientos/movimiento-formulario-entrada.ts`
- Create: `frontend/src/app/features/movimientos/movimiento-formulario-entrada.html`
- Create: `frontend/src/app/features/movimientos/movimiento-formulario-salida.ts`
- Create: `frontend/src/app/features/movimientos/movimiento-formulario-salida.html`

**Interfaces:**
- Consumes: `MovimientoService.registrarEntrada/registrarSalida` (Task 3), `ProductoService.listar` (ya existente), `NotificacionService` (ya existente, alertify por debajo desde el plan de reskin).

- [ ] **Paso 1: Formulario de entrada**

Crear `frontend/src/app/features/movimientos/movimiento-formulario-entrada.ts`:

```typescript
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { ProductoService } from '../productos/producto.service';
import { EntradaRequest } from './movimiento.modelo';
import { MovimientoService } from './movimiento.service';

@Component({
  selector: 'app-movimiento-formulario-entrada',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimiento-formulario-entrada.html',
})
export class MovimientoFormularioEntrada {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly movimientoService = inject(MovimientoService);
  private readonly productoService = inject(ProductoService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly router = inject(Router);

  protected readonly productos = this.productoService.listar(signal(true));
  protected readonly opcionesProducto = computed(() =>
    this.productos.hasValue() ? this.productos.value() : [],
  );

  protected readonly form = this.fb.group({
    productoId: this.fb.control<number | null>(null, Validators.required),
    cantidad: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
    fechaVencimiento: [''],
    numeroLote: ['', Validators.maxLength(50)],
    motivo: ['', Validators.maxLength(200)],
  });

  protected readonly enviado = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  protected readonly productoSeleccionado = computed(() => {
    const id = this.form.controls.productoId.value;
    return this.opcionesProducto().find((p) => p.id === id) ?? null;
  });

  protected guardar(): void {
    if (this.guardando()) return;
    this.enviado.set(true);
    this.errorGeneral.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    if (v.productoId === null || v.cantidad === null) return;
    const request: EntradaRequest = {
      productoId: v.productoId,
      cantidad: v.cantidad,
      fechaVencimiento: v.fechaVencimiento.trim() === '' ? null : v.fechaVencimiento,
      numeroLote: v.numeroLote.trim() === '' ? null : v.numeroLote.trim(),
      motivo: v.motivo.trim() === '' ? null : v.motivo.trim(),
    };
    this.guardando.set(true);
    this.movimientoService.registrarEntrada(request).subscribe({
      next: () => {
        this.guardando.set(false);
        void this.router
          .navigate(['/movimientos'])
          .then(() => this.notificaciones.exito('Entrada registrada.'));
      },
      error: () => {
        this.guardando.set(false);
        this.errorGeneral.set('No se pudo registrar la entrada. Inténtalo de nuevo.');
      },
    });
  }
}
```

- [ ] **Paso 2: Plantilla del formulario de entrada**

Crear `frontend/src/app/features/movimientos/movimiento-formulario-entrada.html`:

```html
<p>
  <a routerLink="/movimientos"><span aria-hidden="true">← </span>Volver a Movimientos</a>
</p>
<h1 tabindex="-1">Registrar entrada</h1>

@if (errorGeneral(); as mensaje) {
  <div class="w3-panel w3-pale-red w3-leftbar w3-border-red" role="alert">
    <p>{{ mensaje }}</p>
  </div>
}

<form [formGroup]="form" (ngSubmit)="guardar()" novalidate class="w3-section w3-white w3-padding w3-card">
  <div class="w3-section">
    <label for="entrada-producto"><b>Producto</b></label>
    <select id="entrada-producto" class="w3-select" formControlName="productoId">
      <option [ngValue]="null" disabled>Elige un producto</option>
      @for (p of opcionesProducto(); track p.id) {
        <option [ngValue]="p.id">{{ p.nombre }} ({{ p.codigoPtv }})</option>
      }
    </select>
    @if (enviado() && form.controls.productoId.invalid) {
      <p class="texto-error"><span aria-hidden="true">⚠ </span>Elige un producto.</p>
    }
  </div>

  <div class="w3-section">
    <label for="entrada-cantidad"><b>Cantidad</b></label>
    <input
      id="entrada-cantidad"
      class="w3-input"
      type="number"
      min="1"
      step="1"
      inputmode="numeric"
      formControlName="cantidad"
    />
    @if (enviado() && form.controls.cantidad.invalid) {
      <p class="texto-error"><span aria-hidden="true">⚠ </span>Ingresa una cantidad de al menos 1.</p>
    }
  </div>

  @if (productoSeleccionado()?.esPerecible) {
    <div class="w3-section">
      <label for="entrada-vencimiento"><b>Fecha de vencimiento</b></label>
      <input id="entrada-vencimiento" class="w3-input" type="date" formControlName="fechaVencimiento" />
    </div>
  }

  <div class="w3-section">
    <label for="entrada-lote"><b>N° de lote (opcional)</b></label>
    <input id="entrada-lote" class="w3-input" type="text" maxlength="50" formControlName="numeroLote" />
  </div>

  <div class="w3-section">
    <label for="entrada-motivo"><b>Motivo (opcional)</b></label>
    <input id="entrada-motivo" class="w3-input" type="text" maxlength="200" formControlName="motivo" />
  </div>

  <div class="w3-section">
    <button type="submit" class="w3-button w3-emerald w3-round" [attr.aria-disabled]="guardando()">
      {{ guardando() ? 'Guardando…' : 'Registrar entrada' }}
    </button>
    <a routerLink="/movimientos" class="w3-button w3-white w3-border w3-round w3-margin-left">Cancelar</a>
  </div>
</form>
```

- [ ] **Paso 3: Formulario de salida**

Crear `frontend/src/app/features/movimientos/movimiento-formulario-salida.ts`:

```typescript
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { traducirError } from '../../core/errores/traducir-error';
import { ProductoService } from '../productos/producto.service';
import { SalidaRequest } from './movimiento.modelo';
import { MovimientoService } from './movimiento.service';

@Component({
  selector: 'app-movimiento-formulario-salida',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimiento-formulario-salida.html',
})
export class MovimientoFormularioSalida {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly movimientoService = inject(MovimientoService);
  private readonly productoService = inject(ProductoService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly router = inject(Router);

  protected readonly productos = this.productoService.listar(signal(true));
  protected readonly opcionesProducto = computed(() =>
    this.productos.hasValue() ? this.productos.value() : [],
  );

  protected readonly form = this.fb.group({
    productoId: this.fb.control<number | null>(null, Validators.required),
    cantidad: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
    motivo: ['', [Validators.required, Validators.maxLength(200)]],
  });

  protected readonly enviado = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  protected guardar(): void {
    if (this.guardando()) return;
    this.enviado.set(true);
    this.errorGeneral.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    if (v.productoId === null || v.cantidad === null) return;
    const request: SalidaRequest = { productoId: v.productoId, cantidad: v.cantidad, motivo: v.motivo.trim() };
    this.guardando.set(true);
    this.movimientoService.registrarSalida(request).subscribe({
      next: () => {
        this.guardando.set(false);
        void this.router
          .navigate(['/movimientos'])
          .then(() => this.notificaciones.exito('Salida registrada.'));
      },
      error: (err: unknown) => {
        this.guardando.set(false);
        this.errorGeneral.set(traducirError(err).mensaje);
      },
    });
  }
}
```

Nota para quien implemente: a diferencia del formulario de entrada, este usa `traducirError(err).mensaje` en el error handler en vez de un mensaje fijo — el backend puede rechazar la salida con `BusinessException("Stock insuficiente...")` (ver `MovimientoService.registrarSalida`), y ese mensaje específico debe llegar al usuario, no un genérico. Revisar `frontend/src/app/core/errores/traducir-error.ts` (ya existente, usado en `producto-formulario.ts`) para confirmar la firma exacta antes de usarlo.

- [ ] **Paso 4: Plantilla del formulario de salida**

Crear `frontend/src/app/features/movimientos/movimiento-formulario-salida.html`:

```html
<p>
  <a routerLink="/movimientos"><span aria-hidden="true">← </span>Volver a Movimientos</a>
</p>
<h1 tabindex="-1">Registrar salida</h1>

@if (errorGeneral(); as mensaje) {
  <div class="w3-panel w3-pale-red w3-leftbar w3-border-red" role="alert">
    <p>{{ mensaje }}</p>
  </div>
}

<form [formGroup]="form" (ngSubmit)="guardar()" novalidate class="w3-section w3-white w3-padding w3-card">
  <div class="w3-section">
    <label for="salida-producto"><b>Producto</b></label>
    <select id="salida-producto" class="w3-select" formControlName="productoId">
      <option [ngValue]="null" disabled>Elige un producto</option>
      @for (p of opcionesProducto(); track p.id) {
        <option [ngValue]="p.id">{{ p.nombre }} ({{ p.codigoPtv }})</option>
      }
    </select>
    @if (enviado() && form.controls.productoId.invalid) {
      <p class="texto-error"><span aria-hidden="true">⚠ </span>Elige un producto.</p>
    }
  </div>

  <div class="w3-section">
    <label for="salida-cantidad"><b>Cantidad</b></label>
    <input
      id="salida-cantidad"
      class="w3-input"
      type="number"
      min="1"
      step="1"
      inputmode="numeric"
      formControlName="cantidad"
    />
    @if (enviado() && form.controls.cantidad.invalid) {
      <p class="texto-error"><span aria-hidden="true">⚠ </span>Ingresa una cantidad de al menos 1.</p>
    }
  </div>

  <div class="w3-section">
    <label for="salida-motivo"><b>Motivo</b></label>
    <input id="salida-motivo" class="w3-input" type="text" maxlength="200" formControlName="motivo" />
    @if (enviado() && form.controls.motivo.invalid) {
      <p class="texto-error"><span aria-hidden="true">⚠ </span>Ingresa el motivo de la salida.</p>
    }
  </div>

  <div class="w3-section">
    <button type="submit" class="w3-button w3-crimson w3-round" [attr.aria-disabled]="guardando()">
      {{ guardando() ? 'Guardando…' : 'Registrar salida' }}
    </button>
    <a routerLink="/movimientos" class="w3-button w3-white w3-border w3-round w3-margin-left">Cancelar</a>
  </div>
</form>
```

- [ ] **Paso 5: Verificar**

```bash
cd frontend && npx ng build && npx eslint "src/**/*.ts" "src/**/*.html"
```

Manual: registrar una entrada de un producto perecible (confirmar que aparece el campo de fecha de vencimiento) y de uno no perecible (confirmar que no aparece); registrar una salida y confirmar el mensaje de error real cuando se pide más cantidad que el stock disponible (crear ese escenario a propósito).

- [ ] **Paso 6: Commit**

```bash
git add frontend/src/app/features/movimientos/movimiento-formulario-entrada.ts frontend/src/app/features/movimientos/movimiento-formulario-entrada.html frontend/src/app/features/movimientos/movimiento-formulario-salida.ts frontend/src/app/features/movimientos/movimiento-formulario-salida.html
git commit -m "Agrega formularios de registro de entrada y salida de stock"
```

---

## Task 6: Frontend — Estado de stock

**Files:**
- Create: `frontend/src/app/features/movimientos/stock-lista.ts`
- Create: `frontend/src/app/features/movimientos/stock-lista.html`

**Interfaces:**
- Consumes: `MovimientoService.listarStock(): HttpResourceRef<StockStatus[]>` (Task 3), `EstadoBadge` (ya existente — sus 4 estados `normal|bajo|critico|agotado` coinciden en minúsculas exactas con los valores del backend `NORMAL|BAJO|CRITICO|AGOTADO`, solo hay que pasar `.toLowerCase()`).

- [ ] **Paso 1: Componente**

Crear `frontend/src/app/features/movimientos/stock-lista.ts`:

```typescript
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { EstadoBadge, TipoEstado } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { MovimientoService } from './movimiento.service';

@Component({
  selector: 'app-stock-lista',
  imports: [EstadoVista, EstadoBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './stock-lista.html',
})
export class StockLista {
  private readonly movimientoService = inject(MovimientoService);

  protected readonly stock = this.movimientoService.listarStock();
  protected readonly todos = computed(() => (this.stock.hasValue() ? this.stock.value() : []));

  protected estadoBadge(estado: string): TipoEstado {
    return estado.toLowerCase() as TipoEstado;
  }
}
```

- [ ] **Paso 2: Plantilla**

Crear `frontend/src/app/features/movimientos/stock-lista.html`:

```html
<h1 tabindex="-1">Estado de stock</h1>

<app-estado-vista
  [cargando]="stock.status() === 'loading'"
  [error]="stock.error()"
  [vacio]="todos().length === 0"
  mensajeCarga="Cargando stock…"
  mensajeVacio="Aún no hay productos activos con movimientos."
  (reintentar)="stock.reload()"
>
  <div class="w3-responsive">
    <table class="w3-table w3-bordered w3-white">
      <caption class="visualmente-oculto">Estado de stock por producto</caption>
      <thead>
        <tr class="w3-light-grey">
          <th scope="col">Producto</th>
          <th scope="col">Stock actual</th>
          <th scope="col">Estado</th>
          <th scope="col">Próximo vencimiento</th>
        </tr>
      </thead>
      <tbody>
        @for (p of todos(); track p.productoId) {
          <tr>
            <td>{{ p.productoNombre }}</td>
            <td>{{ p.stockActual }}</td>
            <td><app-estado-badge [estado]="estadoBadge(p.estado)" /></td>
            <td>{{ p.fechaVencimientoProximo || '—' }}</td>
          </tr>
        }
      </tbody>
    </table>
  </div>
</app-estado-vista>
```

- [ ] **Paso 3: Verificar**

```bash
cd frontend && npx ng build && npx eslint "src/**/*.ts" "src/**/*.html"
```

Manual: navegar a `/movimientos/stock`, confirmar que aparece un producto por cada producto activo y que el badge de estado coincide con lo esperado según sus umbrales (comparar contra `GET /api/movimientos/stock` directamente si hace falta).

- [ ] **Paso 4: Commit**

```bash
git add frontend/src/app/features/movimientos/stock-lista.ts frontend/src/app/features/movimientos/stock-lista.html
git commit -m "Agrega vista de estado de stock por producto"
```

---

## Task 7: Frontend — Alertas de vencimiento

**Files:**
- Create: `frontend/src/app/features/movimientos/alertas-lista.ts`
- Create: `frontend/src/app/features/movimientos/alertas-lista.html`

**Interfaces:**
- Consumes: `MovimientoService.alertasVencimiento(dias): HttpResourceRef<Movimiento[]>` (Task 3).

- [ ] **Paso 1: Componente**

Crear `frontend/src/app/features/movimientos/alertas-lista.ts`:

```typescript
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { EstadoBadge } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { MovimientoService } from './movimiento.service';

@Component({
  selector: 'app-alertas-lista',
  imports: [EstadoVista, EstadoBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './alertas-lista.html',
})
export class AlertasLista {
  private readonly movimientoService = inject(MovimientoService);

  protected readonly diasUmbral = signal(7);
  protected readonly alertas = this.movimientoService.alertasVencimiento(this.diasUmbral);
  protected readonly todos = computed(() => (this.alertas.hasValue() ? this.alertas.value() : []));

  protected cambiarUmbral(valor: string): void {
    const numero = Number(valor);
    if (Number.isInteger(numero) && numero > 0) this.diasUmbral.set(numero);
  }
}
```

- [ ] **Paso 2: Plantilla**

Crear `frontend/src/app/features/movimientos/alertas-lista.html`:

```html
<h1 tabindex="-1">Alertas de vencimiento</h1>

<div class="w3-section w3-padding-small">
  <label for="alertas-umbral"><b>Avisar con</b></label>
  <select
    #selectUmbral
    id="alertas-umbral"
    class="w3-select"
    style="width: auto; display: inline-block"
    [value]="diasUmbral()"
    (change)="cambiarUmbral(selectUmbral.value)"
  >
    <option value="3">3 días de anticipación</option>
    <option value="7">7 días de anticipación</option>
    <option value="15">15 días de anticipación</option>
    <option value="30">30 días de anticipación</option>
  </select>
</div>

<app-estado-vista
  [cargando]="alertas.status() === 'loading'"
  [error]="alertas.error()"
  [vacio]="todos().length === 0"
  mensajeCarga="Cargando alertas…"
  mensajeVacio="No hay lotes por vencer en el rango seleccionado."
  (reintentar)="alertas.reload()"
>
  <ul class="w3-ul w3-white w3-border">
    @for (a of todos(); track a.id) {
      <li class="w3-padding">
        <app-estado-badge estado="pendiente" />
        <b>{{ a.productoNombre }}</b> — {{ a.motivo }}
      </li>
    }
  </ul>
</app-estado-vista>
```

- [ ] **Paso 3: Verificar**

```bash
cd frontend && npx ng build && npx eslint "src/**/*.ts" "src/**/*.html"
```

Este es el último componente del plan — confirmar que el build queda completamente limpio (sin ningún "no se puede encontrar el módulo" pendiente).

Manual: navegar a `/movimientos/alertas`, confirmar que la lista cambia al cambiar el umbral de días, y probar la navegación completa: desde el sidebar, entrar a Movimientos, Estado de stock y Alertas de vencimiento; registrar una entrada y una salida y confirmar que aparecen en el historial y afectan el estado de stock correctamente.

- [ ] **Paso 4: Commit**

```bash
git add frontend/src/app/features/movimientos/alertas-lista.ts frontend/src/app/features/movimientos/alertas-lista.html
git commit -m "Agrega vista de alertas de vencimiento"
```

---

## Fuera de alcance (explícitamente, para no seguir ampliando este plan)

- HU-07/08 (Solicitudes) — depende de este plan (genera salidas), se planifica después como un plan separado.
- HU-09 a 13 (Analítica) — reportes de consumo; el agregado de consumo por fechas ya existe en `MovimientoRepository.findConsumoEntreFechas` y quedó identificado, pero las pantallas son un plan aparte.
- Confirmar con Jorge si existen variantes de PTV con sufijo — no bloquea este plan (ver spec).
- Refactor de `getAlertasVencimiento` para usar un DTO propio en vez de sintetizar un `Movimiento` falso — funciona tal cual, no se toca aquí.
