package com.devsenior.sillon.seguridad.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CorsPropertiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"*", "${CORS_ORIGENES}", "https://app.sillon.co/", "http://app.sillon.co", "https://*.sillon.co", "  "})
    void rechazaOrigenesInseguros(String origen) {
        assertThatThrownBy(() -> new CorsProperties(List.of(origen)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rechazaListaVaciaONula() {
        assertThatThrownBy(() -> new CorsProperties(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CorsProperties(List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aceptaOrigenesValidosYLimpiaEspacios() {
        CorsProperties props = new CorsProperties(List.of(" https://app.sillon.co ", "http://localhost:3000"));
        assertThat(props.origenesPermitidos()).containsExactly("https://app.sillon.co", "http://localhost:3000");
    }
}