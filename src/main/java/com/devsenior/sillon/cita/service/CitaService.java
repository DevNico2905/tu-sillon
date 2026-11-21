package com.devsenior.sillon.cita.service;

import com.devsenior.sillon.cita.dto.CitaRequest;
import com.devsenior.sillon.cita.dto.CitaResponse;
import com.devsenior.sillon.cita.dto.DatosRecordatorio;
import com.devsenior.sillon.cita.dto.DisponibilidadResponse;
import com.devsenior.sillon.cita.mapper.CitaMapper;
import com.devsenior.sillon.cita.model.Cita;
import com.devsenior.sillon.cita.model.EstadoCita;
import com.devsenior.sillon.cita.model.OrigenCita;
import com.devsenior.sillon.cita.repository.CitaRepository;
import com.devsenior.sillon.cliente.model.Cliente;
import com.devsenior.sillon.cliente.service.ClienteService;
import com.devsenior.sillon.compartido.excepcion.AccionNoPermitidaException;
import com.devsenior.sillon.compartido.excepcion.RecursoNoEncontradoException;
import com.devsenior.sillon.compartido.excepcion.ReglaNegocioException;
import com.devsenior.sillon.compartido.util.FormatoColombia;
import com.devsenior.sillon.salon.model.Salon;
import com.devsenior.sillon.salon.service.SalonService;
import com.devsenior.sillon.servicio.model.Servicio;
import com.devsenior.sillon.servicio.service.ServicioService;
import com.devsenior.sillon.usuario.model.Usuario;
import com.devsenior.sillon.usuario.service.EstilistaService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CitaService {

    /** Lo único que un estilista puede hacer con sus citas. */
    private static final Set<EstadoCita> DESTINOS_DEL_ESTILISTA = Set.of(EstadoCita.COMPLETADA, EstadoCita.NO_ASISTIO);

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final CitaRepository citaRepository;
    private final SalonService salonService;
    private final ServicioService servicioService;
    private final EstilistaService estilistaService;
    private final ClienteService clienteService;
    private final ReglasAgenda reglasAgenda;
    private final CitaMapper citaMapper;

    public CitaService(CitaRepository citaRepository, SalonService salonService, ServicioService servicioService,
                       EstilistaService estilistaService, ClienteService clienteService,
                       ReglasAgenda reglasAgenda, CitaMapper citaMapper) {
        this.citaRepository = citaRepository;
        this.salonService = salonService;
        this.servicioService = servicioService;
        this.estilistaService = estilistaService;
        this.clienteService = clienteService;
        this.reglasAgenda = reglasAgenda;
        this.citaMapper = citaMapper;
    }

    /** Cita creada por el salón: nace CONFIRMADA con origen SALON. */
    @Transactional
    public CitaResponse crearDesdeSalon(Long salonId, CitaRequest request) {
        return citaMapper.toResponse(agendar(salonId, request, EstadoCita.CONFIRMADA, OrigenCita.SALON));
    }

    /** Reserva del cliente final: nace PENDIENTE con origen EN_LINEA. Mismo método de agendar. */
    @Transactional
    public CitaResponse reservarEnLinea(Long salonId, CitaRequest request) {
        return citaMapper.toResponse(agendar(salonId, request, EstadoCita.PENDIENTE, OrigenCita.EN_LINEA));
    }

    /**
     * Único punto de entrada para agendar (SPEC R18 y R40). Orden:
     * 1. Servicio del salón y activo (404). 2. Si llega estilista: del salón y activo (404) y que ofrece el servicio (409).
     * 3. Fin = inicio + duración. 4. No en el pasado (409). 5. Horario del salón (409).
     * 6. Con estilista: sin cruces (409). Sin estilista: se asigna uno libre (409 si no hay).
     * 7. Cliente, precio congelado, estado y origen.
     */
    private Cita agendar(Long salonId, CitaRequest request, EstadoCita estadoInicial, OrigenCita origen) {
        Servicio servicio = obtenerServicioActivo(salonId, request.servicioId());
        Usuario elegido = request.estilistaId() == null ? null
                : obtenerEstilistaQueOfrece(salonId, request.estilistaId(), servicio);

        LocalDateTime inicio = request.inicio().withSecond(0).withNano(0);
        LocalDateTime fin = inicio.plusMinutes(servicio.getDuracionMinutos());

        reglasAgenda.validarNoPasado(inicio);

        Salon salon = salonService.obtenerEntidad(salonId);
        reglasAgenda.validarHorario(salon.getConfiguracion(), inicio, fin);

        Usuario estilista;
        if (elegido != null) {
            if (tieneCruce(elegido, inicio, fin)) {
                throw new ReglaNegocioException(elegido.getNombre() + " ya tiene una cita en ese horario");
            }
            estilista = elegido;
        } else {
            estilista = asignarEstilistaLibre(salonId, servicio, inicio, fin);
        }

        Cliente cliente = clienteService.buscarOCrear(salon, request.clienteNombre(), request.clienteTelefono());

        Cita cita = new Cita();
        cita.setSalon(salon);
        cita.setCliente(cliente);
        cita.setEstilista(estilista);
        cita.setServicio(servicio);
        cita.setInicio(inicio);
        cita.setFin(fin);
        cita.setPrecio(servicio.getPrecio());
        cita.setEstado(estadoInicial);
        cita.setOrigen(origen);
        cita.setNota(request.nota() == null || request.nota().isBlank() ? null : request.nota().trim());
        return citaRepository.save(cita);
    }

    /**
     * Horas de inicio libres para un servicio en una fecha. La calcula Java, nunca la IA.
     * Con estilista: sus horas (404 si no existe, 409 si no ofrece el servicio).
     * Sin estilista (R40): las horas en que AL MENOS UN estilista activo que ofrece el servicio está libre;
     * el campo estilista de la respuesta va en null. Si nadie ofrece el servicio, la lista va vacía.
     */
    @Transactional(readOnly = true)
    public DisponibilidadResponse disponibilidad(Long salonId, Long servicioId, Long estilistaId, LocalDate fecha) {
        Servicio servicio = obtenerServicioActivo(salonId, servicioId);
        Usuario elegido = estilistaId == null ? null : obtenerEstilistaQueOfrece(salonId, estilistaId, servicio);
        Salon salon = salonService.obtenerEntidad(salonId);

        List<Usuario> estilistas = elegido != null ? List.of(elegido)
                : estilistaService.activosQueOfrecen(salonId, servicio.getId());

        // TreeSet: une las horas de todos los estilistas sin repetir y en orden
        TreeSet<LocalTime> libres = new TreeSet<>();
        for (Usuario estilista : estilistas) {
            libres.addAll(reglasAgenda.horariosLibres(salon.getConfiguracion(), fecha,
                    servicio.getDuracionMinutos(), ocupadosDelDia(estilista, fecha)));
        }
        return new DisponibilidadResponse(fecha, servicio.getNombre(), elegido == null ? null : elegido.getNombre(),
                servicio.getDuracionMinutos(), libres.stream().map(FORMATO_HORA::format).toList());
    }

    /**
     * Sin estilista elegido (R40): entre los activos que ofrecen el servicio y están libres en [inicio, fin),
     * el que tenga menos citas ese día; si empatan, el primero por nombre. 409 si no hay ninguno.
     */
    private Usuario asignarEstilistaLibre(Long salonId, Servicio servicio, LocalDateTime inicio, LocalDateTime fin) {
        List<Usuario> candidatos = estilistaService.activosQueOfrecen(salonId, servicio.getId());
        if (candidatos.isEmpty()) {
            throw new ReglaNegocioException("Nadie en el salón ofrece el servicio " + servicio.getNombre());
        }
        List<Usuario> libres = candidatos.stream().filter(estilista -> !tieneCruce(estilista, inicio, fin)).toList();
        if (libres.isEmpty()) {
            throw new ReglaNegocioException("Ya no hay estilistas libres para " + servicio.getNombre() + " en ese horario");
        }
        if (libres.size() == 1) {
            return libres.get(0);
        }
        LocalDateTime desde = inicio.toLocalDate().atStartOfDay();
        LocalDateTime hasta = desde.plusDays(1);
        return libres.stream()
                .min(Comparator.comparingLong((Usuario estilista) -> citaRepository
                                .countByEstilistaIdAndEstadoInAndInicioGreaterThanEqualAndInicioLessThan(
                                        estilista.getId(), EstadoCita.OCUPAN_AGENDA, desde, hasta))
                        .thenComparing(Usuario::getNombre))
                .orElseThrow();
    }

    private Servicio obtenerServicioActivo(Long salonId, Long servicioId) {
        Servicio servicio = servicioService.obtenerEntidad(salonId, servicioId);
        if (!servicio.isActivo()) {
            throw new RecursoNoEncontradoException("No existe un servicio activo con id " + servicioId);
        }
        return servicio;
    }

    private Usuario obtenerEstilistaQueOfrece(Long salonId, Long estilistaId, Servicio servicio) {
        Usuario estilista = estilistaService.obtenerActivo(salonId, estilistaId);
        if (!estilista.ofrece(servicio.getId())) {
            throw new ReglaNegocioException(estilista.getNombre() + " no ofrece el servicio " + servicio.getNombre());
        }
        return estilista;
    }

    private boolean tieneCruce(Usuario estilista, LocalDateTime inicio, LocalDateTime fin) {
        return citaRepository.existsByEstilistaIdAndEstadoInAndInicioLessThanAndFinGreaterThan(
                estilista.getId(), EstadoCita.OCUPAN_AGENDA, fin, inicio);
    }

    private List<ReglasAgenda.Intervalo> ocupadosDelDia(Usuario estilista, LocalDate fecha) {
        return citaRepository
                .findByEstilistaIdAndEstadoInAndInicioLessThanAndFinGreaterThan(
                        estilista.getId(), EstadoCita.OCUPAN_AGENDA, fecha.plusDays(1).atStartOfDay(), fecha.atStartOfDay())
                .stream()
                .map(cita -> new ReglasAgenda.Intervalo(cita.getInicio(), cita.getFin()))
                .toList();
    }

    /** Agenda de un día (hoy si no llega fecha). El dueño ve todas; el estilista solo las suyas. */
    @Transactional(readOnly = true)
    public List<CitaResponse> listarDelDia(Long salonId, Long usuarioId, boolean esEstilista, LocalDate fecha) {
        LocalDate dia = fecha != null ? fecha : reglasAgenda.hoy();
        LocalDateTime desde = dia.atStartOfDay();
        LocalDateTime hasta = dia.plusDays(1).atStartOfDay();
        List<Cita> citas = esEstilista
                ? citaRepository.findBySalonIdAndEstilistaIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
                        salonId, usuarioId, desde, hasta)
                : citaRepository.findBySalonIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(
                        salonId, desde, hasta);
        return citas.stream().map(citaMapper::toResponse).toList();
    }

    /**
     * Orden (SPEC R19): existe en el salón (404) → si es estilista, debe ser suya (404) y el destino
     * COMPLETADA o NO_ASISTIO (403) → la transición debe ser válida (409) → saveAndFlush.
     */
    @Transactional
    public CitaResponse cambiarEstado(Long salonId, Long usuarioId, boolean esEstilista, Long citaId, EstadoCita destino) {
        Cita cita = obtenerEntidad(salonId, citaId);
        if (esEstilista) {
            if (!cita.getEstilista().getId().equals(usuarioId)) {
                throw new RecursoNoEncontradoException("No existe la cita con id " + citaId);
            }
            if (!DESTINOS_DEL_ESTILISTA.contains(destino)) {
                throw new AccionNoPermitidaException("Un estilista solo puede marcar sus citas como COMPLETADA o NO_ASISTIO");
            }
        }
        if (!cita.getEstado().puedeCambiarA(destino)) {
            throw new ReglaNegocioException("No se puede pasar una cita de " + cita.getEstado() + " a " + destino);
        }
        cita.setEstado(destino);
        // saveAndFlush: el UPDATE (y @PreUpdate) ocurre antes de armar la respuesta, así fechaActualizacion sale nueva
        return citaMapper.toResponse(citaRepository.saveAndFlush(cita));
    }

    /**
     * Datos verificados para el recordatorio (solo lectura). Solo citas PENDIENTE o CONFIRMADA (otra: 409).
     * Java calcula y escribe cada dato; la IA solo los usa para redactar.
     */
    @Transactional(readOnly = true)
    public DatosRecordatorio datosRecordatorio(Long salonId, Long citaId) {
        Cita cita = obtenerEntidad(salonId, citaId);
        if (!cita.getEstado().ocupaAgenda()) {
            throw new ReglaNegocioException("Solo se envían recordatorios de citas PENDIENTE o CONFIRMADA; esta está "
                    + cita.getEstado());
        }
        Salon salon = cita.getSalon();
        String datos = String.join("\n",
                "- Servicio: " + cita.getServicio().getNombre() + " (" + cita.getServicio().getDuracionMinutos() + " minutos)",
                "- Estilista: " + cita.getEstilista().getNombre(),
                "- Fecha: " + FormatoColombia.fecha(cita.getInicio().toLocalDate()),
                "- Hora: " + FormatoColombia.hora(cita.getInicio().toLocalTime()),
                "- Valor: " + FormatoColombia.pesos(cita.getPrecio()),
                "- Dirección: " + salon.getDireccion() + ", " + salon.getCiudad());
        return new DatosRecordatorio(cita.getId(), salon.getId(), salon.getNombre(),
                cita.getCliente().getNombre(), cita.getCliente().getTelefono(), datos);
    }

    @Transactional(readOnly = true)
    public Cita obtenerEntidad(Long salonId, Long citaId) {
        return citaRepository.findByIdAndSalonId(citaId, salonId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la cita con id " + citaId));
    }
}
