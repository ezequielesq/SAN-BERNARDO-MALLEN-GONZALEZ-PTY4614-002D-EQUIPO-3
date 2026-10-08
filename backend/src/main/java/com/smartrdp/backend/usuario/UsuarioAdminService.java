package com.smartrdp.backend.usuario;

import com.smartrdp.backend.exception.BusinessException;
import com.smartrdp.backend.exception.ResourceNotFoundException;
import com.smartrdp.backend.usuario.dto.UsuarioEdicionRequest;
import com.smartrdp.backend.usuario.dto.UsuarioRequest;
import com.smartrdp.backend.usuario.dto.UsuarioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Administración de bodegueros y empleados por el ADMIN (los administradores no se gestionan aquí). */
@Service
@RequiredArgsConstructor
public class UsuarioAdminService {

    private static final List<Rol> ROLES_ADMINISTRABLES = List.of(Rol.BODEGUERO, Rol.EMPLEADO);
    private static final int LARGO_MINIMO_PASSWORD = 6;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar(boolean soloActivos) {
        List<Usuario> usuarios = soloActivos
                ? usuarioRepository.findByRolInAndActivoTrueOrderByNombreAsc(ROLES_ADMINISTRABLES)
                : usuarioRepository.findByRolInOrderByNombreAsc(ROLES_ADMINISTRABLES);
        return usuarios.stream().map(UsuarioResponse::from).toList();
    }

    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        Rol rol = validarRol(request.rol());
        String nombre = request.nombre().trim();
        String email = request.email().trim();
        validarCorreo(email, null);
        if (rol == Rol.EMPLEADO) validarNombreEmpleado(nombre, null);

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setEmail(email);
        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setRol(rol);
        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse editar(Long id, UsuarioEdicionRequest request) {
        Usuario usuario = buscar(id);
        Rol rol = validarRol(request.rol());
        String nombre = request.nombre().trim();
        String email = request.email().trim();
        validarCorreo(email, id);
        if (rol == Rol.EMPLEADO && usuario.isActivo()) validarNombreEmpleado(nombre, id);

        String password = request.password();
        if (password != null && !password.isBlank()) {
            if (password.length() < LARGO_MINIMO_PASSWORD) {
                throw new BusinessException("La contraseña debe tener al menos 6 caracteres.", "password");
            }
            usuario.setPassword(passwordEncoder.encode(password));
        }
        usuario.setNombre(nombre);
        usuario.setEmail(email);
        usuario.setRol(rol);
        return UsuarioResponse.from(usuario);
    }

    @Transactional
    public void desactivar(Long id) {
        buscar(id).setActivo(false);
    }

    @Transactional
    public void reactivar(Long id) {
        Usuario usuario = buscar(id);
        if (usuario.getRol() == Rol.EMPLEADO) validarNombreEmpleado(usuario.getNombre(), id);
        usuario.setActivo(true);
    }

    private Usuario buscar(Long id) {
        return usuarioRepository.findByIdAndRolIn(id, ROLES_ADMINISTRABLES)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
    }

    private Rol validarRol(Rol rol) {
        if (rol == null || !ROLES_ADMINISTRABLES.contains(rol)) {
            throw new BusinessException("El rol debe ser Bodeguero o Empleado.", "rol");
        }
        return rol;
    }

    private void validarCorreo(String email, Long idActual) {
        boolean repetido = idActual == null
                ? usuarioRepository.existsByEmailIgnoreCase(email)
                : usuarioRepository.existsByEmailIgnoreCaseAndIdNot(email, idActual);
        if (repetido) {
            throw new BusinessException("Ya existe un usuario con el correo " + email + ".", "email");
        }
    }

    private void validarNombreEmpleado(String nombre, Long idActual) {
        boolean repetido = idActual == null
                ? usuarioRepository.existsByNombreIgnoreCaseAndRolAndActivoTrue(nombre, Rol.EMPLEADO)
                : usuarioRepository.existsByNombreIgnoreCaseAndRolAndActivoTrueAndIdNot(
                        nombre, Rol.EMPLEADO, idActual);
        if (repetido) {
            throw new BusinessException("Ya hay un empleado activo con el nombre " + nombre + ".", "nombre");
        }
    }
}
