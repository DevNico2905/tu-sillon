package com.devsenior.sillon.salon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devsenior.sillon.compartido.excepcion.RecursoNoEncontradoException;
import com.devsenior.sillon.salon.dto.DatosSalonRequest;
import com.devsenior.sillon.salon.dto.SalonResponse;
import com.devsenior.sillon.salon.mapper.SalonMapper;
import com.devsenior.sillon.salon.model.ConfiguracionSalon;
import com.devsenior.sillon.salon.model.Plan;
import com.devsenior.sillon.salon.model.Salon;
import com.devsenior.sillon.salon.repository.SalonRepository;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalonServiceTest {

    @Mock
    private SalonRepository salonRepository;

    private SalonService salonService;

    @BeforeEach
    void setUp() {
        salonService = new SalonService(salonRepository, new SalonMapper());
    }

    @Test
    void actualizarDatos_limpiaEspaciosYConservaElSlug() {
        Salon salon = salon();
        when(salonRepository.findById(1L)).thenReturn(Optional.of(salon));
        when(salonRepository.save(any(Salon.class))).thenAnswer(inv -> inv.getArgument(0));

        SalonResponse respuesta = salonService.actualizarDatos(1L, new DatosSalonRequest(
                "  La Navaja Barber Club ", " Rionegro ", " Cra. 50 # 47-20, local 3 ", " 604 444 1234 "));

        assertEquals("La Navaja Barber Club", respuesta.nombre());
        assertEquals("Rionegro", respuesta.ciudad());
        assertEquals("Cra. 50 # 47-20, local 3", respuesta.direccion());
        assertEquals("604 444 1234", respuesta.telefono());
        assertEquals("la-navaja", respuesta.slug());
    }

    @Test
    void actualizarDatos_salonInexistente_lanza404() {
        when(salonRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> salonService.actualizarDatos(
                1L, new DatosSalonRequest("X", "Rionegro", "Calle 1", "300")));
        verify(salonRepository, never()).save(any());
    }

    private Salon salon() {
        Salon salon = new Salon();
        salon.setId(1L);
        salon.setNombre("La Navaja");
        salon.setSlug("la-navaja");
        salon.setCiudad("Medellín");
        salon.setDireccion("Calle 10 # 40-20");
        salon.setTelefono("3001234567");
        salon.setPlan(Plan.GRATIS);
        salon.setConfiguracion(new ConfiguracionSalon(LocalTime.of(9, 0), LocalTime.of(19, 0), false, 30));
        return salon;
    }
}
