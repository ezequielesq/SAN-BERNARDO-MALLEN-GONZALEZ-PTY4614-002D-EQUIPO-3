# Diagramas UML — Smart RDP

> Proyecto de Título PTY4614-002D · Equipo 3 · Duoc UC, Sede San Bernardo
> Versión: borrador para revisión · 26-11-2026 (se actualizó con proveedores y la consulta de lotes)

## 1. Alcance

El proyecto documenta seis diagramas UML, suficientes para describir qué hace el sistema, cómo se estructura y cómo se ejecutan sus dos flujos principales. Cubren cinco de los 14 tipos de diagrama de UML 2.5.1 (casos de uso, clases, secuencia, componentes y despliegue) y se complementan con el [modelo de datos](../EVIDENCIAS%20DE%20SISTEMA/BASE%20DE%20DATOS/07-MODELO-DE-DATOS.md).

| N.º | Diagrama | Tipo UML | Qué muestra |
|---|---|---|---|
| 1 | Casos de uso | Comportamiento | Qué puede hacer cada rol. |
| 2 | Clases del dominio | Estructura | Entidades de negocio y sus relaciones. |
| 3 | Aprobar un pedido | Interacción (secuencia) | Cómo el bodeguero aprueba y se descuenta el stock por FEFO. |
| 4 | Pedido del empleado | Interacción (secuencia) | Cómo el empleado se identifica y crea un pedido. |
| 5 | Arquitectura y despliegue | Estructura (despliegue, dibujado como diagrama de flujo) | Capas del sistema y dónde corren. |
| 6 | Componentes | Estructura (componentes, dibujado como diagrama de flujo) | Módulos del frontend y del backend, sus dependencias y las interfaces entre ellos. |

