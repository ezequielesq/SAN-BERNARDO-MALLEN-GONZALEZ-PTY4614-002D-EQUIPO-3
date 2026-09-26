package com.smartrdp.backend.solicitud;

import com.smartrdp.backend.solicitud.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/solicitudes")
@RequiredArgsConstructor
public class SolicitudController {

    private final SolicitudService solicitudService;

    @PostMapping
    public ResponseEntity<SolicitudResponse> crear(@Valid @RequestBody SolicitudRequest request) {
        // TODO: obtener id del usuario autenticado via SecurityContext
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitudService.crear(request, null));
    }

    @GetMapping
    public List<SolicitudResponse> listar() {
        return solicitudService.listarTodas();
    }

    @GetMapping("/pendientes")
    public List<SolicitudResponse> pendientes() {
        return solicitudService.listarPendientes();
    }

    @PutMapping("/{id}/aprobar")
    public SolicitudResponse aprobar(@PathVariable Long id,
                                      @RequestBody AprobarSolicitudRequest request) {
        return solicitudService.aprobar(id, request, null);
    }

    @PutMapping("/{id}/rechazar")
    public ResponseEntity<Void> rechazar(@PathVariable Long id) {
        solicitudService.rechazar(id, null);
        return ResponseEntity.noContent().build();
    }
}
