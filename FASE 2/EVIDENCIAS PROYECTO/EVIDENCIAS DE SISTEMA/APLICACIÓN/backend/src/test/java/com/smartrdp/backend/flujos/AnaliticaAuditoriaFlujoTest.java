package com.smartrdp.backend.flujos;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flujo J: analítica y auditoría. Cada prueba genera sus datos con flujos reales (entradas, salidas, pedidos
 * y aprobaciones) en una categoría propia y consulta con el filtro de esa categoría o de su solicitud, de modo
 * que solo afirma sobre lo que ella misma creó.
 */
class AnaliticaAuditoriaFlujoTest extends FlujoBase {

    private static final String XLSX = "spreadsheetml.sheet";

    // ================================================================== consumo y más usados (HU-100, 110)

    @Test
    @DisplayName("HU-100 / HU-110 Consumo por rango y productos más usados de mayor a menor, netos de devoluciones y correcciones")
    void consumoPorRangoYMasUsadosOrdenados() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        Catalogo categoria = crearCategoriaPropia(false);
        var p1 = productoEn(categoria, 100);
        var p2 = productoEn(categoria, 100);
        var p3 = productoEn(categoria, 100);

        // P1: una salida manual de 5 y un pedido aprobado de 10 (15 en total).
        salida(bodeguero, p1.id(), 5).esperando(201);
        aprobar(bodeguero, pedirOk(empleado, p1.id(), 10)).esperando(200);
        // P2: un pedido aprobado de 30 del que el empleado devuelve 5 (neto 25).
        long pedidoP2 = pedirOk(empleado, p2.id(), 30);
        aprobar(bodeguero, pedidoP2).esperando(200);
        long devolucion = devolver(empleado, pedidoP2, item(p2.id(), 5)).esperando(201).json().path("id").asLong();
        aprobar(bodeguero, devolucion).esperando(200);
        // P3: una salida de 2 que cuenta y otra de 4 que se corrige (no cuenta, ni ella ni su corrección).
        salida(bodeguero, p3.id(), 2).esperando(201);
        long corregida = salida(bodeguero, p3.id(), 4).esperando(201).json().path("id").asLong();
        post("/api/movimientos/operaciones/" + corregida + "/correccion", bodeguero.token(), null).esperando(201);

        String rango = "desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(1) + "&categoriaId=" + categoria.id();

        // Más usados: ordenados de mayor a menor consumo neto.
        var masUsados = get("/api/analitica/mas-usados?" + rango, bodeguero.token()).esperando(200).json();
        assertThat(masUsados).hasSize(3);
        assertThat(masUsados.get(0).path("productoId").asLong()).isEqualTo(p2.id());
        assertThat(masUsados.get(0).path("totalSalidas").asLong()).isEqualTo(25);
        assertThat(masUsados.get(0).path("nombre").asText()).isEqualTo(p2.nombre());
        assertThat(masUsados.get(0).path("categoriaNombre").asText()).isEqualTo(categoria.nombre());
        assertThat(masUsados.get(1).path("productoId").asLong()).isEqualTo(p1.id());
        assertThat(masUsados.get(1).path("totalSalidas").asLong()).isEqualTo(15);
        assertThat(masUsados.get(2).path("productoId").asLong()).isEqualTo(p3.id());
        assertThat(masUsados.get(2).path("totalSalidas").asLong()).isEqualTo(2);

        // Consumo por producto: entradas y salidas del rango con su valor (costo 1000 por unidad).
        var porProducto = get("/api/analitica/consumo?agruparPor=PRODUCTO&" + rango, bodeguero.token())
                .esperando(200).json();
        assertThat(porProducto).hasSize(3);
        JsonNode c1 = filaConsumo(porProducto, p1.id());
        assertThat(c1.path("nombre").asText()).isEqualTo(p1.nombre());
        assertThat(c1.path("entradaCantidad").asLong()).isEqualTo(100);
        assertThat(c1.path("salidaCantidad").asLong()).isEqualTo(15);
        assertThat(c1.path("entradaValor").asLong()).isEqualTo(100_000);
        assertThat(c1.path("salidaValor").asLong()).isEqualTo(15_000);
        JsonNode c2 = filaConsumo(porProducto, p2.id());
        assertThat(c2.path("salidaCantidad").asLong()).isEqualTo(30);
        assertThat(c2.path("entradaCantidad").asLong()).isEqualTo(105); // 100 comprados + 5 devueltos
        JsonNode c3 = filaConsumo(porProducto, p3.id());
        assertThat(c3.path("salidaCantidad").asLong()).isEqualTo(2);
        assertThat(c3.path("entradaCantidad").asLong()).isEqualTo(100);

