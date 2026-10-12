# Diagramas UML — Smart RDP

> Proyecto de Título PTY4614-002D · Equipo 3 · Duoc UC, Sede San Bernardo
> Versión: borrador para revisión · 12-10-2026

## 1. Alcance

El proyecto documenta cinco diagramas UML, suficientes para describir qué hace el sistema, cómo se estructura y cómo se ejecutan sus dos flujos principales. Cubren cuatro de los 14 tipos de diagrama de UML 2.5.1 (casos de uso, clases, secuencia y despliegue) y se complementan con el [modelo de datos](07-Modelo-de-Datos.md).

| N.º | Diagrama | Tipo UML | Qué muestra |
|---|---|---|---|
| 1 | Casos de uso | Comportamiento | Qué puede hacer cada rol. |
| 2 | Clases del dominio | Estructura | Entidades de negocio y sus relaciones. |
| 3 | Aprobar un pedido | Interacción (secuencia) | Cómo el bodeguero aprueba y se descuenta el stock por FEFO. |
| 4 | Pedido del empleado | Interacción (secuencia) | Cómo el empleado se identifica y crea un pedido. |
| 5 | Arquitectura y despliegue | Estructura (despliegue, dibujado como diagrama de flujo) | Componentes del sistema y dónde corren. |

Los diagramas están escritos en [Mermaid](https://mermaid.js.org), que GitHub dibuja directamente en el navegador. El diagrama de casos de uso se dibuja con la sintaxis de diagramas de flujo de Mermaid siguiendo la notación UML (actores, casos de uso, línea de asociación y relaciones `include`), porque la sintaxis nativa de casos de uso de Mermaid (`usecase-beta`) es reciente y puede no estar disponible en la versión que usa GitHub.

## 2. Diagrama de casos de uso

El **Administrador** ve Analítica y administra usuarios. El **Bodeguero** administra todo lo demás pero no a los usuarios y es el único que aprueba solicitudes. El **Empleado** no inicia sesión: usa la vista pública y se identifica con su nombre y su contraseña en cada acción.

```mermaid
flowchart LR
    adm(["Administrador"])
    bod(["Bodeguero"])
    emp(["Empleado"])

    subgraph SIS["Sistema Smart RDP"]
        uc1(["Administrar usuarios"])
        uc2(["Consultar analítica y exportar movimientos"])
        uc3(["Gestionar productos"])
        uc4(["Administrar categorías y unidades de medida"])
        uc5(["Registrar entrada con lote y vencimiento"])
        uc6(["Registrar salida por FEFO"])
        uc7(["Consultar stock y alertas de vencimiento"])
        uc8(["Aprobar o rechazar solicitudes"])
        uc9(["Consultar historial de movimientos y solicitudes"])
        uc10(["Crear pedido de insumos"])
        uc11(["Devolver productos"])
        uc12(["Consultar mis solicitudes"])
        uc13(["Registrar movimiento Solicitado o Devolución"])
        uc14(["Verificar identidad con contraseña"])
    end

    adm --- uc1
    adm --- uc2

    bod --- uc2
    bod --- uc3
    bod --- uc4
    bod --- uc5
    bod --- uc6
    bod --- uc7
    bod --- uc8
    bod --- uc9

    emp --- uc10
    emp --- uc11
    emp --- uc12

    uc8 -.->|"«include»"| uc13
    uc10 -.->|"«include»"| uc14
    uc11 -.->|"«include»"| uc14
    uc12 -.->|"«include»"| uc14
```

## 3. Diagrama de clases del dominio

Las nueve entidades heredan de `BaseEntity` (`id`, `createdAt`, `updatedAt`). Solo `Movimiento`, `Solicitud` y `Usuario` tienen historial con Hibernate Envers.

```mermaid
classDiagram
    direction LR

    class BaseEntity {
        <<abstract>>
        -Long id
        -LocalDateTime createdAt
        -LocalDateTime updatedAt
    }
    class Usuario {
        -String nombre
        -String email
        -String password
        -Rol rol
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
        -Integer stockMinimo
        -Integer stockCritico
        -Integer costoUnitario
        -boolean activo
    }
    class Lote {
        -String numeroLote
        -LocalDate fechaVencimiento
        -Integer cantidadOriginal
        -Integer cantidadDisponible
    }
    class Movimiento {
        -TipoMovimiento tipo
        -Integer cantidad
        -String motivo
        -Integer costoUnitario
    }
    class ConsumoLote {
        -Integer cantidad
        -Integer cantidadDevuelta
    }
    class Solicitud {
        -TipoSolicitud tipo
        -EstadoSolicitud estado
        -String observacion
    }
    class DetalleSolicitud {
        -Integer cantidadSolicitada
        -Integer cantidadEntregada
    }
    class Rol {
        <<enumeration>>
        ADMIN
        BODEGUERO
        EMPLEADO
    }
    class TipoMovimiento {
        <<enumeration>>
        ENTRADA
        SALIDA
        SOLICITADO
        DEVOLUCION
    }
    class TipoSolicitud {
        <<enumeration>>
        PEDIDO
        DEVOLUCION
    }
    class EstadoSolicitud {
        <<enumeration>>
        PENDIENTE
        APROBADA
        RECHAZADA
    }

    Categoria "1" -- "*" Producto : clasifica
    UnidadMedida "1" -- "*" Producto : mide
    Producto "1" *-- "*" Lote : tiene
    Producto "1" -- "*" Movimiento : registra
    Lote "0..1" -- "*" Movimiento : afecta
    Usuario "0..1" -- "*" Movimiento : realiza
    Movimiento "1" *-- "*" ConsumoLote : descuenta
    Lote "1" -- "*" ConsumoLote : es consumido en
    Usuario "0..1" -- "*" Solicitud : solicitante
    Usuario "0..1" -- "*" Solicitud : bodeguero
    Solicitud "0..1" -- "*" Solicitud : solicitudOrigen
    Solicitud "1" *-- "*" DetalleSolicitud : contiene
    Producto "1" -- "*" DetalleSolicitud : se pide en
    DetalleSolicitud "0..1" -- "0..1" Movimiento : genera
    Usuario ..> Rol
    Movimiento ..> TipoMovimiento
    Solicitud ..> TipoSolicitud
    Solicitud ..> EstadoSolicitud
```

## 4. Diagrama de secuencia: aprobar un pedido

El bodeguero revisa un pedido pendiente, ajusta si es necesario la cantidad que realmente entrega y lo aprueba. El sistema descuenta del stock por FEFO y deja un movimiento **Solicitado**. Toda la aprobación ocurre en una sola transacción: si falla un descuento, no queda nada a medias.

```mermaid
sequenceDiagram
    autonumber
    actor B as Bodeguero
    participant UI as Angular (historial de solicitudes)
    participant C as SolicitudController
    participant S as SolicitudService
    participant M as MovimientoService
    participant DB as PostgreSQL

    B->>UI: Ajusta las cantidades entregadas y pulsa Aprobar
    UI->>C: PUT /api/solicitudes/{id}/aprobar con token JWT
    C->>S: aprobar(id, request)
    S->>DB: Busca la solicitud y la bloquea (findByIdForUpdate)
    DB-->>S: Solicitud
    alt La solicitud ya no está pendiente
        S-->>C: BusinessException "La solicitud ya fue procesada"
        C-->>UI: 422
    else La solicitud está pendiente
        loop Por cada línea con cantidad entregada mayor que cero
            S->>S: Valida que lo entregado no supere lo solicitado
            S->>M: registrarSolicitado(productoId, cantidad, motivo)
            M->>DB: Lee los lotes del producto ordenados por vencimiento
            alt Stock insuficiente
                M-->>S: BusinessException "Stock insuficiente"
                S-->>C: Se revierte toda la aprobación
                C-->>UI: 422
            else Stock suficiente
                M->>DB: Descuenta de cada lote y guarda consumo_lote
                M->>DB: Guarda el Movimiento de tipo SOLICITADO
                M-->>S: Movimiento
            end
        end
        S->>DB: Marca la solicitud APROBADA y registra al bodeguero
        S-->>C: SolicitudResponse
        C-->>UI: 200 OK
        UI-->>B: Muestra la solicitud aprobada
    end
```

## 5. Diagrama de secuencia: pedido del empleado

El empleado no inicia sesión. Elige su nombre en una lista, escribe su contraseña y envía el pedido. El sistema verifica su identidad en cada acción y limita los intentos fallidos: tras cinco intentos incorrectos el empleado queda bloqueado cinco minutos.

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
    P-->>UI: Nombres de empleados activos y productos
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
        S->>DB: Guarda la Solicitud PENDIENTE y sus detalles
        S-->>P: SolicitudResponse
        P-->>UI: 201 Created
        UI-->>E: Confirma que el pedido quedó pendiente
    end
```

## 6. Diagrama de arquitectura y despliegue

La arquitectura es **cliente-servidor**: una aplicación de una sola página (SPA) en Angular consume una API REST de Spring Boot, que persiste en PostgreSQL. El backend es un **monolito modular en capas** (controlador, servicio, repositorio) con un paquete por dominio: `producto`, `movimiento`, `solicitud`, `usuario`, `catalogo`, `analitica`, `auditoria`, `publico`. La seguridad es sin estado: el cliente envía un token JWT en cada petición y un filtro valida el rol antes de llegar al controlador.

```mermaid
flowchart LR
    subgraph CL["Equipo del restaurante: navegador web"]
        SPA["Aplicación Angular 22 (SPA)<br/>W3.CSS, rutas protegidas por rol,<br/>interceptor que agrega el token JWT"]
    end

    subgraph SRV["Servidor local o red LAN: Spring Boot 4, Java 21"]
        SEC["Seguridad<br/>JwtAuthenticationFilter y SecurityConfig<br/>(roles Administrador, Bodeguero, Empleado)"]
        CTL["Capa web: controladores REST<br/>/api/auth, /api/productos, /api/movimientos,<br/>/api/solicitudes, /api/analitica,<br/>/api/usuarios, /api/publico"]
        SVC["Capa de servicios<br/>reglas de negocio, FEFO y transacciones"]
        REP["Capa de persistencia<br/>Spring Data JPA, Hibernate y Envers"]
        SEC --> CTL --> SVC --> REP
    end

    DB[("PostgreSQL<br/>base smartrdp")]

    SPA -- "HTTP y JSON,<br/>Authorization: Bearer" --> SEC
    REP -- "JDBC" --> DB
```

### Patrón de arquitectura

| Aspecto | Decisión | Motivo |
|---|---|---|
| Estilo general | Cliente-servidor con SPA y API REST | Separa la interfaz de las reglas de negocio y deja el frontend reutilizable. |
| Organización del backend | Monolito modular en capas, un paquete por dominio | Es simple de desplegar en el restaurante y suficiente para un equipo de tres personas. |
| Seguridad | Autenticación con JWT sin estado y control de acceso por rol (RBAC) | El servidor no guarda sesiones y cada ruta declara qué roles la usan. |
| Persistencia | JPA con Hibernate sobre PostgreSQL; auditoría con Envers | Trazabilidad de quién cambió qué y cuándo, sin código manual en la lógica de negocio. |
| Despliegue | Local o en la red LAN del restaurante; PostgreSQL puede levantarse con `docker-compose.yml` | Corresponde a la restricción de conectividad local del proyecto. La decisión final de infraestructura sigue pendiente con el cliente. |

## 7. Referencias

- Object Management Group. (2017). *Unified Modeling Language (UML), versión 2.5.1*. https://www.omg.org/spec/UML/2.5.1
- Mermaid. [Sequence diagrams](https://mermaid.js.org/syntax/sequenceDiagram.html), [Class diagrams](https://mermaid.js.org/syntax/classDiagram.html) y [Flowcharts](https://mermaid.js.org/syntax/flowchart.html).
- GitHub Docs. [Creating diagrams](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-diagrams).
