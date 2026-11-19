package com.devsenior.sillon.compartido.excepcion;

import org.springframework.http.HttpStatus;

/** 401: el refresh token falta, venció, fue revocado o se reutilizó. */
public class SesionInvalidaException extends ExcepcionApi {

    public SesionInvalidaException(String mensaje) {
        super(HttpStatus.UNAUTHORIZED, mensaje);
    }
}