        // Consumo por categoría: una sola fila con la suma de los tres productos.
        var porCategoria = get("/api/analitica/consumo?agruparPor=CATEGORIA&" + rango, bodeguero.token())
                .esperando(200).json();
        assertThat(porCategoria).hasSize(1);
        assertThat(porCategoria.get(0).path("id").asLong()).isEqualTo(categoria.id());
        assertThat(porCategoria.get(0).path("nombre").asText()).isEqualTo(categoria.nombre());
        assertThat(porCategoria.get(0).path("salidaCantidad").asLong()).isEqualTo(47);
        assertThat(porCategoria.get(0).path("entradaCantidad").asLong()).isEqualTo(305);

        // Un rango que no incluye hoy no trae movimientos; otra categoría tampoco.
        String pasado = "desde=" + hoy().minusDays(30) + "&hasta=" + hoy().minusDays(10) + "&categoriaId=" + categoria.id();
        assertThat(get("/api/analitica/mas-usados?" + pasado, bodeguero.token()).esperando(200).json()).isEmpty();
        assertThat(get("/api/analitica/consumo?agruparPor=PRODUCTO&" + pasado, bodeguero.token())
                .esperando(200).json()).isEmpty();
        Catalogo otraCategoria = crearCategoriaPropia(false);
        assertThat(get("/api/analitica/mas-usados?desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(1)
                + "&categoriaId=" + otraCategoria.id(), bodeguero.token()).esperando(200).json()).isEmpty();

