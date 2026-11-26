package com.devsenior.sillon.usuario.service;

import com.devsenior.sillon.auth.service.AuthService;
import com.devsenior.sillon.auth.service.SesionService;
import com.devsenior.sillon.cita.model.EstadoCita;
import com.devsenior.sillon.cita.repository.CitaRepository;
import com.devsenior.sillon.compartido.config.NegocioProperties;
import com.devsenior.sillon.compartido.excepcion.PagoRequeridoException;
import com.devsenior.sillon.compartido.excepcion.RecursoNoEncontradoException;
import com.devsenior.sillon.compartido.excepcion.ReglaNegocioException;
import com.devsenior.sillon.salon.model.Plan;
import com.devsenior.sillon.salon.model.Salon;
import com.devsenior.sillon.salon.service.SalonService;
import com.devsenior.sillon.servicio.model.Servicio;
import com.devsenior.sillon.servicio.repository.ServicioRepository;
import com.devsenior.sillon.usuario.dto.EstilistaActualizacionRequest;
import com.devsenior.sillon.usuario.dto.EstilistaRequest;
import com.devsenior.sillon.usuario.dto.EstilistaResponse;
import com.devsenior.sillon.usuario.mapper.EstilistaMapper;
import com.devsenior.sillon.usuario.model.Rol;
import com.devsenior.sillon.usuario.model.Usuario;
import com.devsenior.sillon.usuario.repository.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstilistaService {

    private final UsuarioRepository usuarioRepository;
    private final ServicioRepository servicioRepository;
    private final SalonService salonService;
    private final EstilistaMapper estilistaMapper;
    private final PasswordEncoder passwordEncoder;
    private final NegocioProperties negocio;
    private final CitaRepository citaRepository;
    private final Clock clock;
    private final SesionService sesionService;

    public EstilistaService(UsuarioRepository usuarioRepository, ServicioRepository servicioRepository,
                            SalonService salonService, EstilistaMapper estilistaMapper,
                            PasswordEncoder passwordEncoder, NegocioProperties negocio,
                            CitaRepository citaRepository, Clock clock,
                            SesionService sesionService) {
        this.usuarioRepository = usuarioRepository;
        this.servicioRepository = servicioRepository;
        this.salonService = salonService;
        this.estilistaMapper = estilistaMapper;
        this.passwordEncoder = passwordEncoder;
        this.negocio = negocio;
        this.citaRepository = citaRepository;
        this.clock = clock;
        this.sesionService = sesionService;
    }

    /**
     * Orden de validación (SPEC R10):
     * 1. Límite del plan (402), contando solo estilistas activos: no depende de los datos enviados; si el salón no puede crear más, lo demás sobra.
     * 2. Correo único en toda la plataforma (409).
     * 3. Todos los servicios son del salón (404).
     */
    @Transactional
    public EstilistaResponse crear(Long salonId, EstilistaRequest request) {
        Salon salon = salonService.obtenerEntidad(salonId);

        validarCupoDelPlan(salon);

        String email = AuthService.normalizarCorreo(request.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new ReglaNegocioException("Ya existe una cuenta con ese correo");
        }

        List<Servicio> servicios = serviciosDelSalon(salonId, request.servicioIds());

        Usuario estilista = new Usuario();
        estilista.setNombre(request.nombre().trim());
        estilista.setEmail(email);
        estilista.setPassword(passwordEncoder.encode(request.password()));
        estilista.setRol(Rol.ESTILISTA);
        estilista.setActivo(true);
        estilista.setSalon(salon);
        estilista.setServicios(new HashSet<>(servicios));
        return estilistaMapper.toResponse(usuarioRepository.save(estilista));
    }

    /**
     * Edita nombre, servicios, estado y, si viene, la contraseña (SPEC R43). Orden de validación:
     * 1. El estilista existe en el salón (404).
     * 2. Reactivarlo respeta el cupo del plan (402).
     * 3. Desactivarlo exige que no tenga citas pendientes o confirmadas sin terminar (409).
     * 4. Todos los servicios son del salón (404).
     */
    @Transactional
    public EstilistaResponse actualizar(Long salonId, Long id, EstilistaActualizacionRequest request) {
        Salon salon = salonService.obtenerEntidad(salonId);
        Usuario estilista = usuarioRepository.findByIdAndSalonIdAndRol(id, salonId, Rol.ESTILISTA)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el estilista con id " + id));

        boolean activar = request.activo() && !estilista.isActivo();
        boolean desactivar = !request.activo() && estilista.isActivo();
        if (activar) {
            validarCupoDelPlan(salon);
        }
        if (desactivar) {
            long pendientes = citaRepository.countByEstilistaIdAndEstadoInAndFinGreaterThan(
                    estilista.getId(), EstadoCita.OCUPAN_AGENDA, LocalDateTime.now(clock));
            if (pendientes > 0) {
                throw new ReglaNegocioException(estilista.getNombre() + " tiene " + pendientes
                        + (pendientes == 1 ? " cita pendiente o confirmada" : " citas pendientes o confirmadas")
                        + ". Cancélalas antes de desactivarlo");
            }
        }

        estilista.setServicios(new HashSet<>(serviciosDelSalon(salonId, request.servicioIds())));
        estilista.setNombre(request.nombre().trim());
        estilista.setActivo(request.activo());
        boolean cambiaContrasena = request.password() != null && !request.password().isBlank();
        if (cambiaContrasena) {
            estilista.setPassword(passwordEncoder.encode(request.password()));
        }
        Usuario guardado = usuarioRepository.save(estilista);
        if (cambiaContrasena || desactivar) {
            // Sus dispositivos dejan de poder refrescar; el access token vigente vence en máximo 15 min
            sesionService.revocarTodas(guardado.getId());
        }
        return estilistaMapper.toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<EstilistaResponse> listar(Long salonId) {
        return usuarioRepository.findBySalonIdAndRolOrderByNombreAsc(salonId, Rol.ESTILISTA).stream()
                .map(estilistaMapper::toResponse)
                .toList();
    }

    /** Estilistas activos del salón que ofrecen el servicio, ordenados por nombre. */
    @Transactional(readOnly = true)
    public List<Usuario> activosQueOfrecen(Long salonId, Long servicioId) {
        return usuarioRepository.findBySalonIdAndRolAndActivoTrueOrderByNombreAsc(salonId, Rol.ESTILISTA).stream()
                .filter(estilista -> estilista.ofrece(servicioId))
                .toList();
    }

    /** El estilista debe existir en el salón y estar activo (404 si no). */
    @Transactional(readOnly = true)
    public Usuario obtenerActivo(Long salonId, Long estilistaId) {
        return usuarioRepository.findByIdAndSalonIdAndRol(estilistaId, salonId, Rol.ESTILISTA)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el estilista con id " + estilistaId));
    }

    private void validarCupoDelPlan(Salon salon) {
        long activos = usuarioRepository.countBySalonIdAndRolAndActivoTrue(salon.getId(), Rol.ESTILISTA);
        if (salon.getPlan() == Plan.GRATIS && activos >= negocio.limiteEstilistasGratis()) {
            throw new PagoRequeridoException("El plan GRATIS permite máximo "
                    + negocio.limiteEstilistasGratis() + " estilistas activos. Pásate al plan PRO para agregar más");
        }
    }

    private List<Servicio> serviciosDelSalon(Long salonId, Collection<Long> ids) {
        Set<Long> idsPedidos = new HashSet<>(ids);
        List<Servicio> servicios = servicioRepository.findByIdInAndSalonId(idsPedidos, salonId);
        if (servicios.size() != idsPedidos.size()) {
            throw new RecursoNoEncontradoException("Uno o más servicios no existen en este salón");
        }
        return servicios;
    }
}
