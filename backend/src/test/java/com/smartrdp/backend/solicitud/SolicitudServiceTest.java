package com.smartrdp.backend.solicitud;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.movimiento.MovimientoService;
import com.smartrdp.backend.producto.Producto;
import com.smartrdp.backend.producto.ProductoRepository;
import com.smartrdp.backend.solicitud.dto.AprobarSolicitudRequest;
import com.smartrdp.backend.solicitud.dto.SolicitudRequest;
import com.smartrdp.backend.usuario.Usuario;
import com.smartrdp.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SolicitudServiceTest {

    @Mock SolicitudRepository solicitudRepository;
    @Mock ProductoRepository productoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock MovimientoService movimientoService;
    @InjectMocks SolicitudService solicitudService;

    @Test
    void crear_whenProductoNotFound_thenThrowsResourceNotFoundException() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());
        var req = new SolicitudRequest(List.of(new SolicitudRequest.Item(99L, 2)));
        assertThatThrownBy(() -> solicitudService.crear(req, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aprobar_whenSolicitudYaAprobada_thenThrowsBusinessException() {
        var solicitud = new Solicitud();
        solicitud.setEstado(EstadoSolicitud.APROBADA);
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        var req = new AprobarSolicitudRequest(List.of());
        assertThatThrownBy(() -> solicitudService.aprobar(1L, req, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya fue procesada");
    }

    @Test
    void rechazar_whenPendiente_thenCambiaEstado() {
        var solicitud = new Solicitud();
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        solicitudService.rechazar(1L, 1L);
        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.RECHAZADA);
    }
}
