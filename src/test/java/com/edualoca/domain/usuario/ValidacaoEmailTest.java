package com.edualoca.domain.usuario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Validacao de e-mail do cadastro de usuario, sem regex (Sonar java:S8786: sem backtracking super-linear). */
class ValidacaoEmailTest {

    @ParameterizedTest(name = "[{0}] -> {1}")
    @CsvSource(delimiter = '|', value = {
            "nome@example.com        | true",
            "a@b.c                   | true",
            "nova.resp@example.com   | true",
            "sem-arroba              | false",
            "@example.com            | false",
            "nome@                   | false",
            "nome@example            | false",
            "nome@.com               | false",
            "nome@example.           | false",
            "nome@@example.com       | false",
            "no me@example.com       | false",
            "nome@exa mple.com       | false",
    })
    @DisplayName("Um único @, parte local não vazia, domínio com ponto fora das pontas e sem espaços")
    void formatos(String email, boolean valido) {
        assertThat(UsuarioService.emailValido(email)).isEqualTo(valido);
    }

    @Test
    @Timeout(value = 200, unit = TimeUnit.MILLISECONDS)
    @DisplayName("Entrada longa e maliciosa é recusada em tempo linear")
    void entradaMaliciosaRapida() {
        String ataque = "a@" + "a.".repeat(50_000) + "@";
        assertThat(UsuarioService.emailValido(ataque)).isFalse();
    }
}
