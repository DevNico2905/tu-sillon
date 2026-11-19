package com.devsenior.sillon.seguridad.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devsenior.sillon.seguridad.config.JwtProperties;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtServiceTest {

    private static final Clock RELOJ = Clock.systemUTC();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "${JWT_SECRETO}", "${JWT_SECRETO_CON_UN_NOMBRE_MUY_LARGO_DE_MAS_DE_32}", "corto"})
    void rechazaSecretosFaltantesOInseguros(String secreto) {
        assertThatThrownBy(() -> new JwtService(new JwtProperties(secreto, 15), RELOJ))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aceptaUnSecretoDe32CaracteresOMas() {
        assertThatCode(() -> new JwtService(new JwtProperties("a".repeat(32), 15), RELOJ))
                .doesNotThrowAnyException();
    }
}