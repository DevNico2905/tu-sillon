package com.devsenior.sillon.seguridad.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * sillon.cors.origenes-permitidos (variable CORS_ORIGENES), separados por coma.
 * Se valida al arrancar: sin comodines, sin barra final y sin placeholders sin resolver.
 */
@ConfigurationProperties(prefix = "sillon.cors")
public record CorsProperties(List<String> origenesPermitidos) {

    public CorsProperties {
        List<String> limpios = origenesPermitidos == null ? List.of()
                : origenesPermitidos.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
        if (limpios.isEmpty()) {
            throw new IllegalStateException("Falta CORS_ORIGENES (ej: https://app.midominio.com)");
        }
        for (String origen : limpios) {
            if (origen.contains("${") || origen.contains("*") || origen.endsWith("/")
                    || !(origen.startsWith("https://") || origen.startsWith("http://localhost"))) {
                throw new IllegalStateException("Origen CORS inválido: '" + origen
                        + "'. Usa el origen exacto, sin comodines ni barra final (ej: https://app.midominio.com)");
            }
        }
        origenesPermitidos = limpios;
    }
}