Los diagramas están escritos en [Mermaid](https://mermaid.js.org), que GitHub dibuja directamente en el navegador. El diagrama de casos de uso se dibuja con la sintaxis de diagramas de flujo de Mermaid siguiendo la notación UML (actores, casos de uso, línea de asociación y relaciones `include` y `extend`), porque la sintaxis nativa de casos de uso de Mermaid (`usecase-beta`) es reciente y puede no estar disponible en la versión que usa GitHub.

## 2. Diagrama de casos de uso

El **Administrador** administra usuarios, usa el panel de analítica y consulta en solo lectura productos, movimientos, stock, alertas, lotes y calendario. El **Bodeguero** administra todo lo demás pero no a los usuarios, es el único que aprueba solicitudes, registra las entradas (una compra exige el costo unitario, que alimenta el historial de precios, y el proveedor), puede cargarlas desde una planilla Excel, mantiene el registro de proveedores, lleva el calendario de eventos de la bodega y también usa el panel de analítica. El **Empleado** no inicia sesión: usa la vista pública y se identifica con su nombre y su contraseña en cada acción; no accede al panel.

El panel de analítica es un caso base, **Consultar panel de analítica**, cuyos bloques (consumo neto, pérdidas, solicitado, pedidos por persona y productos más usados) comparten un mismo filtro: rango de fechas, agrupación por día, semana o mes, categoría, productos, persona y proveedor; además muestra las compras por proveedor. Siempre incluye los vencimientos próximos; el historial de precios de compra (al elegir un producto, con su comparación por proveedor), los productos clave (una lista propia de cada usuario) y la exportación a Excel con los filtros aplicados son extensiones que el usuario activa cuando las necesita.

**Consultar lotes** lo usan el Administrador y el Bodeguero: lista los lotes con filtros (producto, categoría, proveedor, estado, número de lote y días por vencer), en orden FEFO, y abre el detalle de un lote con su trazabilidad; solo lee, porque los lotes nacen con las entradas. **Gestionar proveedores** (crear, editar, deshabilitar y reactivar, con el RUT validado) es del Bodeguero, igual que las categorías y las unidades de medida.

```mermaid
flowchart LR
    adm(["Administrador"])
    bod(["Bodeguero"])
    emp(["Empleado"])

    subgraph SIS["Sistema Smart RDP"]
        uc1(["Administrar usuarios"])
        uc17(["Consultar productos, movimientos y calendario en solo lectura"])
        uc7(["Consultar stock y alertas de vencimiento"])
        uc3(["Gestionar productos"])
        uc4(["Administrar categorías y unidades de medida"])
        uc22(["Gestionar proveedores"])
        uc5(["Registrar entrada con lote, vencimiento, costo y proveedor de la compra"])
        uc23(["Consultar lotes y su trazabilidad"])
        uc6(["Registrar salida por FEFO"])
        uc8(["Aprobar o rechazar solicitudes"])
        uc9(["Consultar historial de movimientos y solicitudes"])
        uc10(["Crear pedido de insumos"])
        uc11(["Devolver productos"])
        uc12(["Consultar mis solicitudes"])
        uc13(["Registrar operación Entrega de pedido o Devolución"])
        uc14(["Verificar identidad con contraseña"])
        uc15(["Cargar entradas desde Excel"])
        uc16(["Gestionar eventos del calendario"])
        subgraph PAN["Panel de analítica"]
            uc2(["Consultar panel de analítica con filtros compartidos"])
            uc18(["Ver vencimientos próximos"])
            uc19(["Ver historial de precios de compra y compararlo por proveedor"])
            uc20(["Definir mis productos clave"])
            uc21(["Exportar movimientos a Excel con los filtros"])
        end
    end

    adm --- uc1
    adm --- uc17
    adm --- uc2
    adm --- uc7
    adm --- uc23

    bod --- uc2
    bod --- uc7
    bod --- uc3
    bod --- uc4
    bod --- uc22
    bod --- uc23
    bod --- uc5
    bod --- uc6
    bod --- uc8
    bod --- uc9
    bod --- uc15
    bod --- uc16

    emp --- uc10
    emp --- uc11
    emp --- uc12

    uc8 -.->|"«include»"| uc13
    uc10 -.->|"«include»"| uc14
    uc11 -.->|"«include»"| uc14
    uc12 -.->|"«include»"| uc14
    uc2 -.->|"«include»"| uc18
    %% Enlaces invisibles (~~~): solo ordenan el dibujo, no son relaciones UML.
    adm ~~~ uc19
    adm ~~~ uc20
    adm ~~~ uc21
    bod ~~~ uc19
    bod ~~~ uc20
    bod ~~~ uc21
    emp ~~~ uc21
    uc19 -.->|"«extend»"| uc2
    uc20 -.->|"«extend»"| uc2
    uc21 -.->|"«extend»"| uc2
```

## 3. Diagrama de clases del dominio

Todas las entidades de negocio heredan de `BaseEntity` (`id`, `createdAt`, `updatedAt`). `Evento` es una agenda compartida de los bodegueros, sin historial ni triggers. `Proveedor` (migración V5) es el mantenedor de a quién se le compra: tiene RUT normalizado y único, nombre único sin distinguir mayúsculas y estado `activo`; hereda de `BaseEntity` y no de la base de los catálogos de nombre corto (`CatalogoEntity`), porque necesita un nombre de 100 caracteres y una unicidad distinta. Cada `Lote` tiene cero o un `Proveedor`: es obligatorio en una compra (regla del servicio, no de la base) y los lotes anteriores a V5 no lo tienen. Solo `Solicitud` y `Usuario` tienen historial con Hibernate Envers; `Operacion`, `Movimiento` y `Lote` son inmutables (`@Immutable`, y triggers en la base). El stock no es un atributo: se calcula con vistas. `Lote.costoUnitario` es el costo con que entró el lote: en una compra es obligatorio y de él sale el historial de precios del panel; `Producto.costoUnitario` es solo un valor de referencia. `ProductoClave` (migración V4) es la lista personal de productos que cada usuario sigue en el panel de analítica: une un `Usuario` con un `Producto`, una sola vez por par, sin historial y con borrado en cascada.

```mermaid
classDiagram
    direction LR

    class BaseEntity {
        <<abstract>>
        -Long id
        -LocalDateTime createdAt
        -LocalDateTime updatedAt
    }
    class Rol {
        -String codigo
        -String nombre
    }
    class Usuario {
        -String nombre
        -String email
        -String password
        -boolean activo
    }
    class Categoria {
        -String nombre
        -boolean requierePtv
        -boolean activo
    }
    class UnidadMedida {
        -String nombre
        -boolean activo
    }
    class Producto {
        -String nombre
        -String codigoPtv
        -String codigo
        -boolean esPerecible
        -int stockMinimo
        -int stockCritico
        -Integer costoUnitario
        -boolean activo
    }
    class Lote {
        -String numeroLote
        -LocalDate fechaVencimiento
        -Integer costoUnitario
    }
    class TipoOperacion {
        -String codigo
        -String nombre
        -short sentido
        -String solicitudRequerida
        -boolean seleccionable
    }
    class Operacion {
        -String nota
        +agregarMovimiento(Lote, int)
    }
    class Movimiento {
        -int cantidad
    }
    class EstadoSolicitud {
        -String codigo
        -String nombre
    }
    class Solicitud {
        -String observacion
        +esDevolucion() boolean
    }
    class DetalleSolicitud {
        -int cantidadSolicitada
    }
    class Evento {
        -String titulo
        -LocalDate fecha
        -LocalTime hora
        -String descripcion
    }
    class ProductoClave
    class Proveedor {
        -String rut
        -String nombre
        -boolean activo
    }


    Rol "1" <-- "*" Usuario : tiene
    Categoria "1" <-- "*" Producto : clasifica
    UnidadMedida "1" <-- "*" Producto : mide
    Producto "1" <-- "*" Lote : tiene
    Proveedor "0..1" <-- "*" Lote : provee
    Producto "1" <-- "*" ProductoClave : es clave
    Usuario "1" <-- "*" ProductoClave : marca
    TipoOperacion "1" <-- "*" Operacion : clasifica
    Usuario "1" <-- "*" Operacion : registra
    Operacion "1" *-- "1..*" Movimiento : contiene
    Lote "1" <-- "*" Movimiento : afecta
    Operacion "0..1" <-- "0..1" Operacion : corrige
    Solicitud "0..1" <-- "0..1" Operacion : se cumple con
    EstadoSolicitud "1" <-- "*" Solicitud : estado
    Usuario "1" <-- "*" Solicitud : solicita
    Usuario "0..1" <-- "*" Solicitud : resuelve
    Solicitud "0..1" <-- "*" Solicitud : devolución de
    Solicitud "1" *-- "1..*" DetalleSolicitud : contiene
    Producto "1" <-- "*" DetalleSolicitud : se pide en
    Usuario "1" <-- "*" Evento : crea
```

## 4. Diagrama de secuencia: aprobar un pedido

El bodeguero revisa un pedido pendiente, ajusta si es necesario la cantidad que realmente entrega y lo aprueba. El sistema descuenta del stock por FEFO y deja una operación **Entrega de pedido** (`ENTREGA_PEDIDO`). Toda la aprobación ocurre en una sola transacción: si falla un descuento, no queda nada a medias.

```mermaid
sequenceDiagram
    autonumber
    actor B as Bodeguero
    participant UI as Angular (historial de solicitudes)
    participant C as SolicitudController
    participant S as SolicitudService
    participant M as MovimientoService
    participant DB as PostgreSQL

    B->>UI: Ajusta lo que entrega por producto (puede agregar otros) y pulsa Aprobar
    UI->>C: PUT /api/solicitudes/{id}/aprobar {items: [{productoId, cantidad}]} con token JWT
    C->>S: aprobar(id, request)
    S->>DB: Busca la solicitud y la bloquea (FOR UPDATE)
    alt La solicitud ya no está pendiente
        S-->>C: BusinessException "La solicitud ya fue procesada"
        C-->>UI: 422
    else La solicitud está pendiente
        S->>M: entregarPedido(solicitud, bodeguero, entregas por producto)
        M->>DB: Bloquea los lotes de cada producto (FOR UPDATE, orden por id)
        M->>DB: Lee los saldos (vista saldo_lote) y reparte por FEFO
        alt Stock insuficiente
            M-->>S: BusinessException "Stock insuficiente"
            S-->>C: Se revierte toda la aprobación
            C-->>UI: 422
        else Stock suficiente
            M->>DB: Inserta la Operación ENTREGA_PEDIDO con un Movimiento por lote
            S->>DB: Marca la solicitud APROBADA y registra al bodeguero
            Note over S,DB: Al confirmar, los triggers diferidos verifican saldo ≥ 0 y solicitud aprobada ⇔ operación
            S-->>C: SolicitudResponse
            C-->>UI: 200 OK
            UI-->>B: Muestra la solicitud aprobada
        end
    end
```

## 5. Diagrama de secuencia: pedido del empleado

El empleado no inicia sesión. Elige su nombre en una lista, ve solo los productos con stock y cuánto hay disponible de cada uno, escribe su contraseña y envía el pedido. El sistema verifica su identidad en cada acción y limita los intentos fallidos: tras cinco intentos incorrectos el empleado queda bloqueado cinco minutos. Antes de guardar el pedido comprueba que cada cantidad no supere el stock disponible; el stock no se reserva.

```mermaid
sequenceDiagram
    autonumber
    actor E as Empleado
    participant UI as Angular (vista pública)
    participant P as PublicoController
    participant V as VerificadorIdentidad
    participant S as SolicitudService
    participant DB as PostgreSQL

    E->>UI: Abre la vista pública del empleado
    UI->>P: GET /api/publico/empleados y /api/publico/productos
    P-->>UI: Nombres de empleados activos y productos con stock (con su disponible)
    E->>UI: Elige su nombre, agrega productos y escribe su contraseña
    UI->>P: POST /api/publico/solicitudes (empleadoId, password, items)
    P->>V: verificar(empleadoId, password)
    V->>DB: Busca al empleado activo con rol EMPLEADO
    V->>V: Reserva el intento y comprueba si está bloqueado
    alt Bloqueado o contraseña incorrecta
        V-->>P: BusinessException "Usuario o contraseña incorrectos" o "Demasiados intentos"
        P-->>UI: 422 con el mensaje
        UI-->>E: Muestra el error
    else Contraseña correcta
        V-->>P: Empleado verificado y contador de fallos en cero
        P->>P: Fija el correo del empleado como autor de la auditoría
        P->>S: crearPedido(empleadoId, items)
        S->>DB: Lee el stock disponible de cada producto (vista stock_producto)
        alt Algún producto sin stock o con cantidad mayor que el disponible
            S-->>P: BusinessException "Solo hay N de producto"
            P-->>UI: 422 con el mensaje
            UI-->>E: Muestra el error
        else Hay stock para todos los productos
            S->>DB: Guarda la Solicitud PENDIENTE y sus detalles
            S-->>P: SolicitudResponse
            P-->>UI: 201 Created
            UI-->>E: Confirma que el pedido quedó pendiente
        end
    end
```

## 6. Diagrama de arquitectura y despliegue

La arquitectura es **cliente-servidor**: una aplicación de una sola página (SPA) en Angular consume una API REST de Spring Boot, que persiste en PostgreSQL. El backend es un **monolito modular en capas** (controlador, servicio, repositorio) con un paquete por dominio: `producto`, `movimiento`, `solicitud`, `usuario`, `catalogo`, `analitica`, `auditoria`, `evento`, `publico`. La seguridad es sin estado: el cliente envía un token JWT en cada petición y un filtro valida el rol antes de llegar al controlador. El panel de analítica se sirve bajo `/api/analitica/panel/*` (resumen, consumo por período, productos más usados, pedidos por persona, precios, compras por proveedor y productos clave), junto a `/api/analitica/vencimientos` y `/api/analitica/exportar-movimientos`; todo `/api/analitica` es solo para Administrador y Bodeguero.

```mermaid
flowchart LR
    subgraph CL["Equipo del restaurante: navegador web"]
        SPA["Aplicación Angular 22 (SPA)<br/>W3.CSS, rutas protegidas por rol,<br/>interceptor que agrega el token JWT"]
    end

    subgraph SRV["Servidor local o red LAN: Spring Boot 4, Java 21"]
        SEC["Seguridad<br/>JwtAuthenticationFilter y SecurityConfig<br/>(roles Administrador, Bodeguero, Empleado)"]
        CTL["Capa web: controladores REST<br/>/api/auth, /api/productos, /api/movimientos,<br/>/api/categorias, /api/unidades-medida,<br/>/api/proveedores, /api/lotes,<br/>/api/solicitudes, /api/auditoria,<br/>/api/analitica y /api/analitica/panel,<br/>/api/usuarios, /api/publico,<br/>/api/eventos"]
        SVC["Capa de servicios<br/>reglas de negocio, FEFO y transacciones"]
        REP["Capa de persistencia<br/>Spring Data JPA, Hibernate y Envers"]
        SEC --> CTL --> SVC --> REP
    end

    DB[("PostgreSQL<br/>base smartrdp<br/>esquema con Flyway (V1 a V5)")]

    SPA -- "HTTP y JSON,<br/>Authorization: Bearer" --> SEC
    REP -- "JDBC" --> DB
```

### Patrón de arquitectura

| Aspecto | Decisión | Motivo |
|---|---|---|
| Estilo general | Cliente-servidor con SPA y API REST | Separa la interfaz de las reglas de negocio y deja el frontend reutilizable. |
| Organización del backend | Monolito modular en capas, un paquete por dominio | Es simple de desplegar en el restaurante y suficiente para un equipo de tres personas. |
| Seguridad | Autenticación con JWT sin estado y control de acceso por rol (RBAC) | El servidor no guarda sesiones y cada ruta declara qué roles la usan. |
| Persistencia | JPA con Hibernate sobre PostgreSQL; auditoría con Envers; esquema versionado con Flyway (V4 agrega `productos_clave` y V5 agrega `proveedores`) | Trazabilidad de quién cambió qué y cuándo, sin código manual en la lógica de negocio; cada cambio de esquema queda como una migración. |
| Despliegue | Local o en la red LAN del restaurante; el `docker-compose.yml` levanta PostgreSQL, el backend y el frontend (nginx) | Corresponde a la restricción de conectividad local del proyecto. La decisión final de infraestructura sigue pendiente con el cliente. |

## 7. Diagrama de componentes

Muestra los módulos del sistema y quién depende de quién. En el backend hay un componente por dominio (el mismo nombre que su paquete Java), y cada uno tiene su controlador, servicio y repositorio. Las flechas son las dependencias reales entre paquetes, tomadas de los `import` del código; para que el diagrama se lea, se muestran las principales y se omiten las que pasan por el componente `exception` y por `shared`, que usan casi todos. En el frontend, `features/analitica` aparece aparte porque, además de `core` y `shared`, usa el `ProductoService` de `features/productos` para la lista de productos del filtro.

```mermaid
flowchart LR
    subgraph FE["Frontend: Angular 22 (SPA) servida por nginx o ng serve"]
        direction TB
        FCORE["core<br/>sesión y guardias de ruta,<br/>interceptor que agrega el JWT,<br/>manejo de errores y avisos"]
        FFEAT["features<br/>productos, movimientos y lotes, solicitudes,<br/>calendario, usuarios,<br/>administración (categorías,<br/>unidades y proveedores),<br/>vista pública del empleado"]
        FANA["features/analitica<br/>panel con filtros compartidos,<br/>gráficos, vencimientos, precios,<br/>compras por proveedor<br/>y productos clave"]
        FSH["shared<br/>modal, paginación,<br/>orden, confirmación,<br/>estados, calendario<br/>y proveedores (servicio y RUT)"]
        FFEAT --> FCORE
        FFEAT --> FSH
        FANA --> FCORE
        FANA --> FSH
        FANA -- "ProductoService" --> FFEAT
    end

    subgraph BE["Backend: Spring Boot 4, Java 21 (monolito modular)"]
        direction TB
        SEC["seguridad y configuración<br/>filtro JWT, roles, CORS,<br/>límite de intentos fallidos"]
        subgraph DOM["Componentes de dominio: controlador, servicio y repositorio"]
            direction LR
            PRO["producto"]
            CAT["catalogo<br/>(categorías, unidades<br/>y proveedores)"]
            MOV["movimiento<br/>(operaciones, lotes y su consulta, FEFO)"]
            SOL["solicitud<br/>(pedidos y devoluciones)"]
            USU["usuario"]
            EVE["evento"]
            ANA["analitica<br/>(panel y productos clave)"]
            AUD["auditoria"]
            PUB["publico<br/>(vista del empleado)"]
        end
        EXC["exception<br/>manejo global de errores"]
        SEC --> DOM
        DOM -.-> EXC
        PRO --> CAT
        MOV --> PRO
        MOV --> CAT
        SOL --> MOV
        SOL --> USU
        ANA --> MOV
        ANA --> PRO
        ANA --> USU
        AUD --> SOL
        PUB --> SOL
        PUB --> USU
        EVE --> USU
    end

    DB[("PostgreSQL<br/>tablas, vistas y triggers<br/>(esquema versionado con Flyway)")]

    FCORE -- "HTTP y JSON<br/>Authorization: Bearer" --> SEC
    DOM -- "JDBC<br/>JPA, Hibernate y Envers" --> DB
```

Versión en imagen: [DIAGRAMA DE COMPONENTES.png](DIAGRAMA%20DE%20COMPONENTES.png).

### Interfaces entre componentes

| Desde | Hacia | Interfaz | Detalle |
|---|---|---|---|
| Frontend (`core`) | Seguridad del backend | API REST sobre HTTP y JSON | Cada petición lleva `Authorization: Bearer <token JWT>`; el filtro valida el token y el rol antes de llegar al controlador. |
| Frontend (`features/analitica`) | `analitica` | API REST `/api/analitica/panel/*`, `/api/analitica/vencimientos` y `/api/analitica/exportar-movimientos` | Todos los bloques reciben el mismo filtro (fechas, agrupación, categoría, productos, persona y proveedor); la exportación devuelve un archivo `.xlsx` con esos filtros. Las listas largas usan el paginador compartido `app-paginacion` de `shared`. |
| `analitica` | `movimiento`, `producto` y `usuario` | Llamadas a métodos dentro del mismo proceso | Lee el libro de movimientos y las vistas de inventario, los productos y los usuarios (solicitantes y usuario autenticado, dueño de sus productos clave). |
| Frontend (`features/movimientos` y `features/administracion`) | `movimiento` y `catalogo` | API REST `/api/lotes` (solo lectura: lista con filtros y detalle con trazabilidad) y `/api/proveedores` | El servicio `ProveedorService` de `shared` consulta y mantiene los proveedores; el RUT se valida también en el cliente con el módulo 11. La lectura es para Administrador y Bodeguero; crear, editar, deshabilitar y reactivar un proveedor, solo para el Bodeguero. |
| `movimiento` | `catalogo` | Llamadas a métodos dentro del mismo proceso | Al registrar una entrada, el lote queda asociado a su proveedor: una compra lo exige y debe estar habilitado. |
| Componentes de dominio | PostgreSQL | JDBC, mediante JPA, Hibernate y Envers | El esquema lo crean y versionan las migraciones de Flyway; Envers escribe las tablas de auditoría. |
| Cualquier componente de dominio | `exception` | Manejador global de excepciones | Convierte los errores de negocio, de validación y de base de datos en respuestas HTTP con un mensaje legible. |

### Comunicación entre servicios

No hay comunicación entre servicios porque el sistema no está dividido en servicios: el backend es un **monolito modular** que corre como un solo proceso, y sus componentes de dominio se llaman entre sí con llamadas a métodos dentro de la misma transacción. No existen colas de mensajes, un gestor de API ni llamadas HTTP entre componentes. La única comunicación por red es la del navegador con el backend y la del backend con PostgreSQL, ambas descritas arriba y en el diagrama de la sección 6.

## 8. Referencias

- Object Management Group. (2017). *Unified Modeling Language (UML), versión 2.5.1*. https://www.omg.org/spec/UML/2.5.1
- Mermaid. [Sequence diagrams](https://mermaid.js.org/syntax/sequenceDiagram.html), [Class diagrams](https://mermaid.js.org/syntax/classDiagram.html) y [Flowcharts](https://mermaid.js.org/syntax/flowchart.html).
- GitHub Docs. [Creating diagrams](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-diagrams).
