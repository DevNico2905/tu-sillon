package com.devsenior.sillon.compartido.limite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.devsenior.sillon.compartido.excepcion.LimiteExcedidoException;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LimitadorTest {

    /** Reloj manual: el tiempo solo avanza cuando el test lo dice. */
    private static final class RelojManual implements TimeMeter {
        private long nanos;

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }

        void avanzar(Duration duracion) {
            nanos += duracion.toNanos();
        }
    }

    private RelojManual reloj;
    private Limitador limitador;

    @BeforeEach
    void setUp() {
        reloj = new RelojManual();
        limitador = new Limitador(reloj);
    }

    @Test
    void permiteHastaLaCapacidadYRechazaLaSiguiente() {
        for (int i = 0; i < 5; i++) {
            limitador.consumir(ReglaLimite.LOGIN_IP, "1.1.1.1");
        }
        assertThrows(LimiteExcedidoException.class, () -> limitador.consumir(ReglaLimite.LOGIN_IP, "1.1.1.1"));
    }

    @Test
    void retryAfterEsLoQueFaltaParaLaSiguienteFicha() {
        agotar(ReglaLimite.LOGIN_IP, "1.1.1.1");

        // 5 fichas por minuto: se recupera una cada 12 segundos
        LimiteExcedidoException ex = assertThrows(LimiteExcedidoException.class,
                () -> limitador.consumir(ReglaLimite.LOGIN_IP, "1.1.1.1"));
        assertEquals(12, ex.getSegundosEspera());
        assertEquals(429, ex.getEstado().value());
    }

    @Test
    void lasFichasSeRecuperanConElTiempo() {
        agotar(ReglaLimite.LOGIN_IP, "1.1.1.1");

        reloj.avanzar(Duration.ofSeconds(12));

        assertDoesNotThrow(() -> limitador.consumir(ReglaLimite.LOGIN_IP, "1.1.1.1"));
        assertThrows(LimiteExcedidoException.class, () -> limitador.consumir(ReglaLimite.LOGIN_IP, "1.1.1.1"));
    }

    @Test
    void clavesDistintasNoSeAfectan() {
        agotar(ReglaLimite.LOGIN_IP, "1.1.1.1");

        assertDoesNotThrow(() -> limitador.consumir(ReglaLimite.LOGIN_IP, "2.2.2.2"));
    }

    @Test
    void reglasDistintasConLaMismaClaveNoSeAfectan() {
        agotar(ReglaLimite.LOGIN_IP, "1.1.1.1");

        assertDoesNotThrow(() -> limitador.consumir(ReglaLimite.REFRESH_IP, "1.1.1.1"));
    }

    private void agotar(ReglaLimite regla, String clave) {
        for (int i = 0; i < regla.getCapacidad(); i++) {
            limitador.consumir(regla, clave);
        }
    }
}
