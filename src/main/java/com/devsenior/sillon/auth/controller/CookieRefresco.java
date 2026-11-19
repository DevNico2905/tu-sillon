package com.devsenior.sillon.auth.controller;

import com.devsenior.sillon.auth.config.SesionProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Arma y borra la cookie del refresh token. Todos sus atributos viven solo aquí. */
@Component
public class CookieRefresco {

    public static final String NOMBRE = "sillon_refresh";
    /** Solo viaja a login, refresh y logout: nunca al resto de la API. */
    static final String RUTA = "/api/auth";

    private final SesionProperties propiedades;

    public CookieRefresco(SesionProperties propiedades) {
        this.propiedades = propiedades;
    }

    public ResponseCookie crear(String valor) {
        return base(valor).maxAge(Duration.ofDays(propiedades.diasInactividad())).build();
    }

    public ResponseCookie borrar() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOMBRE, valor)
                .httpOnly(true)
                .secure(propiedades.cookieSegura())
                .sameSite("Strict")
                .path(RUTA);
    }
}
