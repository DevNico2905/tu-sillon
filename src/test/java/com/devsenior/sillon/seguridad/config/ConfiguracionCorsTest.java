package com.devsenior.sillon.seguridad.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

/** R39 y R38: qué deja ver CORS al front. */
class ConfiguracionCorsTest {

    private final CorsConfiguration config = new ConfiguracionCors()
            .corsConfigurationSource(new CorsProperties(List.of("https://sillon.nick-bern.com")))
            .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/salon"));

    @Test
    void exponeRetryAfterParaLaCuentaRegresivaDelFront() {
        assertThat(config.getExposedHeaders()).containsExactly("Retry-After");
    }

    @Test
    void permiteCredencialesSoloAlOrigenConfigurado() {
        assertThat(config.getAllowCredentials()).isTrue();
        assertThat(config.getAllowedOrigins()).containsExactly("https://sillon.nick-bern.com");
    }
}
