package com.devsenior.sillon.compartido.limite;

import com.devsenior.sillon.compartido.excepcion.LimiteExcedidoException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Token bucket en memoria (Bucket4j). Un balde por regla + clave (IP, correo o salón).
 * Caffeine acota la memoria: máximo 100.000 baldes, y cada uno expira tras 1 hora sin uso.
 * Límite consciente: cada instancia cuenta por separado y un reinicio borra los contadores.
 * Con varias instancias, el cambio es usar el backend de Redis de Bucket4j; las reglas no cambian.
 */
@Component
public class Limitador {

    private final Cache<String, Bucket> baldes = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(1))
            .build();
    private final TimeMeter tiempo;

    public Limitador() {
        this(TimeMeter.SYSTEM_MILLISECONDS);
    }

    /** Para los tests: un reloj que se puede adelantar a mano. */
    Limitador(TimeMeter tiempo) {
        this.tiempo = tiempo;
    }

    /** Gasta una ficha o lanza 429 con los segundos que faltan para la siguiente. */
    public void consumir(ReglaLimite regla, String clave) {
        Bucket balde = baldes.get(regla.name() + ':' + clave, llave -> crear(regla));
        ConsumptionProbe intento = balde.tryConsumeAndReturnRemaining(1);
        if (!intento.isConsumed()) {
            long segundos = TimeUnit.NANOSECONDS.toSeconds(intento.getNanosToWaitForRefill() + 999_999_999L);
            throw new LimiteExcedidoException(Math.max(1, segundos));
        }
    }

    private Bucket crear(ReglaLimite regla) {
        return Bucket.builder()
                .addLimit(limite -> limite
                        .capacity(regla.getCapacidad())
                        .refillGreedy(regla.getCapacidad(), regla.getPeriodo()))
                .withCustomTimePrecision(tiempo)
                .build();
    }
}
