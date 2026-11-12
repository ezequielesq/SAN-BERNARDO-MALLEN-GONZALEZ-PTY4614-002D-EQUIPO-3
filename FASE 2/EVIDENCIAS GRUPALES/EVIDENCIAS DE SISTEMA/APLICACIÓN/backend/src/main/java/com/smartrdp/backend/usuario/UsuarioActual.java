package com.smartrdp.backend.usuario;

import com.smartrdp.backend.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** El usuario autenticado de la petición actual (antes duplicado en MovimientoService y SolicitudService). */
@Component
@RequiredArgsConstructor
public class UsuarioActual {

    private final UsuarioRepository usuarioRepository;

    public Optional<Usuario> opcional() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        return usuarioRepository.findByEmailAndActivoTrue(auth.getName());
    }

    /** Para operaciones que exigen autor: el libro y la resolución de solicitudes no admiten autor nulo. */
    public Usuario obligatorio() {
        return opcional().orElseThrow(() -> new BusinessException("Debes iniciar sesión para realizar esta acción."));
    }
}
