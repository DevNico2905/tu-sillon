package com.devsenior.sillon.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devsenior.sillon.auth.config.SesionProperties;
import com.devsenior.sillon.auth.model.SesionRefresco;
import com.devsenior.sillon.auth.repository.SesionRefrescoRepository;
import com.devsenior.sillon.compartido.excepcion.SesionInvalidaException;
import com.devsenior.sillon.usuario.model.Usuario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SesionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-19T15:00:00Z");
    private static final String VALOR = "valor-del-refresh-token";
    private static final String FAMILIA = "familia-1";

    @Mock
    private SesionRefrescoRepository repositorio;

    private SesionService servicio;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        SesionProperties propiedades = new SesionProperties(7, 30, 10, true);
        servicio = new SesionService(repositorio, propiedades, Clock.fixed(AHORA, ZoneOffset.UTC));
        usuario = new Usuario();
        usuario.setId(5L);
        usuario.setActivo(true);
    }

    // ---------- emitir ----------

    @Test
    void emitir_guardaSoloElHashConSusVencimientos() {
        String valor = servicio.emitir(usuario);

        SesionRefresco guardada = capturarGuardada();
        assertEquals(SesionService.hash(valor), guardada.getHash());
        assertNotEquals(valor, guardada.getHash());
        assertEquals(AHORA.plus(Duration.ofDays(7)), guardada.getExpiraEn());
        assertEquals(AHORA.plus(Duration.ofDays(30)), guardada.getExpiraAbsoluto());
    }

    // ---------- rotar: caso feliz ----------

    @Test
    void rotar_tokenValido_emiteOtroDeLaMismaFamiliaSinExtenderElMaximo() {
        SesionRefresco actual = sesion(AHORA.plus(Duration.ofDays(3)), AHORA.plus(Duration.ofDays(20)));
        when(repositorio.findByHash(SesionService.hash(VALOR))).thenReturn(Optional.of(actual));
        when(repositorio.marcarUsada(1L, AHORA)).thenReturn(1);

        SesionService.Rotacion rotacion = servicio.rotar(VALOR);

        SesionRefresco nueva = capturarGuardada();
        assertSame(usuario, rotacion.usuario());
        assertNotEquals(VALOR, rotacion.nuevoValor());
        assertEquals(FAMILIA, nueva.getFamilia());
        assertEquals(actual.getExpiraAbsoluto(), nueva.getExpiraAbsoluto());
        assertEquals(AHORA.plus(Duration.ofDays(7)), nueva.getExpiraEn());
    }

    @Test
    void rotar_cercaDelMaximo_laInactividadNoPasaDelAbsoluto() {
        Instant absoluto = AHORA.plus(Duration.ofDays(2));
        when(repositorio.findByHash(SesionService.hash(VALOR))).thenReturn(Optional.of(sesion(absoluto, absoluto)));
        when(repositorio.marcarUsada(1L, AHORA)).thenReturn(1);

        servicio.rotar(VALOR);

        assertEquals(absoluto, capturarGuardada().getExpiraEn());
    }

    // ---------- rotar: reuso ----------

    @Test
    void rotar_reusoFueraDeLaGracia_revocaTodaLaFamilia() {
        SesionRefresco usada = sesionVigente();
        usada.setUsadoEn(AHORA.minusSeconds(60));
        when(repositorio.findByHash(SesionService.hash(VALOR))).thenReturn(Optional.of(usada));

        assertThrows(SesionInvalidaException.class, () -> servicio.rotar(VALOR));
        verify(repositorio).revocarFamilia(FAMILIA, AHORA);
    }

    @Test
    void rotar_reusoDentroDeLaGracia_otraPestana_noRevoca() {
        SesionRefresco usada = sesionVigente();
        usada.setUsadoEn(AHORA.minusSeconds(3));
        when(repositorio.findByHash(SesionService.hash(VALOR))).thenReturn(Optional.of(usada));

        assertThrows(SesionInvalidaException.class, () -> servicio.rotar(VALOR));
        verify(repositorio, never()).revocarFamilia(anyString(), any());
    }

    @Test
    void rotar_carreraPerdida_noRevocaNiEmite() {
        when(repositorio.findByHash(SesionService.hash(VALOR))).thenReturn(Optional.of(sesionVigente()));
        when(repositorio.marcarUsada(1L, AHORA)).thenReturn(0);

        assertThrows(SesionInvalidaException.class, () -> servicio.rotar(VALOR));
        verify(repositorio, never()).revocarFamilia(anyString(), any());
        verify(repositorio, never()).save(any());
    }

    // ---------- rotar: rechazos ----------

    @Test
    void rotar_vencidoPorInactividad_lanza401() {
        when(repositorio.findByHash(SesionService.hash(VALOR)))
                .thenReturn(Optional.of(sesion(AHORA.minusSeconds(1), AHORA.plus(Duration.ofDays(20)))));

        assertThrows(SesionInvalidaException.class, () -> servicio.rotar(VALOR));
    }

    @Test
    void rotar_vencidoPorMaximoAbsoluto_lanza401() {
        when(repositorio.findByHash(SesionService.hash(VALOR)))
                .thenReturn(Optional.of(sesion(AHORA.plus(Duration.ofDays(3)), AHORA.minusSeconds(1))));

        assertThrows(SesionInvalidaException.class, () -> servicio.rotar(VALOR));
    }

    @Test
    void rotar_revocado_lanza401() {
        SesionRefresco revocada = sesionVigente();
        revocada.setRevocadoEn(AHORA.minusSeconds(30));
        when(repositorio.findByHash(SesionService.hash(VALOR))).thenReturn(Optional.of(revocada));

        assertThrows(SesionInvalidaException.class, () -> servicio.rotar(VALOR));
    }

    @Test
    void rotar_usuarioDesactivado_revocaLaFamilia() {
        usuario.setActivo(false);
        when(repositorio.findByHash(SesionService.hash(VALOR))).thenReturn(Optional.of(sesionVigente()));

        assertThrows(SesionInvalidaException.class, () -> servicio.rotar(VALOR));
        verify(repositorio).revocarFamilia(FAMILIA, AHORA);
    }

    @Test
    void rotar_tokenDesconocido_lanza401() {
        when(repositorio.findByHash(SesionService.hash(VALOR))).thenReturn(Optional.empty());

        assertThrows(SesionInvalidaException.class, () -> servicio.rotar(VALOR));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rotar_sinCookie_lanza401SinConsultarLaBase(String valor) {
        assertThrows(SesionInvalidaException.class, () -> servicio.rotar(valor));
        verify(repositorio, never()).findByHash(anyString());
    }

    // ---------- revocar ----------

    @Test
    void revocar_tokenConocido_revocaSuFamilia() {
        when(repositorio.findByHash(SesionService.hash(VALOR))).thenReturn(Optional.of(sesionVigente()));

        servicio.revocar(VALOR);

        verify(repositorio).revocarFamilia(FAMILIA, AHORA);
    }

    @Test
    void revocar_sinCookie_noHaceNada() {
        servicio.revocar(null);

        verify(repositorio, never()).findByHash(anyString());
    }

    // ---------- ayudas ----------

    private SesionRefresco sesionVigente() {
        return sesion(AHORA.plus(Duration.ofDays(3)), AHORA.plus(Duration.ofDays(20)));
    }

    private SesionRefresco sesion(Instant expiraEn, Instant expiraAbsoluto) {
        SesionRefresco sesion = new SesionRefresco(usuario, SesionService.hash(VALOR), FAMILIA,
                AHORA.minus(Duration.ofDays(1)), expiraEn, expiraAbsoluto);
        sesion.setId(1L);
        return sesion;
    }

    private SesionRefresco capturarGuardada() {
        ArgumentCaptor<SesionRefresco> captor = ArgumentCaptor.forClass(SesionRefresco.class);
        verify(repositorio).save(captor.capture());
        return captor.getValue();
    }
}
