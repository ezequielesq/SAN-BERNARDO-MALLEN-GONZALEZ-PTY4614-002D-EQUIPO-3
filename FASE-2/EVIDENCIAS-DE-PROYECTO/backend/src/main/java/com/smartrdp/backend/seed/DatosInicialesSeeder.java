package com.smartrdp.backend.seed;

import com.smartrdp.backend.catalogo.Categoria;
import com.smartrdp.backend.catalogo.CategoriaRepository;
import com.smartrdp.backend.catalogo.UnidadMedida;
import com.smartrdp.backend.catalogo.UnidadMedidaRepository;
import com.smartrdp.backend.usuario.Rol;
import com.smartrdp.backend.usuario.Usuario;
import com.smartrdp.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatosInicialesSeeder implements CommandLineRunner {

    static final List<String> CATEGORIAS = List.of(
            "Vinos", "Aguas y bebidas", "Licores", "Abarrotes", "Lácteos", "Frutas y verduras", "Aseo");

    static final java.util.Set<String> CATEGORIAS_CON_PTV = java.util.Set.of("Vinos", "Licores");

    static final List<String> UNIDADES = List.of(
            "Unidad", "Botella", "Caja", "Paquete", "Kg", "g", "Litro", "ml");

    private final CategoriaRepository categoriaRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-email:}")
    private String adminEmail;

    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        for (String nombre : CATEGORIAS) {
            if (!categoriaRepository.existsByNombreIgnoreCase(nombre)) {
                Categoria categoria = new Categoria(nombre);
                categoria.setRequierePtv(CATEGORIAS_CON_PTV.contains(nombre));
                categoriaRepository.save(categoria);
            }
        }
        for (String nombre : UNIDADES) {
            if (!unidadMedidaRepository.existsByNombreIgnoreCase(nombre)) {
                unidadMedidaRepository.save(new UnidadMedida(nombre));
            }
        }
        sembrarAdmin();
    }

    private void sembrarAdmin() {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("SMARTRDP_ADMIN_EMAIL / SMARTRDP_ADMIN_PASSWORD no definidas: no se crea el ADMIN inicial.");
            return;
        }
        if (usuarioRepository.existsByEmail(adminEmail)) {
            return;
        }
        Usuario admin = new Usuario();
        admin.setNombre("Administrador");
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRol(Rol.ADMIN);
        usuarioRepository.save(admin);
        log.info("ADMIN inicial creado: {}", adminEmail);
    }
}
