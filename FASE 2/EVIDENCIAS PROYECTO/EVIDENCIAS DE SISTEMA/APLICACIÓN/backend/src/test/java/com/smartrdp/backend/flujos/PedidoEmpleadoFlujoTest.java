package com.smartrdp.backend.flujos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Flujo F: el empleado pide insumos desde la vista pública, identificándose con su id y contraseña. */
class PedidoEmpleadoFlujoTest extends FlujoBase {

    @Test
    @DisplayName("HU-300 La vista pública lista solo empleados activos y productos con stock")
    void vistaPublicaListaSoloEmpleadosActivosYProductosConStock() {
        Cuenta activo = crearEmpleado();
        Cuenta desactivado = crearEmpleado();
        Cuenta bodeguero = crearBodeguero();
        delete("/api/usuarios/" + desactivado.id(), tokenAdmin()).esperando(204);

        var conStock = productoConStock(5);
        var sinStock = crearProducto(false);
        var agotado = productoConStock(3);
        salida(agotado.id(), 3).esperando(201);
        var deshabilitado = productoConStock(4);
        delete("/api/productos/" + deshabilitado.id(), bodeguero().token()).esperando(204);

        // Sin ninguna sesión.
        var empleados = get("/api/publico/empleados", null).esperando(200).json();
        List<Long> idsEmpleados = new ArrayList<>();
        empleados.forEach(e -> idsEmpleados.add(e.path("id").asLong()));
        assertThat(idsEmpleados).contains(activo.id()).doesNotContain(desactivado.id(), bodeguero.id());
        JsonNode filaActivo = empleados.valueStream().filter(e -> e.path("id").asLong() == activo.id())
                .findFirst().orElseThrow();
        assertThat(filaActivo.path("nombre").asText()).isEqualTo(activo.nombre());
        assertThat(filaActivo.has("password")).isFalse();
        assertThat(filaActivo.has("email")).isFalse();

        var productos = get("/api/publico/productos", null).esperando(200).json();
        List<Long> idsProductos = new ArrayList<>();
        productos.forEach(p -> idsProductos.add(p.path("id").asLong()));
        assertThat(idsProductos).contains(conStock.id())
                .doesNotContain(sinStock.id(), agotado.id(), deshabilitado.id());
        JsonNode filaProducto = productos.valueStream().filter(p -> p.path("id").asLong() == conStock.id())
                .findFirst().orElseThrow();
        assertThat(filaProducto.path("nombre").asText()).isEqualTo(conStock.nombre());
        assertThat(filaProducto.path("stock").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("HU-80 Pedido válido: queda PENDIENTE y no toca el stock")
    void pedidoValidoQuedaPendiente() {
        Cuenta empleado = crearEmpleado();
        var producto = productoConStock(10);

        var respuesta = pedir(empleado, item(producto.id(), 3)).esperando(201);

        var solicitud = respuesta.json();
        assertThat(solicitud.path("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(solicitud.path("tipo").asText()).isEqualTo("PEDIDO");
        assertThat(solicitud.path("solicitanteNombre").asText()).isEqualTo(empleado.nombre());
        assertThat(solicitud.path("detalles")).hasSize(1);
        var detalle = detalleDe(solicitud, producto.id());
        assertThat(detalle.path("cantidadSolicitada").asInt()).isEqualTo(3);
        assertThat(detalle.path("cantidadEntregada").isNull()).isTrue();

        // El bodeguero lo ve entre los pendientes; el pedido no reserva ni descuenta stock.
        assertThat(estaPendiente(solicitud.path("id").asLong())).isTrue();
        assertThat(stockDe(producto.id())).isEqualTo(10);
    }

    @Test
    @DisplayName("HU-300 Contraseña incorrecta: el pedido se rechaza y no queda registrado")
    void contrasenaIncorrectaRechazaElPedido() {
        Cuenta empleado = crearEmpleado();
        var producto = productoConStock(10);

        var respuesta = pedirConClave(empleado, "clave-equivocada", item(producto.id(), 1));

        assertThat(respuesta.estado()).isEqualTo(422);
        assertThat(respuesta.mensaje()).isEqualTo("Usuario o contraseña incorrectos.");
        assertThat(misSolicitudes(empleado).esperando(200).json()).isEmpty();

        // Un empleado que no existe recibe el mismo mensaje: no se revela qué ids existen.
        var inexistente = post("/api/publico/solicitudes", null, objeto("empleadoId", 999_999_999L,
                "password", "lo-que-sea", "items", List.of(item(producto.id(), 1))));
        assertThat(inexistente.estado()).isEqualTo(422);
        assertThat(inexistente.mensaje()).isEqualTo("Usuario o contraseña incorrectos.");

        // La contraseña es obligatoria.
        assertThat(pedirConClave(empleado, "", item(producto.id(), 1)).estado()).isEqualTo(400);

        // Con la contraseña correcta, el mismo pedido sí entra.
        pedir(empleado, item(producto.id(), 1)).esperando(201);
    }

    @Test
    @DisplayName("HU-300 Cinco intentos fallidos: el empleado queda bloqueado, incluso con su contraseña correcta")
    void cincoIntentosFallidosBloqueanAlEmpleado() {
        Cuenta empleado = crearEmpleado();
        Cuenta otro = crearEmpleado();
        var producto = productoConStock(10);

        for (int intento = 1; intento <= 5; intento++) {
            var fallido = pedirConClave(empleado, "mala-" + intento, item(producto.id(), 1));
            assertThat(fallido.estado()).as("intento fallido %d", intento).isEqualTo(422);
            assertThat(fallido.mensaje()).as("intento fallido %d", intento).isEqualTo("Usuario o contraseña incorrectos.");
        }

        // Bloqueado: ya no sirve ni la contraseña correcta, ni en pedidos ni en «mis solicitudes».
        var bloqueado = pedir(empleado, item(producto.id(), 1));
        assertThat(bloqueado.estado()).isEqualTo(422);
        assertThat(bloqueado.mensaje()).contains("Demasiados intentos");
        var consulta = misSolicitudes(empleado);
        assertThat(consulta.estado()).isEqualTo(422);
        assertThat(consulta.mensaje()).contains("Demasiados intentos");
        assertThat(stockDe(producto.id())).isEqualTo(10);

        // El bloqueo es solo de ese empleado.
        pedir(otro, item(producto.id(), 1)).esperando(201);
    }

    @Test
    @DisplayName("HU-80 Cantidad mayor al stock disponible: el pedido se rechaza")
    void cantidadMayorAlStockSeRechaza() {
        Cuenta empleado = crearEmpleado();
        var producto = productoConStock(5);
        var sinStock = crearProducto(false);

        var excesivo = pedir(empleado, item(producto.id(), 6));
        assertThat(excesivo.estado()).isEqualTo(422);
        assertThat(excesivo.mensaje()).contains("Solo hay 5");

        var agotado = pedir(empleado, item(sinStock.id(), 1));
        assertThat(agotado.estado()).isEqualTo(422);
        assertThat(agotado.mensaje()).contains("ya no tiene stock");

        // Si una línea es válida y otra no, no queda nada registrado.
        var mezcla = pedir(empleado, item(producto.id(), 2), item(sinStock.id(), 1));
        assertThat(mezcla.estado()).isEqualTo(422);
        assertThat(misSolicitudes(empleado).esperando(200).json()).isEmpty();

        // Pedir justo lo disponible sí se puede.
        pedir(empleado, item(producto.id(), 5)).esperando(201);
    }

    @Test
    @DisplayName("HU-80 «Mis solicitudes» devuelve solo las del empleado")
    void misSolicitudesDevuelveSoloLasDelEmpleado() {
        Cuenta ana = crearEmpleado();
        Cuenta beto = crearEmpleado();
        var producto = productoConStock(20);

        long deAna1 = pedirOk(ana, producto.id(), 1);
        long deAna2 = pedirOk(ana, producto.id(), 2);
        long deBeto = pedirOk(beto, producto.id(), 3);

        List<Long> idsAna = new ArrayList<>();
        misSolicitudes(ana).esperando(200).json().forEach(s -> idsAna.add(s.path("id").asLong()));
        assertThat(idsAna).containsExactlyInAnyOrder(deAna1, deAna2);

        var solicitudesBeto = misSolicitudes(beto).esperando(200).json();
        assertThat(solicitudesBeto).hasSize(1);
        assertThat(solicitudesBeto.get(0).path("id").asLong()).isEqualTo(deBeto);
        assertThat(solicitudesBeto.get(0).path("solicitanteNombre").asText()).isEqualTo(beto.nombre());

        // Las contraseñas no se mezclan: la de Ana no abre las solicitudes de Beto.
        var cruzada = post("/api/publico/mis-solicitudes", null,
                objeto("empleadoId", beto.id(), "password", ana.clave()));
        assertThat(cruzada.estado()).isEqualTo(422);
    }
}
