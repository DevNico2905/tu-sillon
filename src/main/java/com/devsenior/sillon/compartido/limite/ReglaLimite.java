package com.devsenior.sillon.compartido.limite;

import java.time.Duration;

/**
 * Todos los límites de peticiones de Sillón en un solo lugar.
 * Token bucket: "capacidad" fichas que se rellenan de forma continua a lo largo de "periodo".
 */
public enum ReglaLimite {

    LOGIN_IP(5, Duration.ofMinutes(1)),
    /** Frena ataques repartidos entre muchas IPs contra una misma cuenta. */
    LOGIN_CORREO(10, Duration.ofMinutes(15)),
    REGISTRO_IP(3, Duration.ofHours(1)),
    REFRESH_IP(30, Duration.ofMinutes(1)),
    CHAT_IP(10, Duration.ofMinutes(1)),
    /** Protege los créditos de IA del salón aunque el atacante rote IPs. */
    CHAT_SALON(100, Duration.ofHours(1)),
    RESERVA_IP(5, Duration.ofMinutes(10)),
    RESERVA_SALON(30, Duration.ofHours(1)),
    PUBLICO_IP(120, Duration.ofMinutes(1));

    private final int capacidad;
    private final Duration periodo;

    ReglaLimite(int capacidad, Duration periodo) {
        this.capacidad = capacidad;
        this.periodo = periodo;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public Duration getPeriodo() {
        return periodo;
    }
}
