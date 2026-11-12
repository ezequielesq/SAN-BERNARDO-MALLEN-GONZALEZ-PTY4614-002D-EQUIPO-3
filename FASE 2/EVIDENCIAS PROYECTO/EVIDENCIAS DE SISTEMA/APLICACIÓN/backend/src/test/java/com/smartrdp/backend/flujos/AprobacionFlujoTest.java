package com.smartrdp.backend.flujos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** Flujo G: el bodeguero aprueba o rechaza los pedidos del empleado. */
class AprobacionFlujoTest extends FlujoBase {

    @Test
    @DisplayName("HU-90 Aprobar: se genera la Entrega de pedido y se descuenta el stock por FEFO")
    void aprobarGeneraLaEntregaYDescuentaPorFefo() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        var producto = crearProducto(true);
        // Se ingresa primero el lote que vence más tarde.
        entrada(producto.id(), 5, hoy().plusDays(60), "LEJANO").esperando(201);
        entrada(producto.id(), 5, hoy().plusDays(10), "CERCANO").esperando(201);
        long pedido = pedirOk(empleado, producto.id(), 7);

        var aprobada = aprobar(bodeguero, pedido).esperando(200).json();

        assertThat(aprobada.path("estado").asText()).isEqualTo("APROBADA");
        assertThat(detalleDe(aprobada, producto.id()).path("cantidadEntregada").asInt()).isEqualTo(7);
        assertThat(stockDe(producto.id())).isEqualTo(3);

        // El libro tiene la «Entrega de pedido»: primero el lote que vence antes, hecha por el bodeguero.
        var entregas = movimientosDeTipo(producto.id(), "ENTREGA_PEDIDO");
        assertThat(entregas).hasSize(2);
        assertThat(cantidadDelLote(entregas, "CERCANO")).isEqualTo(5);
        assertThat(cantidadDelLote(entregas, "LEJANO")).isEqualTo(2);
        assertThat(entregas).allMatch(m -> bodeguero.email().equals(m.path("usuarioEmail").asText()));
        assertThat(entregas).allMatch(m -> m.path("nota").asText().contains("Solicitud #" + pedido));