        // Parámetros obligatorios, y acceso: el administrador consulta; sin sesión, no.
        assertThat(get("/api/analitica/consumo?" + rango, bodeguero.token()).estado()).isEqualTo(400);
        assertThat(get("/api/analitica/mas-usados?categoriaId=" + categoria.id(), bodeguero.token()).estado())
                .isEqualTo(400);
        assertThat(get("/api/analitica/mas-usados?" + rango, tokenAdmin()).esperando(200).json()).hasSize(3);
        assertThat(get("/api/analitica/mas-usados?" + rango, null).estado()).isEqualTo(401);
    }

    // ================================================================== vencimientos (HU-130, 140)

    @Test
    @DisplayName("HU-130 / HU-140 Vencimientos: vencidos y próximos, con filtros por días, categoría y rango de fechas")
    void vencimientosConFiltros() {
        Cuenta bodeguero = crearBodeguero();
        Catalogo categoria = crearCategoriaPropia(false);
        Catalogo otraCategoria = crearCategoriaPropia(false);
        var v1 = crearProductoEn(categoria.id(), true);
        var v2 = crearProductoEn(categoria.id(), true);
        var v3 = crearProductoEn(categoria.id(), true);
        var w1 = crearProductoEn(otraCategoria.id(), true);
        entrada(v1.id(), 4, hoy().minusDays(3), "V1-VENCIDO").esperando(201);
        entrada(v1.id(), 6, hoy().plusDays(5), "V1-PRONTO").esperando(201);
        entrada(v1.id(), 7, hoy().plusDays(20), "V1-MEDIO").esperando(201);
        entrada(v1.id(), 8, hoy().plusDays(90), "V1-LEJANO").esperando(201);
        entrada(v2.id(), 3, hoy().plusDays(10), "V2-UNICO").esperando(201);
        entrada(w1.id(), 9, hoy().plusDays(5), "W1-PRONTO").esperando(201);
        entrada(v3.id(), 2, hoy().plusDays(3), "V3-AGOTADO").esperando(201);
        // Lo ya consumido no está en riesgo: V2 queda con 2 y el lote de V3 se agota.
        salida(bodeguero, v2.id(), 1).esperando(201);
        salida(bodeguero, v3.id(), 2).esperando(201);

        // Próximos 30 días de la categoría: el vencido (marcado), los próximos, por fecha; sin el lejano ni el agotado.
        var treinta = get("/api/analitica/vencimientos?dias=30&categoriaId=" + categoria.id(), bodeguero.token())
                .esperando(200).json();
        assertThat(treinta).hasSize(4);
        assertThat(fechas(treinta)).containsExactly(hoy().minusDays(3), hoy().plusDays(5), hoy().plusDays(10),
                hoy().plusDays(20));
        assertThat(treinta.get(0).path("productoId").asLong()).isEqualTo(v1.id());
        assertThat(treinta.get(0).path("nombre").asText()).isEqualTo(v1.nombre());
        assertThat(treinta.get(0).path("cantidadEnRiesgo").asInt()).isEqualTo(4);
        assertThat(treinta.get(0).path("vencido").asBoolean()).isTrue();
        assertThat(treinta.get(1).path("vencido").asBoolean()).isFalse();
        assertThat(treinta.get(1).path("cantidadEnRiesgo").asInt()).isEqualTo(6);
        assertThat(treinta.get(2).path("productoId").asLong()).isEqualTo(v2.id());
        assertThat(treinta.get(2).path("cantidadEnRiesgo").asInt()).isEqualTo(2);
        assertThat(treinta.get(3).path("cantidadEnRiesgo").asInt()).isEqualTo(7);

        // Filtro por días: con 7 solo el vencido y el que vence en 5; con 100 entra también el lejano.
        assertThat(fechas(get("/api/analitica/vencimientos?dias=7&categoriaId=" + categoria.id(), bodeguero.token())
                .esperando(200).json())).containsExactly(hoy().minusDays(3), hoy().plusDays(5));
        assertThat(get("/api/analitica/vencimientos?dias=100&categoriaId=" + categoria.id(), bodeguero.token())
                .esperando(200).json()).hasSize(5);

        // Filtro por categoría: la otra categoría solo trae lo suyo.
        var deLaOtra = get("/api/analitica/vencimientos?dias=30&categoriaId=" + otraCategoria.id(), bodeguero.token())
                .esperando(200).json();
        assertThat(deLaOtra).hasSize(1);
        assertThat(deLaOtra.get(0).path("productoId").asLong()).isEqualTo(w1.id());
        // Sin filtro de categoría aparecen ambas (entre lo demás que haya en la base).
        var sinFiltro = get("/api/analitica/vencimientos?dias=30", bodeguero.token()).esperando(200).json();
        assertThat(idsDeProductos(sinFiltro)).contains(v1.id(), v2.id(), w1.id()).doesNotContain(v3.id());

        // Filtro por rango de fechas: los extremos cuentan.
        var rango = get("/api/analitica/vencimientos/rango?desde=" + hoy().plusDays(5) + "&hasta=" + hoy().plusDays(10)
                + "&categoriaId=" + categoria.id(), bodeguero.token()).esperando(200).json();
        assertThat(fechas(rango)).containsExactly(hoy().plusDays(5), hoy().plusDays(10));
        var soloVencidos = get("/api/analitica/vencimientos/rango?desde=" + hoy().minusDays(10) + "&hasta="
                + hoy().minusDays(1) + "&categoriaId=" + categoria.id(), bodeguero.token()).esperando(200).json();
        assertThat(soloVencidos).hasSize(1);
        assertThat(soloVencidos.get(0).path("vencido").asBoolean()).isTrue();
        assertThat(get("/api/analitica/vencimientos/rango?desde=" + hoy().plusDays(200) + "&hasta="
                + hoy().plusDays(210) + "&categoriaId=" + categoria.id(), bodeguero.token()).esperando(200).json())
                .isEmpty();

        // El administrador también ve el reporte.
        assertThat(get("/api/analitica/vencimientos?dias=30&categoriaId=" + categoria.id(), tokenAdmin())
                .esperando(200).json()).hasSize(4);
        assertThat(get("/api/analitica/vencimientos?dias=30", null).estado()).isEqualTo(401);
    }

    @Test
    @Disabled("BUG: el reporte de vencimientos tiene el mismo borde que las alertas: AnaliticaService.getVencimientos "
            + "pasa hoy+N a VistasInventario.lotesConVencimiento, que filtra 'fecha_vencimiento < :hasta' "
            + "(exclusivo); un lote que vence exactamente en N días no sale con dias=N, aunque el filtro por rango "
            + "(hasta inclusive) sí lo incluye")
    @DisplayName("HU-130 Vencimientos: un lote que vence exactamente en N días aparece con dias=N")
    void vencimientosIncluyeElBordeExactoDeNDias() {
        Catalogo categoria = crearCategoriaPropia(false);
        var producto = crearProductoEn(categoria.id(), true);
        entrada(producto.id(), 5, hoy().plusDays(30), "BORDE").esperando(201);

        var reporte = get("/api/analitica/vencimientos?dias=30&categoriaId=" + categoria.id(), bodeguero().token())
                .esperando(200).json();

        assertThat(fechas(reporte)).containsExactly(hoy().plusDays(30));
    }

    // ================================================================== exportar movimientos (HU-120)

    @Test
    @DisplayName("HU-120 Exportar movimientos a Excel: se descarga un archivo válido con los movimientos del rango")
    void exportarMovimientosAExcel() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        Catalogo categoria = crearCategoriaPropia(false);
        var producto = productoEn(categoria, 20);
        var otro = productoEn(crearCategoriaPropia(false), 7);
        salida(bodeguero, producto.id(), 3).esperando(201);
        aprobar(bodeguero, pedirOk(empleado, producto.id(), 4)).esperando(200);

        String url = "/api/analitica/exportar-movimientos?desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(1)
                + "&categoriaId=" + categoria.id();
        var descarga = descargar(url, bodeguero.token()).esperando(200);

        assertThat(descarga.tipoContenido()).contains(XLSX);
        assertThat(descarga.disposicion()).contains("attachment").contains("movimientos.xlsx");
        assertThat(descarga.bytes()).isNotEmpty();
        var hoja = ExcelPruebas.leerHoja(descarga.bytes(), "Movimientos");
        assertThat(hoja.get(0)).containsExactly("ID", "Operación", "Producto", "Tipo", "Sentido", "Cantidad", "Lote",
                "Costo unitario", "Costo total", "Nota", "Fecha", "Usuario");

        // Solo los tres movimientos del producto de la categoría, del más antiguo al más reciente, y no los del otro.
        var libro = new ArrayList<>(movimientos(producto.id()));
        java.util.Collections.reverse(libro); // la API los entrega del más reciente al más antiguo
        assertThat(libro).hasSize(3);
        assertThat(hoja).hasSize(4);
        for (int i = 0; i < 3; i++) {
            JsonNode esperado = libro.get(i);
            List<String> fila = hoja.get(i + 1);
            assertThat(fila.get(0)).as("ID de la fila %d", i + 1).isEqualTo(esperado.path("id").asText());
            assertThat(fila.get(1)).isEqualTo(esperado.path("operacionId").asText());
            assertThat(fila.get(2)).isEqualTo(producto.nombre());
            assertThat(fila.get(3)).isEqualTo(esperado.path("tipoNombre").asText());
            assertThat(fila.get(4)).isEqualTo(esperado.path("sentido").asInt() > 0 ? "Entrada" : "Salida");
            assertThat(fila.get(5)).isEqualTo(esperado.path("cantidad").asText());
            assertThat(fila.get(7)).isEqualTo("1000");
            assertThat(fila.get(8)).isEqualTo(Integer.toString(1000 * esperado.path("cantidad").asInt()));
            assertThat(fila.get(11)).isEqualTo(esperado.path("usuarioEmail").asText());
        }
        assertThat(hoja.get(1).get(3)).isEqualTo("Compra");
        assertThat(hoja.get(1).get(5)).isEqualTo("20");
        assertThat(hoja.get(2).get(5)).isEqualTo("3");
        assertThat(hoja.get(2).get(11)).isEqualTo(bodeguero.email());
        assertThat(hoja.get(3).get(5)).isEqualTo("4");
        assertThat(hoja.get(3).get(9)).contains("Solicitud #");
        assertThat(hoja).noneMatch(f -> f.size() > 2 && otro.nombre().equals(f.get(2)));

        // Un rango sin movimientos entrega el archivo solo con los encabezados.
        var vacio = descargar("/api/analitica/exportar-movimientos?desde=" + hoy().minusDays(30) + "&hasta="
                + hoy().minusDays(10) + "&categoriaId=" + categoria.id(), bodeguero.token()).esperando(200);
        assertThat(ExcelPruebas.leerHoja(vacio.bytes(), "Movimientos")).hasSize(1);

        // El administrador descarga; sin sesión no.
        descargar(url, tokenAdmin()).esperando(200);
        assertThat(descargar(url, null).estado()).isEqualTo(401);
    }

    // ================================================================== historial de entregas (HU-290, HU-180)

    @Test
    @DisplayName("HU-290 Historial de entregas: cada solicitud muestra producto, solicitado y entregado, fecha y responsable")
    void historialDeEntregasMuestraSolicitadoVsEntregado() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        var x = productoConStock(10);
        var y = productoConStock(5);
        var pedidoRechazado = pedirOk(empleado, y.id(), 4);
        long pedido = pedir(empleado, item(x.id(), 4), item(y.id(), 4)).esperando(201).json().path("id").asLong();
        // Antes de aprobar quedan solo 3 de Y (otro retiro) y se entregan 3 de los 4 pedidos.
        salida(y.id(), 2).esperando(201);
        aprobar(bodeguero, pedido, item(x.id(), 4), item(y.id(), 3)).esperando(200);
        rechazar(bodeguero, pedidoRechazado).esperando(204);

        // La administradora consulta el historial del periodo: ve las líneas de la solicitud.
        var revisiones = revisionesDe(pedido, tokenAdmin());
        assertThat(revisiones).isNotEmpty();
        for (JsonNode revision : revisiones) {
            JsonNode lineaX = lineaDe(revision, x.id());
            assertThat(lineaX.path("productoNombre").asText()).isEqualTo(x.nombre());
            assertThat(lineaX.path("cantidadSolicitada").asInt()).isEqualTo(4);
            assertThat(lineaX.path("cantidadEntregada").asInt()).isEqualTo(4);
            JsonNode lineaY = lineaDe(revision, y.id());
            assertThat(lineaY.path("productoNombre").asText()).isEqualTo(y.nombre());
            assertThat(lineaY.path("cantidadSolicitada").asInt()).isEqualTo(4);
            assertThat(lineaY.path("cantidadEntregada").asInt()).isEqualTo(3);
        }
        // La última revisión es la aprobación, hecha por el bodeguero, con fecha de hoy.
        JsonNode aprobacion = revisiones.get(0);
        assertThat(aprobacion.path("estadoSolicitud").asText()).isEqualTo("APROBADA");
        assertThat(aprobacion.path("usuarioEmail").asText()).isEqualTo(bodeguero.email());
        assertThat(aprobacion.path("fecha").asText()).startsWith(hoy().toString());

        // La solicitud que se rechazó no entregó nada.
        JsonNode rechazo = revisionesDe(pedidoRechazado, tokenAdmin()).get(0);
        assertThat(rechazo.path("estadoSolicitud").asText()).isEqualTo("RECHAZADA");
        assertThat(lineaDe(rechazo, y.id()).path("cantidadSolicitada").asInt()).isEqualTo(4);
        assertThat(lineaDe(rechazo, y.id()).path("cantidadEntregada").isNull()).isTrue();

        // El mismo historial por el listado de solicitudes del bodeguero.
        var solicitudes = get("/api/solicitudes?desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(1),
                bodeguero.token()).esperando(200).json();
        JsonNode delListado = null;
        for (JsonNode s : solicitudes) {
            if (s.path("id").asLong() == pedido) delListado = s;
        }
        assertThat(delListado).isNotNull();
        assertThat(delListado.path("solicitanteNombre").asText()).isEqualTo(empleado.nombre());
        assertThat(detalleDe(delListado, y.id()).path("cantidadSolicitada").asInt()).isEqualTo(4);
        assertThat(detalleDe(delListado, y.id()).path("cantidadEntregada").asInt()).isEqualTo(3);

        // Un periodo sin movimientos no trae esas solicitudes; sin sesión no se consulta.
        var antiguo = get("/api/auditoria/historial?desde=" + hoy().minusDays(60) + "&hasta=" + hoy().minusDays(30),
                tokenAdmin()).esperando(200).json();
        for (JsonNode r : antiguo) assertThat(r.path("solicitudId").asLong()).isNotEqualTo(pedido);
        assertThat(get("/api/auditoria/historial?desde=" + hoy() + "&hasta=" + hoy(), null).estado()).isEqualTo(401);
    }

    @Test
    @DisplayName("HU-180 El historial de entregas se descarga como archivo")
    void historialDeEntregasSeDescarga() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        var producto = productoConStock(6);
        aprobar(bodeguero, pedirOk(empleado, producto.id(), 2)).esperando(200);

        var descarga = descargar("/api/auditoria/historial/pdf?desde=" + hoy().minusDays(1) + "&hasta="
                + hoy().plusDays(1), bodeguero.token()).esperando(200);

        assertThat(descarga.bytes()).isNotEmpty();
        assertThat(descarga.disposicion()).contains("attachment");
        // El administrador también lo descarga; sin sesión no.
        descargar("/api/auditoria/historial/pdf?desde=" + hoy() + "&hasta=" + hoy(), tokenAdmin()).esperando(200);
        assertThat(descargar("/api/auditoria/historial/pdf?desde=" + hoy() + "&hasta=" + hoy(), null).estado())
                .isEqualTo(401);
    }

    @Test
    @Disabled("BUG: GET /api/auditoria/historial/pdf no entrega un PDF: AuditoriaController.exportarPdf arma un "
            + "Excel (XSSFWorkbook), lo sirve como application/vnd.openxmlformats...sheet con nombre "
            + "historial-auditoria.xlsx, y sus columnas son solicitud, fecha, usuario, tipo de revisión, estado y "
            + "tipo: no incluye producto, cantidad solicitada ni cantidad entregada, que pide HU-180")
    @DisplayName("HU-180 El historial de entregas se descarga como PDF con fecha, producto, cantidad solicitada, entregada y responsable")
    void historialDeEntregasSeDescargaComoPdfConSusColumnas() {
        Cuenta bodeguero = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        var producto = productoConStock(6);
        aprobar(bodeguero, pedirOk(empleado, producto.id(), 5), item(producto.id(), 4)).esperando(200);

        var descarga = descargar("/api/auditoria/historial/pdf?desde=" + hoy().minusDays(1) + "&hasta="
                + hoy().plusDays(1), bodeguero.token()).esperando(200);

        assertThat(descarga.tipoContenido()).contains("application/pdf");
        assertThat(new String(descarga.bytes(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        String texto = textoDelPdf(descarga.bytes());
        assertThat(texto).contains(producto.nombre()).contains(bodeguero.email());
        assertThat(texto).contains(hoy().toString());
    }

    // ================================================================== auditoría (HU-260)

    @Test
    @DisplayName("HU-260 La auditoría registra quién cambió qué y cuándo: creación por el empleado, aprobación y rechazo por cada bodeguero")
    void laAuditoriaRegistraQuienCambioQue() {
        Cuenta primero = crearBodeguero();
        Cuenta segundo = crearBodeguero();
        Cuenta empleado = crearEmpleado();
        var producto = productoConStock(20);
        long aprobado = pedirOk(empleado, producto.id(), 3);
        long rechazado = pedirOk(empleado, producto.id(), 2);
        aprobar(primero, aprobado).esperando(200);
        rechazar(segundo, rechazado).esperando(204);

        // Solicitud aprobada: primero la modificación del bodeguero, antes de ella la creación por el empleado.
        var delAprobado = revisionesDe(aprobado, tokenAdmin());
        assertThat(delAprobado).hasSize(2);
        assertThat(delAprobado.get(0).path("tipoRevision").asText()).isEqualTo("MOD");
        assertThat(delAprobado.get(0).path("usuarioEmail").asText()).isEqualTo(primero.email());
        assertThat(delAprobado.get(0).path("estadoSolicitud").asText()).isEqualTo("APROBADA");
        assertThat(delAprobado.get(1).path("tipoRevision").asText()).isEqualTo("ADD");
        assertThat(delAprobado.get(1).path("usuarioEmail").asText()).isEqualTo(empleado.email());
        assertThat(delAprobado.get(1).path("estadoSolicitud").asText()).isEqualTo("PENDIENTE");
        assertThat(delAprobado.get(1).path("tipoSolicitud").asText()).isEqualTo("PEDIDO");

        // Solicitud rechazada: el rechazo queda a nombre del segundo bodeguero.
        var delRechazado = revisionesDe(rechazado, tokenAdmin());
        assertThat(delRechazado).hasSize(2);
        assertThat(delRechazado.get(0).path("usuarioEmail").asText()).isEqualTo(segundo.email());
        assertThat(delRechazado.get(0).path("estadoSolicitud").asText()).isEqualTo("RECHAZADA");
        assertThat(delRechazado.get(1).path("usuarioEmail").asText()).isEqualTo(empleado.email());

        // Las revisiones llevan fecha y hora de hoy, y el bodeguero también puede consultarlas.
        for (JsonNode r : delAprobado) assertThat(r.path("fecha").asText()).startsWith(hoy().toString());
        assertThat(revisionesDe(aprobado, primero.token())).hasSize(2);

        // En el stock, el libro dice quién hizo cada movimiento, cuándo y con qué nota.
        var entrega = movimientosDeTipo(producto.id(), "ENTREGA_PEDIDO");
        assertThat(entrega).hasSize(1);
        assertThat(entrega.get(0).path("usuarioEmail").asText()).isEqualTo(primero.email());
        assertThat(entrega.get(0).path("fecha").asText()).startsWith(hoy().toString());
        assertThat(entrega.get(0).path("nota").asText()).contains("Solicitud #" + aprobado);
        var compra = movimientosDeTipo(producto.id(), "COMPRA");
        assertThat(compra.get(0).path("usuarioEmail").asText()).isEqualTo(bodeguero().email());

        // La auditoría solo se lee: no hay forma de crear o corregir revisiones por la API.
        assertThat(revisionesDe(aprobado, tokenAdmin())).hasSize(2);
        assertThat(get("/api/auditoria/historial?desde=" + hoy() + "&hasta=" + hoy(), null).estado()).isEqualTo(401);
    }

    // ------------------------------------------------------------------ ayudantes

    /** Producto no perecible de la categoría, con una entrada inicial. */
    private Prod productoEn(Catalogo categoria, int stockInicial) {
        Prod producto = crearProductoEn(categoria.id(), false);
        entrada(producto.id(), stockInicial, null, "L-" + unico()).esperando(201);
        return producto;
    }

    private static JsonNode filaConsumo(JsonNode filas, long id) {
        for (JsonNode f : filas) {
            if (f.path("id").asLong() == id) return f;
        }
        throw new AssertionError("El consumo no trae el producto " + id);
    }

    private static List<LocalDate> fechas(JsonNode vencimientos) {
        List<LocalDate> fechas = new ArrayList<>();
        for (JsonNode v : vencimientos) fechas.add(LocalDate.parse(v.path("fechaVencimiento").asText()));
        return fechas;
    }

    private static List<Long> idsDeProductos(JsonNode vencimientos) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode v : vencimientos) ids.add(v.path("productoId").asLong());
        return ids;
    }

    /** Revisiones de una solicitud, la más reciente primero. */
    private List<JsonNode> revisionesDe(long solicitudId, String token) {
        var todas = get("/api/auditoria/historial?desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(1), token)
                .esperando(200).json();
        List<JsonNode> propias = new ArrayList<>();
        for (JsonNode r : todas) {
            if (r.path("solicitudId").asLong() == solicitudId) propias.add(r);
        }
        return propias;
    }

    private static JsonNode lineaDe(JsonNode revision, long productoId) {
        for (JsonNode l : revision.path("lineas")) {
            if (l.path("productoId").asLong() == productoId) return l;
        }
        throw new AssertionError("La revisión no tiene línea del producto " + productoId + ": " + revision);
    }

    private static String textoDelPdf(byte[] pdf) {
        try {
            PdfReader lector = new PdfReader(pdf);
            PdfTextExtractor extractor = new PdfTextExtractor(lector);
            StringBuilder texto = new StringBuilder();
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                texto.append(extractor.getTextFromPage(pagina)).append('\n');
            }
            lector.close();
            // El PDF parte las celdas largas en varias líneas: se normalizan los espacios y saltos.
            return texto.toString().replaceAll("\\s+", " ");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo leer el PDF descargado", e);
        }
    }
}
