package com.devsenior.sillon.cita.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devsenior.sillon.cita.dto.CitaRequest;
import com.devsenior.sillon.cita.dto.CitaResponse;
import com.devsenior.sillon.cita.dto.DisponibilidadResponse;
import com.devsenior.sillon.cita.mapper.CitaMapper;
import com.devsenior.sillon.cita.model.Cita;
import com.devsenior.sillon.cita.model.EstadoCita;
import com.devsenior.sillon.cita.repository.CitaRepository;
import com.devsenior.sillon.cliente.model.Cliente;
import com.devsenior.sillon.cliente.service.ClienteService;
import com.devsenior.sillon.compartido.excepcion.ReglaNegocioException;
import com.devsenior.sillon.salon.model.ConfiguracionSalon;
import com.devsenior.sillon.salon.model.Salon;
import com.devsenior.sillon.salon.service.SalonService;
import com.devsenior.sillon.servicio.model.Servicio;
import com.devsenior.sillon.servicio.service.ServicioService;
import com.devsenior.sillon.usuario.model.Usuario;
import com.devsenior.sillon.usuario.service.EstilistaService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** SPEC R40: agendar y consultar disponibilidad con "cualquier estilista". Reloj fijo: lunes 19 oct 2026, 9:10 a. m. */
@ExtendWith(MockitoExtension.class)
class CitaServiceSinEstilistaTest {

