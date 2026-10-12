package com.smartrdp.backend.usuario;

import com.smartrdp.backend.usuario.dto.UsuarioEdicionRequest;
import com.smartrdp.backend.usuario.dto.UsuarioRequest;
import com.smartrdp.backend.usuario.dto.UsuarioResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioAdminController {

    private final UsuarioAdminService usuarioAdminService;

    @GetMapping
    public List<UsuarioResponse> listar(@RequestParam(defaultValue = "false") boolean soloActivos) {
        return usuarioAdminService.listar(soloActivos);
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioAdminService.crear(request));
    }

    @PutMapping("/{id}")
    public UsuarioResponse editar(@PathVariable Long id, @Valid @RequestBody UsuarioEdicionRequest request) {
        return usuarioAdminService.editar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        usuarioAdminService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reactivar")
    public ResponseEntity<Void> reactivar(@PathVariable Long id) {
        usuarioAdminService.reactivar(id);
        return ResponseEntity.noContent().build();
    }
}
