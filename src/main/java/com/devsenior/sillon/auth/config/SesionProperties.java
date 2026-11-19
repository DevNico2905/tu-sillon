package com.devsenior.sillon.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * sillon.sesion.*: vida del refresh token y atributos de su cookie.
 * Se valida al arrancar: una propiedad faltante llega como 0 y se rechaza.
 */
@ConfigurationProperties(prefix = "sillon.sesion")
public record SesionProperties(int diasInactividad, int diasMaximos, int segundosGracia, boolean cookieSegura) {

    public SesionProperties {
        if (diasInactividad <= 0 || diasMaximos < diasInactividad || segundosGracia < 0) {
            throw new IllegalStateException("Configuración de sesión inválida: revisa sillon.sesion.* "
                    + "(dias-inactividad > 0, dias-maximos >= dias-inactividad, segundos-gracia >= 0)");
        }
    }
}
