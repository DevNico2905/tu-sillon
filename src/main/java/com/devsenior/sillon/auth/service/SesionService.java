package com.devsenior.sillon.auth.service;

import com.devsenior.sillon.auth.config.SesionProperties;
import com.devsenior.sillon.auth.model.SesionRefresco;
import com.devsenior.sillon.auth.repository.SesionRefrescoRepository;
import com.devsenior.sillon.compartido.excepcion.SesionInvalidaException;
import com.devsenior.sillon.usuario.model.Usuario;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens con rotación y detección de reuso.
 * Cada uso marca el token como usado y emite otro de la misma familia.
 * Presentar un token ya usado (fuera de la ventana de gracia) revoca toda la familia: señal de robo.
 */
@Service
public class SesionService {

    private static final Logger log = LoggerFactory.getLogger(SesionService.class);
    private static final int BYTES_TOKEN = 32;
    private static final String MENSAJE = "La sesión expiró o no es válida. Inicia sesión de nuevo.";

    private final SecureRandom aleatorio = new SecureRandom();
    private final SesionRefrescoRepository repositorio;
    private final SesionProperties propiedades;
    private final Clock clock;

    public SesionService(SesionRefrescoRepository repositorio, SesionProperties propiedades, Clock clock) {
        this.repositorio = repositorio;
        this.propiedades = propiedades;
        this.clock = clock;
    }

    /** Resultado de rotar: el dueño de la sesión y el nuevo valor para la cookie. */
    public record Rotacion(Usuario usuario, String nuevoValor) {
    }

    /** Inicia una familia nueva (login o registro). Devuelve el valor en claro: solo viaja en la cookie. */
    @Transactional
    public String emitir(Usuario usuario) {
        Instant ahora = clock.instant();
        Instant expiraAbsoluto = ahora.plus(Duration.ofDays(propiedades.diasMaximos()));
        return guardarNueva(usuario, UUID.randomUUID().toString(), ahora, expiraAbsoluto);
    }

    /**
     * noRollbackFor: si se detecta reuso, la revocación de la familia debe quedar guardada
     * aunque el método termine lanzando 401.
     */
    @Transactional(noRollbackFor = SesionInvalidaException.class)
    public Rotacion rotar(String valor) {
        SesionRefresco actual = buscar(valor);
        Instant ahora = clock.instant();

        if (actual.getRevocadoEn() != null) {
            throw invalida();
        }
        if (actual.getUsadoEn() != null) {
            throw reuso(actual, ahora);
        }
        if (!ahora.isBefore(actual.getExpiraEn()) || !ahora.isBefore(actual.getExpiraAbsoluto())) {
            throw invalida();
        }
        Usuario usuario = actual.getUsuario();
        if (!usuario.isActivo()) {
            repositorio.revocarFamilia(actual.getFamilia(), ahora);
            throw invalida();
        }
        if (repositorio.marcarUsada(actual.getId(), ahora) == 0) {
            // Otra petición (otra pestaña) lo usó en este mismo instante: ella se queda con el nuevo
            throw invalida();
        }
        String nuevo = guardarNueva(usuario, actual.getFamilia(), ahora, actual.getExpiraAbsoluto());
        return new Rotacion(usuario, nuevo);
    }

    /** Logout del dispositivo actual. Idempotente: sin cookie o con token desconocido no hace nada. */
    @Transactional
    public void revocar(String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        repositorio.findByHash(hash(valor))
                .ifPresent(sesion -> repositorio.revocarFamilia(sesion.getFamilia(), clock.instant()));
    }

    /** Todos los días a las 3:30 a. m. (Colombia) borra las sesiones vencidas hace más de 7 días. */
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Bogota")
    @Transactional
    public void limpiarVencidas() {
        int borradas = repositorio.borrarVencidas(clock.instant().minus(Duration.ofDays(7)));
        if (borradas > 0) {
            log.info("Limpieza de sesiones: {} borradas", borradas);
        }
    }

    /** SHA-256 en hexadecimal. Público y estático para que los tests verifiquen qué se guarda. */
    public static String hash(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private SesionRefresco buscar(String valor) {
        if (valor == null || valor.isBlank()) {
            throw invalida();
        }
        return repositorio.findByHash(hash(valor)).orElseThrow(SesionService::invalida);
    }

    private SesionInvalidaException reuso(SesionRefresco actual, Instant ahora) {
        Instant finGracia = actual.getUsadoEn().plusSeconds(propiedades.segundosGracia());
        if (ahora.isBefore(finGracia)) {
            // Dos pestañas refrescando a la vez: no es robo, la otra ya tiene la cookie nueva
            return invalida();
        }
        log.warn("Reuso de refresh token: se revoca la familia {} del usuario {}",
                actual.getFamilia(), actual.getUsuario().getId());
        repositorio.revocarFamilia(actual.getFamilia(), ahora);
        return invalida();
    }

    private String guardarNueva(Usuario usuario, String familia, Instant ahora, Instant expiraAbsoluto) {
        String valor = generar();
        Instant porInactividad = ahora.plus(Duration.ofDays(propiedades.diasInactividad()));
        Instant expiraEn = porInactividad.isBefore(expiraAbsoluto) ? porInactividad : expiraAbsoluto;
        repositorio.save(new SesionRefresco(usuario, hash(valor), familia, ahora, expiraEn, expiraAbsoluto));
        return valor;
    }

    private String generar() {
        byte[] bytes = new byte[BYTES_TOKEN];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static SesionInvalidaException invalida() {
        return new SesionInvalidaException(MENSAJE);
    }
}
