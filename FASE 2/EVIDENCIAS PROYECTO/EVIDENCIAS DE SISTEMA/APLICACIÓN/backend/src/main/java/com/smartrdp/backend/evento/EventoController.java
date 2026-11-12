package com.smartrdp.backend.evento;

import com.smartrdp.backend.evento.dto.EventoRequest;
import com.smartrdp.backend.evento.dto.EventoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
public class EventoController {

    private final EventoService eventoService;

    @GetMapping
    public List<EventoResponse> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return eventoService.listar(desde, hasta);
    }

    @PostMapping
    public ResponseEntity<EventoResponse> crear(@Valid @RequestBody EventoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventoService.crear(request));
    }

    @PutMapping("/{id}")
    public EventoResponse actualizar(@PathVariable Long id, @Valid @RequestBody EventoRequest request) {
        return eventoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        eventoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
