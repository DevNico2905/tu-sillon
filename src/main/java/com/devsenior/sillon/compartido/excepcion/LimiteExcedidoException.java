package com.devsenior.sillon.compartido.excepcion;

import org.springframework.http.HttpStatus;

/** 429: se agotó el límite de peticiones. Lleva los segundos para el header Retry-After. */
public class LimiteExcedidoException extends ExcepcionApi {

    private final long segundosEspera;

    public LimiteExcedidoException(long segundosEspera) {
        super(HttpStatus.TOO_MANY_REQUESTS,
                "Demasiadas solicitudes. Intenta de nuevo en " + segundosEspera + " segundos.");
        this.segundosEspera = segundosEspera;
    }

    public long getSegundosEspera() {
        return segundosEspera;
    }
}