        // Ya no está pendiente y el empleado la ve aprobada.
        assertThat(estaPendiente(pedido)).isFalse();
        assertThat(solicitudDelEmpleado(empleado, pedido).path("estado").asText()).isEqualTo("APROBADA");
    }

    @Test
    @DisplayName("HU-170 Entregar menos de lo pedido: se guardan ambas cantidades y solo se descuenta lo entregado")
    void entregarMenosDeLoPedidoGuardaAmbasCantidades() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        var producto = productoConStock(4);
        long pedido = pedirOk(empleado, producto.id(), 4);
        // Antes de aprobar, alguien más saca una unidad: solo quedan 3.
        salida(producto.id(), 1).esperando(201);

        var aprobada = aprobar(bodeguero, pedido, item(producto.id(), 3)).esperando(200).json();

        var detalle = detalleDe(aprobada, producto.id());
        assertThat(detalle.path("cantidadSolicitada").asInt()).isEqualTo(4);
        assertThat(detalle.path("cantidadEntregada").asInt()).isEqualTo(3);
        assertThat(stockDe(producto.id())).isZero();

        // Ambos valores quedan guardados y el empleado los ve igual.
        var delEmpleado = detalleDe(solicitudDelEmpleado(empleado, pedido), producto.id());
        assertThat(delEmpleado.path("cantidadSolicitada").asInt()).isEqualTo(4);
        assertThat(delEmpleado.path("cantidadEntregada").asInt()).isEqualTo(3);
        assertThat(movimientosDeTipo(producto.id(), "ENTREGA_PEDIDO").stream()
                .mapToInt(m -> m.path("cantidad").asInt()).sum()).isEqualTo(3);
    }

    @Test
    @DisplayName("HU-170 No se puede entregar más de lo solicitado")
    void noSePuedeEntregarMasDeLoSolicitado() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        var producto = productoConStock(10);
        long pedido = pedirOk(empleado, producto.id(), 2);

        var excesiva = aprobar(bodeguero, pedido, item(producto.id(), 3));

        assertThat(excesiva.estado()).isEqualTo(422);
        assertThat(excesiva.mensaje()).contains("No puedes entregar más de lo solicitado");
        assertThat(estaPendiente(pedido)).isTrue();
        assertThat(stockDe(producto.id())).isEqualTo(10);
    }

    @Test
    @DisplayName("HU-90 Rechazar: la solicitud queda rechazada y no afecta el stock")
    void rechazarNoAfectaElStock() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        var producto = productoConStock(10);
        long pedido = pedirOk(empleado, producto.id(), 4);

        rechazar(bodeguero, pedido).esperando(204);

        assertThat(stockDe(producto.id())).isEqualTo(10);
        assertThat(movimientosDeTipo(producto.id(), "ENTREGA_PEDIDO")).isEmpty();
        assertThat(estaPendiente(pedido)).isFalse();
        var delEmpleado = solicitudDelEmpleado(empleado, pedido);
        assertThat(delEmpleado.path("estado").asText()).isEqualTo("RECHAZADA");
        assertThat(detalleDe(delEmpleado, producto.id()).path("cantidadEntregada").isNull()).isTrue();
    }

    @Test
    @DisplayName("HU-90 Aprobar dos veces: la segunda se rechaza y el stock se descuenta una sola vez")
    void aprobarDosVecesSeRechazaLaSegunda() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        var producto = productoConStock(10);
        long pedido = pedirOk(empleado, producto.id(), 4);
        aprobar(bodeguero, pedido).esperando(200);
        assertThat(stockDe(producto.id())).isEqualTo(6);

        var segunda = aprobar(bodeguero, pedido);

        assertThat(segunda.estado()).isEqualTo(422);
        assertThat(segunda.mensaje()).contains("ya fue procesada");
        assertThat(stockDe(producto.id())).isEqualTo(6);
        assertThat(movimientosDeTipo(producto.id(), "ENTREGA_PEDIDO")).hasSize(1);

        // Una solicitud resuelta tampoco se rechaza después, ni una rechazada se aprueba.
        assertThat(rechazar(bodeguero, pedido).estado()).isEqualTo(422);
        long otro = pedirOk(empleado, producto.id(), 1);
        rechazar(bodeguero, otro).esperando(204);
        assertThat(aprobar(bodeguero, otro).estado()).isEqualTo(422);
        assertThat(stockDe(producto.id())).isEqualTo(6);
    }

    @Test
    @DisplayName("HU-90 Stock insuficiente al aprobar: se revierte todo, también los demás productos del pedido")
    void stockInsuficienteAlAprobarRevierteTodo() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        var alcanza = productoConStock(5);
        var seAcaba = productoConStock(5);
        long pedido = pedir(empleado, item(alcanza.id(), 2), item(seAcaba.id(), 3)).esperando(201)
                .json().path("id").asLong();
        // Después del pedido, otra salida deja solo 1 unidad del segundo producto.
        salida(seAcaba.id(), 4).esperando(201);

        var fallida = aprobar(bodeguero, pedido);

        assertThat(fallida.estado()).isEqualTo(422);
        assertThat(fallida.mensaje()).contains("Stock insuficiente");
        // Nada quedó a medias: la solicitud sigue pendiente y ningún producto se descontó.
        assertThat(estaPendiente(pedido)).isTrue();
        assertThat(stockDe(alcanza.id())).isEqualTo(5);
        assertThat(stockDe(seAcaba.id())).isEqualTo(1);
        assertThat(movimientosDeTipo(alcanza.id(), "ENTREGA_PEDIDO")).isEmpty();
        assertThat(movimientosDeTipo(seAcaba.id(), "ENTREGA_PEDIDO")).isEmpty();
        assertThat(solicitudDelEmpleado(empleado, pedido).path("estado").asText()).isEqualTo("PENDIENTE");

        // Ajustando lo entregado a lo que hay, la misma solicitud se puede aprobar.
        aprobar(bodeguero, pedido, item(alcanza.id(), 2), item(seAcaba.id(), 1)).esperando(200);
        assertThat(stockDe(alcanza.id())).isEqualTo(3);
        assertThat(stockDe(seAcaba.id())).isZero();
    }

    private int cantidadDelLote(java.util.List<JsonNode> lineas, String numeroLote) {
        return lineas.stream().filter(m -> numeroLote.equals(m.path("numeroLote").asText()))
                .mapToInt(m -> m.path("cantidad").asInt()).sum();
    }
}
