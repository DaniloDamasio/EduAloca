package com.edualoca;

import com.edualoca.api.seguranca.TokenService;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.usuario.AutenticacaoService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import com.edualoca.suporte.RelogioAjustavel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Seguranca do login (RNF-05, RF-01): sessao com expiracao por inatividade e por duracao maxima, e bloqueio por
 * tentativas contra forca bruta. Relogio ajustavel; o 429 pela API esta em {@link LoginBloqueioApiTest}.
 */
class SegurancaLoginTest {

    private static final Instant INICIO = Instant.parse("2027-01-10T12:00:00Z");

    @Nested
    @DisplayName("Expiracao do token")
    class Expiracao {

        private final RelogioAjustavel relogio = new RelogioAjustavel(INICIO, ZoneOffset.UTC);
        private final TokenService tokens = new TokenService(relogio, Duration.ofMinutes(30), Duration.ofHours(8));
        private final Usuario ana = new Usuario(UUID.randomUUID(), "ana@example.com", "hash", Perfil.SOLICITANTE);

        private int status(String token) {
            try {
                tokens.autenticar("Bearer " + token);
                return 200;
            } catch (RegraNegocioException e) {
                return e.getStatusHttp();
            }
        }

        @ParameterizedTest(name = "{0} min parado -> {1}")
        @CsvSource({"29, 200", "30, 401", "31, 401"})
        @DisplayName("Token parado vence ao completar 30 minutos sem uso (borda inclusiva)")
        void inatividade(long minutos, int esperado) {
            String token = tokens.emitir(ana);
            relogio.avancar(Duration.ofMinutes(minutos));

            assertEquals(esperado, status(token));
        }

        @Test
        @DisplayName("Cada uso renova o prazo de inatividade")
        void usoRenova() {
            String token = tokens.emitir(ana);
            for (int i = 0; i < 5; i++) {
                relogio.avancar(Duration.ofMinutes(20));
                assertEquals(200, status(token), "uso " + i);
            }
        }

        @Test
        @DisplayName("Mesmo em uso contínuo, o token vence em 8 horas contadas da emissão")
        void duracaoMaxima() {
            String token = tokens.emitir(ana);
            for (int i = 0; i < 23; i++) {
                relogio.avancar(Duration.ofMinutes(20));
                tokens.autenticar("Bearer " + token);
            }
            relogio.avancar(Duration.ofMinutes(20));

            RegraNegocioException ex = assertThrows(RegraNegocioException.class, () -> tokens.autenticar("Bearer " + token));
            assertAll(
                    () -> assertEquals(401, ex.getStatusHttp()),
                    () -> assertThat(ex.getMessage()).contains("expirada"));
        }

        @Test
        @DisplayName("Token vencido é descartado: continua 401 mesmo se o relógio voltar")
        void vencidoEDescartado() {
            String token = tokens.emitir(ana);
            relogio.avancar(Duration.ofMinutes(31));
            assertEquals(401, status(token));

            relogio.definir(INICIO);
            assertEquals(401, status(token));
        }

        @Test
        @DisplayName("Limites zerados ou negativos são recusados na criação")
        void limitesInvalidos() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new TokenService(relogio, Duration.ZERO, Duration.ofHours(1))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new TokenService(relogio, Duration.ofMinutes(1), Duration.ofHours(-1))));
        }
    }

    @Nested
    @DisplayName("Bloqueio por tentativas")
    class Bloqueio {

        private final RelogioAjustavel relogio = new RelogioAjustavel(INICIO, ZoneOffset.UTC);
        private final AutenticacaoService autenticacao = new AutenticacaoService(relogio);

        @BeforeEach
        void semear() {
            Banco.seedSeNecessario();
        }

        private int tentar(String email, String senha) {
            try {
                autenticacao.autenticar(email, senha);
                return 200;
            } catch (RegraNegocioException e) {
                return e.getStatusHttp();
            }
        }

        private void errar(String email, int vezes) {
            for (int i = 0; i < vezes; i++) {
                assertEquals(401, tentar(email, "errada"), "falha " + (i + 1));
            }
        }

        @Test
        @DisplayName("Quatro erros ainda permitem entrar, e o acerto zera a contagem")
        void quatroErrosEAcerto() {
            errar("usuario@example.com", 4);
            assertEquals(200, tentar("usuario@example.com", "senhaValida123"));

            errar("usuario@example.com", 4);
            assertEquals(200, tentar("usuario@example.com", "senhaValida123"));
        }

        @Test
        @DisplayName("No quinto erro o e-mail é bloqueado: até a senha certa recebe 429")
        void quintoErroBloqueia() {
            errar("usuario@example.com", 5);

            RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                    () -> autenticacao.autenticar("usuario@example.com", "senhaValida123"));
            assertAll(
                    () -> assertEquals(429, ex.getStatusHttp()),
                    () -> assertThat(ex.getMessage()).contains("Muitas tentativas"),
                    () -> assertEquals(200, tentar("admin@example.com", "senhaAdmin123"), "outro e-mail não é afetado"));
        }

        @Test
        @DisplayName("Depois de 15 minutos o bloqueio acaba e a senha certa volta a funcionar")
        void bloqueioExpira() {
            errar("usuario@example.com", 5);
            relogio.avancar(AutenticacaoService.BLOQUEIO.minusSeconds(1));
            assertEquals(429, tentar("usuario@example.com", "senhaValida123"));

            relogio.avancar(Duration.ofSeconds(1));
            assertEquals(200, tentar("usuario@example.com", "senhaValida123"));
        }

        @Test
        @DisplayName("Erros espaçados por mais de 15 minutos não se acumulam")
        void janelaReinicia() {
            errar("usuario@example.com", 4);
            relogio.avancar(AutenticacaoService.JANELA);
            errar("usuario@example.com", 4);

            assertEquals(200, tentar("usuario@example.com", "senhaValida123"));
        }

        @Test
        @DisplayName("E-mail inexistente bloqueia do mesmo jeito (não revela quais contas existem); maiúsculas contam igual")
        void inexistenteEMaiusculas() {
            errar("ninguem@example.com", 5);
            assertEquals(429, tentar("ninguem@example.com", "qualquer"));

            errar("Usuario@Example.com", 3);
            errar("usuario@example.com", 2);
            assertEquals(429, tentar("usuario@example.com", "senhaValida123"));
        }
    }
}
