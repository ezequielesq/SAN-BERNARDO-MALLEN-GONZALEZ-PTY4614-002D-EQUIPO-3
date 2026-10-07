package com.smartrdp.backend.publico;

import com.smartrdp.backend.auditoria.ActorAuditoria;
import com.smartrdp.backend.publico.dto.*;
import com.smartrdp.backend.solicitud.SolicitudService;
import com.smartrdp.backend.solicitud.dto.SolicitudResponse;
import com.smartrdp.backend.usuario.Usuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

/** Vista pública del empleado: sin sesión, cada acción valida usuario + contraseña. */
@RestController
@RequestMapping("/api/publico")
@RequiredArgsConstructor
public class PublicoController {

    private final PublicoService publicoService;
    private final VerificadorIdentidad verificador;
    private final SolicitudService solicitudService;

    @GetMapping("/empleados")
    public List<EmpleadoDto> empleados() {
        return publicoService.listarEmpleados();
    }

    @GetMapping("/productos")
    public List<ProductoPublicoDto> productos() {
        return publicoService.listarProductos();
    }

    @PostMapping("/solicitudes")
    public ResponseEntity<SolicitudResponse> crearSolicitud(@Valid @RequestBody SolicitudPublicaRequest request) {
        Usuario empleado = verificador.verificar(request.empleadoId(), request.password());
        SolicitudResponse respuesta =
                conActor(empleado, () -> solicitudService.crearPedido(empleado.getId(), request.items()));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @PostMapping("/mis-solicitudes")
    public List<SolicitudResponse> misSolicitudes(@Valid @RequestBody CredencialesRequest request) {
        Usuario empleado = verificador.verificar(request.empleadoId(), request.password());
        return solicitudService.listarDeUsuario(empleado.getId());
    }

    @PostMapping("/devoluciones")
    public ResponseEntity<SolicitudResponse> devolver(@Valid @RequestBody DevolucionPublicaRequest request) {
        Usuario empleado = verificador.verificar(request.empleadoId(), request.password());
        SolicitudResponse respuesta = conActor(empleado, () -> solicitudService.crearDevolucion(
                empleado.getId(), request.solicitudOrigenId(), request.items()));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /** El actor se fija fuera de la transacción del servicio: Envers lee el autor al confirmarla. */
    private <T> T conActor(Usuario empleado, Supplier<T> accion) {
        ActorAuditoria.establecer(empleado.getEmail());
        try {
            return accion.get();
        } finally {
            ActorAuditoria.limpiar();
        }
    }
}
