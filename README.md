# Smart RDP

Sistema web de gestión de bodega para el restaurante **Doña Paula** (Viña Santa Rita, Buin). Reemplaza el registro manual en papel y planillas Excel por un sistema que controla productos, lotes, entradas, salidas y solicitudes de insumos con trazabilidad por usuario.

> Proyecto de Título **PTY4614-002D · Equipo 3** · Duoc UC, Sede San Bernardo · Docente: Eliana Mallén González

## Contenido

1. [Descripción](#descripción)
2. [Funcionalidades](#funcionalidades)
3. [Tecnologías](#tecnologías)
4. [Arquitectura](#arquitectura)
5. [Estructura del repositorio](#estructura-del-repositorio)
6. [Ejecución local](#ejecución-local)
7. [Pruebas](#pruebas)
8. [Roles y accesos](#roles-y-accesos)
9. [Metodología](#metodología)
10. [Integrantes y roles](#integrantes-y-roles)
11. [Documentación](#documentación)
12. [Estado del proyecto](#estado-del-proyecto)

## Descripción

La bodega del restaurante se gestiona con notas en papel y planillas Excel. Eso provoca pérdida de trazabilidad de los insumos, mermas de entre 60.000 y 80.000 pesos al mes y 17 a 18 horas mensuales de conteos manuales para reportar al sistema SAP.

Smart RDP digitaliza ese flujo:

- Registra productos, lotes con fecha de vencimiento y cada movimiento de stock.
- Aplica **FEFO** (primero en vencer, primero en salir) en las salidas.
- Permite que el personal pida insumos y los devuelva, y que el bodeguero apruebe cada solicitud.
- Entrega reportes de consumo y de vencimientos para sustentar el reporte a SAP.

**Fuera de alcance:** integración directa con SAP, módulos de venta (POS) y modelos de IA.

## Funcionalidades

| Módulo | Qué permite |
|---|---|
| **Productos** | Alta, edición, deshabilitación y reactivación (borrado lógico); código PTV (vinos y licores) o código numérico según la categoría; exportación a Excel y PDF. |
| **Catálogos** | Categorías (con la marca «Requiere código PTV») y unidades de medida. |
| **Movimientos** | Entradas con lote y vencimiento, salidas por FEFO, historial con filtros y calendario mensual. |
| **Stock y alertas** | Estado de stock (normal, bajo, crítico, agotado) y alertas de productos próximos a vencer. |
| **Solicitudes** | El empleado crea pedidos y devoluciones; el bodeguero los aprueba o rechaza ajustando la cantidad realmente entregada. Cada aprobación genera el movimiento correspondiente. |
| **Analítica** | Consumo por periodo, productos más usados, vencimientos y exportación de movimientos, con filtros por fechas y categoría. |
| **Usuarios** | El administrador crea, edita y desactiva bodegueros y empleados y define sus contraseñas. |
| **Auditoría** | Historial de cambios con Hibernate Envers: quién cambió qué y cuándo, sin guardar contraseñas. |

## Tecnologías

| Capa | Tecnología |
|---|---|
| Backend | Java 21 · Spring Boot 4.1.1 · Spring Web MVC · Spring Security con JWT (jjwt) · Spring Data JPA con Hibernate · Hibernate Envers · Bean Validation · Lombok |
| Reportes | Apache POI (Excel) · OpenPDF (PDF) |
| Frontend | Angular 22 · TypeScript · RxJS · W3.CSS · Font Awesome · alertifyjs · ngx-spinner |
| Base de datos | PostgreSQL |
| Herramientas | Maven Wrapper · npm · Docker Compose (solo para PostgreSQL) · Git y GitHub |

Todo el software es de código abierto y no requiere licencias.

## Arquitectura

Arquitectura **cliente-servidor**: una aplicación de una sola página (SPA) en Angular consume una API REST de Spring Boot que persiste en PostgreSQL. El backend es un monolito modular en capas (controlador, servicio, repositorio) con un paquete por dominio. La seguridad es sin estado: cada petición lleva un token JWT y el servidor valida el rol antes de llegar al controlador.

```mermaid
flowchart LR
    SPA["Angular 22 (SPA)<br/>navegador del restaurante"] -- "HTTP y JSON<br/>token JWT" --> API["Spring Boot 4<br/>API REST en capas"]
    API -- "JDBC" --> DB[("PostgreSQL")]
```

El detalle completo (diagramas de casos de uso, clases, secuencia y despliegue) está en [Diagramas UML](FASE%202/EVIDENCIAS%20PROYECTO/EVIDENCIAS%20DE%20DOCUMENTACI%C3%93N/08-DIAGRAMAS-UML.md), y el esquema de la base en el [Modelo de datos](FASE%202/EVIDENCIAS%20PROYECTO/EVIDENCIAS%20DE%20SISTEMA/BASE%20DE%20DATOS/07-MODELO-DE-DATOS.md).

## Estructura del repositorio

```text
.
├── FASE 1/                          Definición del proyecto
│   ├── EVIDENCIAS INDIVIDUALES/     Autoevaluaciones y diarios de reflexión
│   └── EVIDENCIAS GRUPALES/         Formativa, definición del proyecto APT y planilla de evaluación
├── FASE 2/                          Desarrollo del proyecto
│   ├── EVIDENCIAS INDIVIDUALES/     Diarios de reflexión y autoevaluaciones
│   ├── EVIDENCIAS GRUPALES/         Guías 2.4 y 2.6 y planillas de evaluación
│   └── EVIDENCIAS PROYECTO/
│       ├── PRESENTACIÓN PROYECTO.pptx
│       ├── EVIDENCIAS DE DOCUMENTACIÓN/   Metodología, planes, minutas, diagramas y planillas del cliente
│       └── EVIDENCIAS DE SISTEMA/
│           ├── APLICACIÓN/
│           │   ├── backend/         API REST (Spring Boot)
│           │   └── frontend/        Aplicación web (Angular)
│           └── BASE DE DATOS/       Modelo de datos y diagramas de la base
└── FASE-3/                          Cierre y presentación final
```

Dentro de `FASE 2/EVIDENCIAS PROYECTO/EVIDENCIAS DE SISTEMA/APLICACIÓN/backend/src/main/java/com/smartrdp/backend` hay un paquete por dominio: `producto`, `movimiento`, `solicitud`, `usuario`, `catalogo`, `analitica`, `auditoria`, `publico`, además de `config`, `security`, `exception` y `seed`.

## Ejecución local

POR HACER...

## Pruebas

El backend tiene **63 pruebas de flujo** que recorren el sistema a través de su API (iniciar sesión, crear productos, registrar entradas y salidas, pedir, aprobar, devolver, importar desde Excel, analítica). Cada una indica en su nombre la historia de usuario que cubre. Se ejecutan contra una base PostgreSQL real, porque las reglas principales (stock que no queda negativo, operaciones inmutables, lotes por vencimiento) viven en la base como restricciones y triggers.

Requisitos: Java 21 y un PostgreSQL 18 local con un usuario `postgres` y una base vacía llamada `smartrdp_test`. Las pruebas recrean el esquema con Flyway en cada corrida, así que esa base no debe contener datos que importen.

Desde la carpeta `FASE 2/EVIDENCIAS PROYECTO/EVIDENCIAS DE SISTEMA/APLICACIÓN/backend`:

```bash
SMARTRDP_DB_PASSWORD=<clave-de-postgres> ./mvnw test
```

La clave de la base se entrega solo por la variable de entorno `SMARTRDP_DB_PASSWORD`; no está en el repositorio. El informe de cobertura (JaCoCo) queda en `target/site/jacoco/index.html`.

Resultado de la última corrida (12-11-2026): 63 de 63 aprobadas y 92,9 % de cobertura de líneas. El detalle está en `FASE 2/EVIDENCIAS PROYECTO/EVIDENCIAS DE DOCUMENTACIÓN`: [INFORME DE PRUEBAS](FASE%202/EVIDENCIAS%20PROYECTO/EVIDENCIAS%20DE%20DOCUMENTACI%C3%93N/INFORME%20DE%20PRUEBAS.docx), [MATRIZ DE TRAZABILIDAD](FASE%202/EVIDENCIAS%20PROYECTO/EVIDENCIAS%20DE%20DOCUMENTACI%C3%93N/MATRIZ%20DE%20TRAZABILIDAD.xlsx) (historia ↔ prueba) y [CASOS DE PRUEBA SMARTRDP](FASE%202/EVIDENCIAS%20PROYECTO/EVIDENCIAS%20DE%20DOCUMENTACI%C3%93N/CASOS%20DE%20PRUEBA%20SMARTRDP.docx) (casos manuales).

## Roles y accesos

| Rol | Acceso |
|---|---|
| **Administrador** | Analítica y Usuarios. |
| **Bodeguero** | Productos, catálogos, movimientos, stock, alertas, solicitudes (aprobación y rechazo), historial y Analítica. No gestiona usuarios ni crea solicitudes. |
| **Empleado** | Vista pública: crea pedidos, devuelve productos y consulta sus solicitudes, identificándose con su nombre y contraseña en cada acción. No inicia sesión. |

## Metodología

El proyecto se desarrolla con **Extreme Programming (XP)**, adaptada con prácticas de Gobernanza, Riesgo y Cumplimiento (GRC):

- Iteraciones semanales con reunión de planificación y de aceptación con el cliente (Planning Game).
- Entregas pequeñas agrupadas en releases.
- Historias de usuario con criterios de aceptación Given-When-Then.
- Revisión de código cruzada en lugar de programación en parejas, por la disponibilidad asimétrica del equipo.
- Pruebas de aceptación por historia. El TDD se declaró en la Fase 1 pero no avanzó a la par del desarrollo; las pruebas se automatizaron al cierre de la Fase 2 (ver [Pruebas](#pruebas)).
- Rol de Tracker para calidad y auditoría de cumplimiento.

El detalle está en [Metodología XP](FASE%202/EVIDENCIAS%20PROYECTO/EVIDENCIAS%20DE%20DOCUMENTACI%C3%93N/01-METODOLOGIA-XP.docx).

## Integrantes y roles

| Integrante | Rol en el proyecto |
|---|---|
| Ezequiel Suárez | Programador (backend y frontend) |
| Lucas Villanueva | Programador (desarrollo y pruebas) |
| Carolina Pandorfa | Tracker (calidad y GRC), documentación y seguimiento |
| Eliana Mallén González | Coach (docente) |
| Administración y encargado de bodega del restaurante | Usuarios clave: priorizan y aceptan las historias |

## Documentación

POR HACER...

## Estado del proyecto

El proyecto está en la **Fase 2 (desarrollo)**.

**Implementado:** productos y catálogos, movimientos con lotes y FEFO, estado de stock y alertas, solicitudes con pedidos y devoluciones, Analítica, administración de usuarios, vista pública del empleado, roles y auditoría.

**Pendiente:** carga del catálogo real del cliente y datos de demostración, vista del historial de entregas con exportación a PDF, sugerencia visual del lote FEFO en la salida, definición con el cliente de los reportes de Analítica para pedidos y devoluciones, y la decisión de infraestructura de despliegue (escritorio, servidor propio o red local).
