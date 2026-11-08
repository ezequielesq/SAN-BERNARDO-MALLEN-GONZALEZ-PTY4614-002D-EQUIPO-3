package com.smartrdp.backend.evento;

import com.smartrdp.backend.evento.dto.EventoRequest;
import com.smartrdp.backend.evento.dto.EventoResponse;
import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.usuario.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventoService {

    /** Una agenda anual completa (bisiesto incluido) cabe en una sola consulta. */
    static final int MAXIMO_DIAS = 366;

    private final EventoRepository eventoRepository;
    private final UsuarioActual usuarioActual;

    @Transactional(readOnly = true)
    public List<EventoResponse> listar(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new BusinessException("La fecha 'desde' no puede ser posterior a 'hasta'.", "desde");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS) {
            throw new BusinessException("El rango no puede superar los " + MAXIMO_DIAS + " días.", "hasta");
        }
        return eventoRepository.listarEntre(desde, hasta).stream().map(EventoResponse::from).toList();
    }

    @Transactional
    public EventoResponse crear(EventoRequest request) {
        var evento = new Evento();
        aplicar(evento, request);
        evento.setCreadoPor(usuarioActual.obligatorio());
        return EventoResponse.from(eventoRepository.save(evento));
    }

    @Transactional
    public EventoResponse actualizar(Long id, EventoRequest request) {
        Evento evento = buscar(id);
        aplicar(evento, request);
        return EventoResponse.from(evento);
    }

    @Transactional
    public void eliminar(Long id) {
        eventoRepository.delete(buscar(id));
    }

    private void aplicar(Evento evento, EventoRequest request) {
        evento.setTitulo(request.titulo().strip());
        evento.setFecha(request.fecha());
        evento.setHora(request.hora());
        String descripcion = request.descripcion() == null ? "" : request.descripcion().strip();
        evento.setDescripcion(descripcion.isEmpty() ? null : descripcion);
    }

    private Evento buscar(Long id) {
        return eventoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento", id));
    }
}
