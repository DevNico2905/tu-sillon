package com.devsenior.sillon.publico.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.devsenior.sillon.publico.dto.CatalogoResponse;
import com.devsenior.sillon.salon.model.ConfiguracionSalon;
import com.devsenior.sillon.salon.model.Salon;
import com.devsenior.sillon.salon.service.SalonService;
import com.devsenior.sillon.servicio.mapper.ServicioMapper;
import com.devsenior.sillon.servicio.repository.ServicioRepository;
import com.devsenior.sillon.usuario.model.Rol;
import com.devsenior.sillon.usuario.repository.UsuarioRepository;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** SPEC R42: el catálogo público trae el horario en texto y en datos, y ambos dicen lo mismo. */
@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock
    private SalonService salonService;
    @Mock
    private ServicioRepository servicioRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    private CatalogoService catalogoService;
    private Salon salon;

    @BeforeEach
    void preparar() {
        catalogoService = new CatalogoService(salonService, servicioRepository, usuarioRepository, new ServicioMapper());
        salon = new Salon();
        salon.setId(1L);
        salon.setNombre("Barbería Centro");
        salon.setSlug("barberia-centro");
        when(salonService.obtenerPorSlug("barberia-centro")).thenReturn(salon);
        when(servicioRepository.findBySalonIdAndActivoTrueOrderByNombreAsc(1L)).thenReturn(List.of());
        when(usuarioRepository.findBySalonIdAndRolAndActivoTrueOrderByNombreAsc(1L, Rol.ESTILISTA)).thenReturn(List.of());
    }

    @Test
    void horarioPorDefectoEnTextoYEnDatos() {
        salon.setConfiguracion(ConfiguracionSalon.porDefecto());

        CatalogoResponse catalogo = catalogoService.catalogo("barberia-centro");

        assertEquals(LocalTime.of(8, 0), catalogo.horaApertura());
        assertEquals(LocalTime.of(19, 0), catalogo.horaCierre());
        assertFalse(catalogo.abreDomingos());
        assertEquals(salon.getConfiguracion().descripcion(), catalogo.horario());
    }

    @Test
    void salonQueAbreDomingosConHorarioPropio() {
        salon.setConfiguracion(new ConfiguracionSalon(LocalTime.of(9, 30), LocalTime.of(18, 0), true, 30));

        CatalogoResponse catalogo = catalogoService.catalogo("barberia-centro");

        assertEquals(LocalTime.of(9, 30), catalogo.horaApertura());
        assertEquals(LocalTime.of(18, 0), catalogo.horaCierre());
        assertTrue(catalogo.abreDomingos());
        assertTrue(catalogo.horario().startsWith("Lunes a domingo"));
    }
}
