package com.devsenior.sillon.auth.service;

import com.devsenior.sillon.auth.dto.AuthResponse;
import com.devsenior.sillon.auth.dto.LoginRequest;
import com.devsenior.sillon.auth.dto.RegistroRequest;
import com.devsenior.sillon.auth.dto.SesionIniciada;
import com.devsenior.sillon.compartido.config.NegocioProperties;
import com.devsenior.sillon.compartido.excepcion.CredencialesInvalidasException;
import com.devsenior.sillon.compartido.excepcion.ReglaNegocioException;
import com.devsenior.sillon.compartido.limite.Limitador;
import com.devsenior.sillon.compartido.limite.ReglaLimite;
import com.devsenior.sillon.salon.model.ConfiguracionSalon;
import com.devsenior.sillon.salon.model.Plan;
import com.devsenior.sillon.salon.model.Salon;
import com.devsenior.sillon.salon.repository.SalonRepository;
import com.devsenior.sillon.salon.service.GeneradorSlug;
import com.devsenior.sillon.seguridad.model.UsuarioPrincipal;
import com.devsenior.sillon.seguridad.service.JwtService;
import com.devsenior.sillon.usuario.model.Rol;
import com.devsenior.sillon.usuario.model.Usuario;
import com.devsenior.sillon.usuario.repository.UsuarioRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String MENSAJE_CREDENCIALES = "Correo o contraseña incorrectos";

    private final SalonRepository salonRepository;
    private final UsuarioRepository usuarioRepository;
    private final GeneradorSlug generadorSlug;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final NegocioProperties negocio;
    private final SesionService sesionService;
    private final Limitador limitador;

    public AuthService(SalonRepository salonRepository, UsuarioRepository usuarioRepository,
                       GeneradorSlug generadorSlug, PasswordEncoder passwordEncoder,
                       JwtService jwtService, NegocioProperties negocio,
                       SesionService sesionService, Limitador limitador) {
        this.salonRepository = salonRepository;
        this.usuarioRepository = usuarioRepository;
        this.generadorSlug = generadorSlug;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.negocio = negocio;
        this.sesionService = sesionService;
        this.limitador = limitador;
    }

    /** Crea el salón (GRATIS, créditos de cortesía, horario por defecto) y su dueño en una sola transacción. */
    @Transactional
    public SesionIniciada registrar(RegistroRequest request) {
        String email = normalizarCorreo(request.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new ReglaNegocioException("Ya existe una cuenta con ese correo");
        }

        Salon salon = new Salon();
        salon.setNombre(request.salonNombre().trim());
        salon.setSlug(generadorSlug.generar(request.salonNombre()));
        salon.setCiudad(request.ciudad().trim());
        salon.setDireccion(request.direccion().trim());
        salon.setTelefono(request.telefono().trim());
        salon.setPlan(Plan.GRATIS);
        salon.setCreditosIa(negocio.creditosRegistro());
        salon.setConfiguracion(ConfiguracionSalon.porDefecto());
        salonRepository.save(salon);

        Usuario dueno = new Usuario();
        dueno.setNombre(request.nombre().trim());
        dueno.setEmail(email);
        dueno.setPassword(passwordEncoder.encode(request.password()));
        dueno.setRol(Rol.DUENO);
        dueno.setActivo(true);
        dueno.setSalon(salon);
        usuarioRepository.save(dueno);

        return iniciar(dueno, salon);
    }

    /**
     * 401 con un mensaje genérico: no revela si falló el correo o la contraseña.
     * Ya no es readOnly: el login guarda la nueva sesión de refresco.
     */
    @Transactional
    public SesionIniciada login(LoginRequest request) {
        String email = normalizarCorreo(request.email());
        // Antes de BCrypt: 10 intentos cada 15 min por cuenta, vengan de la IP que vengan
        limitador.consumir(ReglaLimite.LOGIN_CORREO, email);
        Usuario usuario = usuarioRepository.findByEmail(email)
                .filter(Usuario::isActivo)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new CredencialesInvalidasException(MENSAJE_CREDENCIALES));
        return iniciar(usuario, usuario.getSalon());
    }

    /** Rota el refresh token de la cookie y entrega un access token nuevo. */
    public SesionIniciada refrescar(String refresco) {
        SesionService.Rotacion rotacion = sesionService.rotar(refresco);
        Usuario usuario = rotacion.usuario();
        return new SesionIniciada(respuesta(usuario, usuario.getSalon()), rotacion.nuevoValor());
    }

    /** Cierra la sesión de este dispositivo. */
    public void cerrarSesion(String refresco) {
        sesionService.revocar(refresco);
    }

    public static String normalizarCorreo(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private SesionIniciada iniciar(Usuario usuario, Salon salon) {
        return new SesionIniciada(respuesta(usuario, salon), sesionService.emitir(usuario));
    }

    private AuthResponse respuesta(Usuario usuario, Salon salon) {
        String token = jwtService.generar(UsuarioPrincipal.desde(usuario));
        return new AuthResponse(token, "Bearer", jwtService.getExpiracionMinutos(), usuario.getId(),
                usuario.getNombre(), usuario.getRol(), salon.getId(), salon.getSlug());
    }
}
