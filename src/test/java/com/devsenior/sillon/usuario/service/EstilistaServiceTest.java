package com.devsenior.sillon.usuario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class EstilistaServiceTest {

    private static final Long SALON_ID = 1L;

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ServicioRepository servicioRepository;
    @Mock
    private SalonService salonService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CitaRepository citaRepository;
    @Mock
    private SesionService sesionService;

    /** 25 nov 2026, 10:00 en Bogotá. */
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T15:00:00Z"), ZoneId.of("America/Bogota"));

    private EstilistaService estilistaService;
    private Salon salon;

    @BeforeEach
    void setUp() {
        NegocioProperties negocio = new NegocioProperties(10, 300, 200, 2);
        estilistaService = new EstilistaService(usuarioRepository, servicioRepository, salonService,
                new EstilistaMapper(), passwordEncoder, negocio, citaRepository, clock, sesionService);
        salon = new Salon();
        salon.setId(SALON_ID);
        salon.setPlan(Plan.GRATIS);
        when(salonService.obtenerEntidad(SALON_ID)).thenReturn(salon);
    }

    @Test
    void crear_tercerEstilistaEnPlanGratis_lanza402YNoGuarda() {
        when(usuarioRepository.countBySalonIdAndRolAndActivoTrue(SALON_ID, Rol.ESTILISTA)).thenReturn(2L);

        assertThrows(PagoRequeridoException.class, () -> estilistaService.crear(SALON_ID, request(10L)));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void crear_tercerEstilistaEnPlanPro_loCrea() {
        salon.setPlan(Plan.PRO);
        when(usuarioRepository.countBySalonIdAndRolAndActivoTrue(SALON_ID, Rol.ESTILISTA)).thenReturn(2L);
        when(usuarioRepository.existsByEmail("luis@navaja.co")).thenReturn(false);
        when(servicioRepository.findByIdInAndSalonId(anyCollection(), anyLong())).thenReturn(List.of(servicio(10L, "Corte clásico")));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        EstilistaResponse respuesta = estilistaService.crear(SALON_ID, request(10L));

        assertEquals("luis@navaja.co", respuesta.email());
        assertEquals(List.of("Corte clásico"), respuesta.servicios());
    }

    @Test
    void crear_correoRepetido_lanza409() {
        when(usuarioRepository.countBySalonIdAndRolAndActivoTrue(SALON_ID, Rol.ESTILISTA)).thenReturn(0L);
        when(usuarioRepository.existsByEmail("luis@navaja.co")).thenReturn(true);

        assertThrows(ReglaNegocioException.class, () -> estilistaService.crear(SALON_ID, request(10L)));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void crear_servicioDeOtroSalon_lanza404() {
        when(usuarioRepository.countBySalonIdAndRolAndActivoTrue(SALON_ID, Rol.ESTILISTA)).thenReturn(0L);
        when(usuarioRepository.existsByEmail("luis@navaja.co")).thenReturn(false);
        when(servicioRepository.findByIdInAndSalonId(anyCollection(), anyLong())).thenReturn(List.of(servicio(10L, "Corte clásico")));

        assertThrows(RecursoNoEncontradoException.class, () -> estilistaService.crear(SALON_ID, request(10L, 99L)));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void actualizar_cambiaNombreServiciosYContrasena() {
        Usuario luis = estilista(true, servicio(10L, "Corte clásico"));
        when(usuarioRepository.findByIdAndSalonIdAndRol(5L, SALON_ID, Rol.ESTILISTA)).thenReturn(Optional.of(luis));
        when(servicioRepository.findByIdInAndSalonId(anyCollection(), anyLong()))
                .thenReturn(List.of(servicio(10L, "Corte clásico"), servicio(11L, "Barba")));
        when(passwordEncoder.encode("nueva12345")).thenReturn("hash-nuevo");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        EstilistaResponse respuesta = estilistaService.actualizar(SALON_ID, 5L,
                new EstilistaActualizacionRequest("  Luis Pérez G. ", List.of(10L, 11L), true, "nueva12345"));

        assertEquals("Luis Pérez G.", respuesta.nombre());
        assertEquals(List.of("Barba", "Corte clásico"), respuesta.servicios());
        assertEquals(List.of(10L, 11L), respuesta.servicioIds());
        assertEquals("hash-nuevo", luis.getPassword());
        verify(sesionService).revocarTodas(5L);
    }

    @Test
    void actualizar_sinContrasena_conservaLaActual() {
        Usuario luis = estilista(true, servicio(10L, "Corte clásico"));
        when(usuarioRepository.findByIdAndSalonIdAndRol(5L, SALON_ID, Rol.ESTILISTA)).thenReturn(Optional.of(luis));
        when(servicioRepository.findByIdInAndSalonId(anyCollection(), anyLong())).thenReturn(List.of(servicio(10L, "Corte clásico")));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        estilistaService.actualizar(SALON_ID, 5L, new EstilistaActualizacionRequest("Luis", List.of(10L), true, null));

        assertEquals("hash-original", luis.getPassword());
        verify(passwordEncoder, never()).encode(anyString());
        verify(sesionService, never()).revocarTodas(anyLong());
    }

    @Test
    void desactivar_conCitasSinTerminar_lanza409YNoGuarda() {
        Usuario luis = estilista(true, servicio(10L, "Corte clásico"));
        when(usuarioRepository.findByIdAndSalonIdAndRol(5L, SALON_ID, Rol.ESTILISTA)).thenReturn(Optional.of(luis));
        when(citaRepository.countByEstilistaIdAndEstadoInAndFinGreaterThan(
                5L, EstadoCita.OCUPAN_AGENDA, LocalDateTime.of(2026, 11, 25, 10, 0))).thenReturn(2L);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> estilistaService.actualizar(
                SALON_ID, 5L, new EstilistaActualizacionRequest("Luis", List.of(10L), false, null)));

        assertTrue(error.getMessage().contains("2 citas pendientes o confirmadas"));
        assertTrue(luis.isActivo());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void desactivar_sinCitasSinTerminar_loDesactiva() {
        Usuario luis = estilista(true, servicio(10L, "Corte clásico"));
        when(usuarioRepository.findByIdAndSalonIdAndRol(5L, SALON_ID, Rol.ESTILISTA)).thenReturn(Optional.of(luis));
        when(citaRepository.countByEstilistaIdAndEstadoInAndFinGreaterThan(anyLong(), anyCollection(), any())).thenReturn(0L);
        when(servicioRepository.findByIdInAndSalonId(anyCollection(), anyLong())).thenReturn(List.of(servicio(10L, "Corte clásico")));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        EstilistaResponse respuesta = estilistaService.actualizar(
                SALON_ID, 5L, new EstilistaActualizacionRequest("Luis", List.of(10L), false, null));

        assertFalse(respuesta.activo());
        verify(sesionService).revocarTodas(5L);
    }

    @Test
    void reactivar_enPlanGratisSinCupo_lanza402() {
        Usuario luis = estilista(false, servicio(10L, "Corte clásico"));
        when(usuarioRepository.findByIdAndSalonIdAndRol(5L, SALON_ID, Rol.ESTILISTA)).thenReturn(Optional.of(luis));
        when(usuarioRepository.countBySalonIdAndRolAndActivoTrue(SALON_ID, Rol.ESTILISTA)).thenReturn(2L);

        assertThrows(PagoRequeridoException.class, () -> estilistaService.actualizar(
                SALON_ID, 5L, new EstilistaActualizacionRequest("Luis", List.of(10L), true, null)));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void actualizar_estilistaDeOtroSalon_lanza404() {
        when(usuarioRepository.findByIdAndSalonIdAndRol(5L, SALON_ID, Rol.ESTILISTA)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> estilistaService.actualizar(
                SALON_ID, 5L, new EstilistaActualizacionRequest("Luis", List.of(10L), true, null)));
    }

    @Test
    void actualizar_servicioDeOtroSalon_lanza404() {
        Usuario luis = estilista(true, servicio(10L, "Corte clásico"));
        when(usuarioRepository.findByIdAndSalonIdAndRol(5L, SALON_ID, Rol.ESTILISTA)).thenReturn(Optional.of(luis));
        when(servicioRepository.findByIdInAndSalonId(anyCollection(), anyLong())).thenReturn(List.of(servicio(10L, "Corte clásico")));

        assertThrows(RecursoNoEncontradoException.class, () -> estilistaService.actualizar(
                SALON_ID, 5L, new EstilistaActualizacionRequest("Luis", List.of(10L, 99L), true, null)));
        verify(usuarioRepository, never()).save(any());
    }

    private Usuario estilista(boolean activo, Servicio... servicios) {
        Usuario estilista = new Usuario();
        estilista.setId(5L);
        estilista.setNombre("Luis Pérez");
        estilista.setEmail("luis@navaja.co");
        estilista.setPassword("hash-original");
        estilista.setRol(Rol.ESTILISTA);
        estilista.setActivo(activo);
        estilista.setSalon(salon);
        estilista.setServicios(new HashSet<>(Set.of(servicios)));
        return estilista;
    }

    private EstilistaRequest request(Long... servicioIds) {
        return new EstilistaRequest("Luis Pérez", " Luis@Navaja.co ", "luis123456", List.of(servicioIds));
    }

    private Servicio servicio(Long id, String nombre) {
        Servicio servicio = new Servicio();
        servicio.setId(id);
        servicio.setNombre(nombre);
        return servicio;
    }
}
