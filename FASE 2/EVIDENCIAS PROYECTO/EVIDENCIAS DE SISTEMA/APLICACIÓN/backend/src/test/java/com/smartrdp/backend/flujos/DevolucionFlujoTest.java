package com.smartrdp.backend.flujos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Flujo H: devoluciones de insumos de un pedido entregado. */
class DevolucionFlujoTest extends FlujoBase {

    @Test
    @DisplayName("HU-210 Devolución parcial: suma el stock al aprobarla y queda vinculada al pedido original")
    void devolucionParcialSumaElStockYQuedaVinculada() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        var producto = crearProducto(true);
        entrada(producto.id(), 3, hoy().plusDays(10), "CERCANO").esperando(201);
        entrada(producto.id(), 5, hoy().plusDays(60), "LEJANO").esperando(201);
        long pedido = pedirOk(empleado, producto.id(), 6);
        aprobar(bodeguero, pedido).esperando(200);
        assertThat(stockDe(producto.id())).isEqualTo(2);

        var devolucion = devolver(empleado, pedido, item(producto.id(), 2)).esperando(201).json();

        // La devolución nace pendiente y vinculada al pedido; el stock aún no cambia.
        assertThat(devolucion.path("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(devolucion.path("tipo").asText()).isEqualTo("DEVOLUCION");
        assertThat(devolucion.path("solicitudOrigenId").asLong()).isEqualTo(pedido);
        assertThat(stockDe(producto.id())).isEqualTo(2);

        long devolucionId = devolucion.path("id").asLong();
        var aprobada = aprobar(bodeguero, devolucionId).esperando(200).json();
        assertThat(aprobada.path("estado").asText()).isEqualTo("APROBADA");
        assertThat(aprobada.path("solicitudOrigenId").asLong()).isEqualTo(pedido);
        assertThat(stockDe(producto.id())).isEqualTo(4);

        // En el libro: una entrada «Devolución de pedido» que cita el pedido original y repone en el
        // último lote consumido.
        List<JsonNode> devoluciones = movimientosDeTipo(producto.id(), "DEVOLUCION");
        assertThat(devoluciones).isNotEmpty();
        assertThat(devoluciones.stream().mapToInt(m -> m.path("cantidad").asInt()).sum()).isEqualTo(2);
        assertThat(devoluciones).allMatch(m -> m.path("sentido").asInt() == 1);
        assertThat(devoluciones).allMatch(m -> m.path("nota").asText().contains("#" + pedido));
        assertThat(devoluciones).allMatch(m -> "LEJANO".equals(m.path("numeroLote").asText()));

        // El empleado ve cuánto devolvió y cuánto aún puede devolver del pedido.
        var delPedido = detalleDe(solicitudDelEmpleado(empleado, pedido), producto.id());
        assertThat(delPedido.path("cantidadEntregada").asInt()).isEqualTo(6);
        assertThat(delPedido.path("cantidadDevuelta").asInt()).isEqualTo(2);
        assertThat(delPedido.path("devolvible").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("HU-210 Devolver más de lo entregado: rechazado, sumando también las devoluciones anteriores")
    void devolverMasDeLoEntregadoSeRechaza() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        var producto = productoConStock(5);
        var ajeno = productoConStock(5);
        long pedido = pedirOk(empleado, producto.id(), 5);
        // Se entregan 4 de las 5 pedidas.
        aprobar(bodeguero, pedido, item(producto.id(), 4)).esperando(200);
        assertThat(stockDe(producto.id())).isEqualTo(1);

        var excesiva = devolver(empleado, pedido, item(producto.id(), 5));
        assertThat(excesiva.estado()).isEqualTo(422);
        assertThat(excesiva.mensaje()).contains("Solo puedes devolver hasta 4");

        // Tampoco se puede devolver un producto que no estaba en el pedido.
        var otroProducto = devolver(empleado, pedido, item(ajeno.id(), 1));
        assertThat(otroProducto.estado()).isEqualTo(422);
        assertThat(otroProducto.mensaje()).contains("no pertenece a esa solicitud");

        // Lo ya devuelto cuenta: tras devolver 3 quedan 1 por devolver.
        long primera = devolver(empleado, pedido, item(producto.id(), 3)).esperando(201).json().path("id").asLong();
        assertThat(devolver(empleado, pedido, item(producto.id(), 2)).estado())
                .as("con 3 pendientes de aprobar solo queda 1 por pedir").isEqualTo(422);
        aprobar(bodeguero, primera).esperando(200);
        var tras = devolver(empleado, pedido, item(producto.id(), 2));
        assertThat(tras.estado()).isEqualTo(422);
        assertThat(tras.mensaje()).contains("Solo puedes devolver hasta 1");
        devolver(empleado, pedido, item(producto.id(), 1)).esperando(201);

        // Solo la devolución aprobada movió stock: 1 + 3.
        assertThat(stockDe(producto.id())).isEqualTo(4);
        assertThat(stockDe(ajeno.id())).isEqualTo(5);
    }

    @Test
    @DisplayName("HU-210 Devolver un pedido no entregado: rechazado")
    void devolverUnPedidoNoEntregadoSeRechaza() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        Cuenta otroEmpleado = crearEmpleado();
        var producto = productoConStock(10);

        long pendiente = pedirOk(empleado, producto.id(), 2);
        var devolviendoPendiente = devolver(empleado, pendiente, item(producto.id(), 1));
        assertThat(devolviendoPendiente.estado()).isEqualTo(422);
        assertThat(devolviendoPendiente.mensaje()).contains("pedidos aprobados");

        long rechazado = pedirOk(empleado, producto.id(), 2);
        rechazar(bodeguero, rechazado).esperando(204);
        var devolviendoRechazado = devolver(empleado, rechazado, item(producto.id(), 1));
        assertThat(devolviendoRechazado.estado()).isEqualTo(422);
        assertThat(devolviendoRechazado.mensaje()).contains("pedidos aprobados");

        // Tampoco se puede devolver un pedido ajeno, aunque esté entregado.
        long entregadoAOtro = pedirOk(otroEmpleado, producto.id(), 2);
        aprobar(bodeguero, entregadoAOtro).esperando(200);
        var ajeno = devolver(empleado, entregadoAOtro, item(producto.id(), 1));
        assertThat(ajeno.estado()).isEqualTo(422);
        assertThat(ajeno.mensaje()).contains("no existe o no es tuya");

        // Nada se movió salvo la entrega al otro empleado.
        assertThat(stockDe(producto.id())).isEqualTo(8);
        assertThat(movimientosDeTipo(producto.id(), "DEVOLUCION")).isEmpty();
    }
}