    private static final Long SALON_ID = 1L;
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 19);
    private static final LocalDate MARTES = LocalDate.of(2026, 10, 20);

    @Mock
    private CitaRepository citaRepository;
    @Mock
    private SalonService salonService;
    @Mock
    private ServicioService servicioService;
    @Mock
    private EstilistaService estilistaService;
    @Mock
    private ClienteService clienteService;

    private CitaService citaService;
    private Salon salon;
    private Servicio corte;
    private Usuario mateo;
    private Usuario sara;

    @BeforeEach
    void setUp() {
        Clock reloj = Clock.fixed(HOY.atTime(9, 10).atZone(BOGOTA).toInstant(), BOGOTA);
        citaService = new CitaService(citaRepository, salonService, servicioService, estilistaService,
                clienteService, new ReglasAgenda(reloj), new CitaMapper());

        salon = new Salon();
        salon.setId(SALON_ID);
        salon.setConfiguracion(ConfiguracionSalon.porDefecto());
        corte = new Servicio();
        corte.setId(10L);
        corte.setNombre("Corte clásico");
        corte.setDuracionMinutos(45);
        corte.setPrecio(35000);
        corte.setActivo(true);
        mateo = estilista(3L, "Mateo Gil");
        sara = estilista(4L, "Sara Mejía");

        when(servicioService.obtenerEntidad(SALON_ID, corte.getId())).thenReturn(corte);
    }

    // ---------- disponibilidad ----------

    @Test
    void disponibilidad_sinEstilista_uneLasHorasDeTodosYNoNombraEstilista() {
        when(salonService.obtenerEntidad(SALON_ID)).thenReturn(salon);
        when(estilistaService.activosQueOfrecen(SALON_ID, corte.getId())).thenReturn(List.of(mateo, sara));
        // Mateo ocupado de 10:00 a 10:45; Sara libre todo el día
        when(citaRepository.findByEstilistaIdAndEstadoInAndInicioLessThanAndFinGreaterThan(
                eq(mateo.getId()), anyCollection(), any(), any()))
                .thenReturn(List.of(citaOcupada(mateo, MARTES.atTime(10, 0), MARTES.atTime(10, 45))));
        when(citaRepository.findByEstilistaIdAndEstadoInAndInicioLessThanAndFinGreaterThan(
                eq(sara.getId()), anyCollection(), any(), any()))
                .thenReturn(List.of());

        DisponibilidadResponse respuesta = citaService.disponibilidad(SALON_ID, corte.getId(), null, MARTES);

        assertNull(respuesta.estilista());
        assertTrue(respuesta.horariosLibres().contains("10:00:00"), "Sara está libre a las 10:00");
        // 8:00 a 18:15 cada 30 min = 21 horas, sin repetir aunque las den los dos
        assertEquals(21, respuesta.horariosLibres().size());
        assertEquals("08:00:00", respuesta.horariosLibres().get(0));
    }

    @Test
    void disponibilidad_sinEstilistaYNadieOfreceElServicio_listaVacia() {
        when(salonService.obtenerEntidad(SALON_ID)).thenReturn(salon);
        when(estilistaService.activosQueOfrecen(SALON_ID, corte.getId())).thenReturn(List.of());

        DisponibilidadResponse respuesta = citaService.disponibilidad(SALON_ID, corte.getId(), null, MARTES);

        assertTrue(respuesta.horariosLibres().isEmpty());
    }

    // ---------- reservar sin estilista ----------

    @Test
    void reservar_sinEstilista_asignaAlUnicoLibre() {
        prepararReserva(List.of(mateo, sara));
        cruce(mateo, true);
        cruce(sara, false);

        CitaResponse cita = citaService.reservarEnLinea(SALON_ID, requestSinEstilista(MARTES.atTime(10, 0)));

        assertEquals(sara.getId(), cita.estilistaId());
        assertEquals(EstadoCita.PENDIENTE, cita.estado());
    }

    @Test
    void reservar_sinEstilista_dosLibres_asignaAlDeMenosCitasEseDia() {
        prepararReserva(List.of(mateo, sara));
        cruce(mateo, false);
        cruce(sara, false);
        citasDelDia(mateo, 3);
        citasDelDia(sara, 1);

        CitaResponse cita = citaService.reservarEnLinea(SALON_ID, requestSinEstilista(MARTES.atTime(10, 0)));

        assertEquals(sara.getId(), cita.estilistaId());
    }

    @Test
    void reservar_sinEstilista_empate_asignaAlPrimeroPorNombre() {
        prepararReserva(List.of(mateo, sara));
        cruce(mateo, false);
        cruce(sara, false);
        citasDelDia(mateo, 2);
        citasDelDia(sara, 2);

        CitaResponse cita = citaService.reservarEnLinea(SALON_ID, requestSinEstilista(MARTES.atTime(10, 0)));

        assertEquals(mateo.getId(), cita.estilistaId());
    }

    @Test
    void reservar_sinEstilista_todosOcupados_lanza409YNoGuarda() {
        when(salonService.obtenerEntidad(SALON_ID)).thenReturn(salon);
        when(estilistaService.activosQueOfrecen(SALON_ID, corte.getId())).thenReturn(List.of(mateo, sara));
        cruce(mateo, true);
        cruce(sara, true);

        assertThrows(ReglaNegocioException.class,
                () -> citaService.reservarEnLinea(SALON_ID, requestSinEstilista(MARTES.atTime(10, 0))));
        verify(citaRepository, never()).save(any());
    }

    @Test
    void reservar_sinEstilista_nadieOfreceElServicio_lanza409() {
        when(salonService.obtenerEntidad(SALON_ID)).thenReturn(salon);
        when(estilistaService.activosQueOfrecen(SALON_ID, corte.getId())).thenReturn(List.of());

        assertThrows(ReglaNegocioException.class,
                () -> citaService.reservarEnLinea(SALON_ID, requestSinEstilista(MARTES.atTime(10, 0))));
        verify(citaRepository, never()).save(any());
    }

    @Test
    void reservar_sinEstilista_enElPasado_lanza409SinBuscarEstilistas() {
        assertThrows(ReglaNegocioException.class,
                () -> citaService.reservarEnLinea(SALON_ID, requestSinEstilista(HOY.atTime(8, 0))));
        verify(estilistaService, never()).activosQueOfrecen(any(), any());
    }

    // ---------- ayudas ----------

    private void prepararReserva(List<Usuario> candidatos) {
        when(salonService.obtenerEntidad(SALON_ID)).thenReturn(salon);
        when(estilistaService.activosQueOfrecen(SALON_ID, corte.getId())).thenReturn(candidatos);
        Cliente cliente = new Cliente();
        cliente.setNombre("Valentina Ríos");
        cliente.setTelefono("3001234567");
        when(clienteService.buscarOCrear(any(), anyString(), anyString())).thenReturn(cliente);
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void cruce(Usuario estilista, boolean ocupado) {
        when(citaRepository.existsByEstilistaIdAndEstadoInAndInicioLessThanAndFinGreaterThan(
                eq(estilista.getId()), anyCollection(), any(), any())).thenReturn(ocupado);
    }

    private void citasDelDia(Usuario estilista, long cantidad) {
        when(citaRepository.countByEstilistaIdAndEstadoInAndInicioGreaterThanEqualAndInicioLessThan(
                eq(estilista.getId()), anyCollection(), any(), any())).thenReturn(cantidad);
    }

    private CitaRequest requestSinEstilista(LocalDateTime inicio) {
        return new CitaRequest("Valentina Ríos", "300 123 4567", corte.getId(), null, inicio, null);
    }

    private Cita citaOcupada(Usuario estilista, LocalDateTime inicio, LocalDateTime fin) {
        Cita cita = new Cita();
        cita.setEstilista(estilista);
        cita.setInicio(inicio);
        cita.setFin(fin);
        cita.setEstado(EstadoCita.CONFIRMADA);
        return cita;
    }

    private Usuario estilista(Long id, String nombre) {
        Usuario estilista = new Usuario();
        estilista.setId(id);
        estilista.setNombre(nombre);
        estilista.setActivo(true);
        estilista.setServicios(Set.of(corte));
        return estilista;
    }
}
