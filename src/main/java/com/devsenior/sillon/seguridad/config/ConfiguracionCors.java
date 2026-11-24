package com.devsenior.sillon.seguridad.config;

import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Qué orígenes del navegador pueden llamar a /api/** (el front de Next.js). */
@Configuration
public class ConfiguracionCors {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties propiedades) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(propiedades.origenesPermitidos());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Sin esto el navegador oculta Retry-After al front y el 429 no puede mostrar la cuenta regresiva (R38, R39)
        config.setExposedHeaders(List.of("Retry-After"));
        // Necesario para que el navegador envíe la cookie httpOnly del refresh token (B3)
        config.setAllowCredentials(true);
        // El navegador guarda la respuesta del preflight 1 hora: menos peticiones OPTIONS
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", config);
        return fuente;
    }
}