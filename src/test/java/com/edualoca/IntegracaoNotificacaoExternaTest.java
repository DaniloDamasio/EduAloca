package com.edualoca;

import com.edualoca.domain.notificacao.NotificacaoHttpAdapter;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Integracao com provedor de notificacao externa via WireMock (RF-31, RF-32, RF-33, RNF-16)")
class IntegracaoNotificacaoExternaTest {

    private static final String CHAVE_FAKE = "chave-de-teste-nao-usar-em-producao";

    private WireMockServer wireMockServer;

    @BeforeEach
    void subirWireMock() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterEach
    void pararWireMock() {
        wireMockServer.stop();
    }

    private NotificacaoHttpAdapter adapterComTimeouts(Duration timeoutConexao, Duration timeoutLeitura) {
        return new NotificacaoHttpAdapter("http://localhost:" + wireMockServer.port(), CHAVE_FAKE,
                timeoutConexao, timeoutLeitura);
    }

    private NotificacaoHttpAdapter adapterPadrao() {
        return adapterComTimeouts(Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    private Reserva reservaDeExemplo() {
        Sala sala = new Sala(UUID.randomUUID(), "Sala X", "aula", 30, "Bloco A", true);
        return new Reserva(UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.of(2026, 3, 10, 10, 0), LocalDateTime.of(2026, 3, 10, 12, 0),
                "Aula especial", List.of(new ReservaRecurso(sala, 1)), EstadoReserva.APROVADA);
    }

    @Test
    @DisplayName("INT-01: resposta 200 valida deve retornar sucesso e emitir o request esperado")
    void int01_respostaValida_deveRetornarSucessoEEmitirRequestEsperado() {
        wireMockServer.stubFor(post(urlEqualTo("/notificacoes"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"recebido\"}")));

        Reserva reserva = reservaDeExemplo();
        boolean resultado = adapterPadrao().enviar(reserva);

        assertTrue(resultado);
        wireMockServer.verify(postRequestedFor(urlEqualTo("/notificacoes"))
                .withHeader("Authorization", equalTo("Bearer " + CHAVE_FAKE))
                .withRequestBody(matchingJsonPath("$.reservaId", equalTo(reserva.getId().toString()))));
    }

    @Test
    @DisplayName("INT-02: resposta 404 e um erro permanente, nao deve haver retry")
    void int02_resposta404_naoDeveReenviar() {
        wireMockServer.stubFor(post(urlEqualTo("/notificacoes")).willReturn(aResponse().withStatus(404)));

        boolean resultado = adapterPadrao().enviar(reservaDeExemplo());

        assertFalse(resultado);
        wireMockServer.verify(exactly(1), postRequestedFor(urlEqualTo("/notificacoes")));
    }

    @Test
    @DisplayName("INT-03: resposta 500 e um erro transitorio, deve haver uma tentativa extra antes de falhar")
    void int03_resposta500_deveReenviarUmaVezAntesDeFalhar() {
        wireMockServer.stubFor(post(urlEqualTo("/notificacoes")).willReturn(aResponse().withStatus(500)));

        boolean resultado = adapterPadrao().enviar(reservaDeExemplo());

        assertFalse(resultado);
        wireMockServer.verify(exactly(2), postRequestedFor(urlEqualTo("/notificacoes")));
    }

    @Test
    @DisplayName("INT-04: timeout do provedor deve ser interrompido sem lancar excecao para o chamador")
    void int04_timeout_deveInterromperSemLancarExcecao() {
        wireMockServer.stubFor(post(urlEqualTo("/notificacoes"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(500)));

        boolean resultado = adapterComTimeouts(Duration.ofMillis(200), Duration.ofMillis(100))
                .enviar(reservaDeExemplo());

        assertFalse(resultado);
    }

    @Test
    @DisplayName("INT-05: corpo de resposta invalido/malformado em um 200 nao deve lancar excecao")
    void int05_corpoInvalido_naoDeveLancarExcecao() {
        wireMockServer.stubFor(post(urlEqualTo("/notificacoes"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{isto nao e um json valido")));

        boolean resultado = adapterPadrao().enviar(reservaDeExemplo());

        assertTrue(resultado);
    }

    @Test
    @DisplayName("Seguranca: token do provedor vazado no corpo de erro nao deve atravessar para log nem retorno da aplicacao")
    void seguranca_tokenVazadoNoCorpoDeErro_naoDeveAtravessarParaLogNemRetorno() {
        String segredoDoProvedor = "token-interno-do-provedor-vazado";
        wireMockServer.stubFor(post(urlEqualTo("/notificacoes"))
                .willReturn(aResponse().withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"internal\",\"debugToken\":\"" + segredoDoProvedor + "\"}")));

        Logger loggerDoAdapter = Logger.getLogger(NotificacaoHttpAdapter.class.getName());
        List<String> mensagensCapturadas = new ArrayList<>();
        Handler capturador = new Handler() {
            @Override
            public void publish(LogRecord record) {
                mensagensCapturadas.add(record.getMessage());
                for (Object parametro : record.getParameters() == null ? new Object[0] : record.getParameters()) {
                    mensagensCapturadas.add(String.valueOf(parametro));
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        loggerDoAdapter.addHandler(capturador);

        boolean resultado;
        try {
            resultado = adapterPadrao().enviar(reservaDeExemplo());
        } finally {
            loggerDoAdapter.removeHandler(capturador);
        }

        assertFalse(resultado);
        wireMockServer.verify(postRequestedFor(urlEqualTo("/notificacoes"))
                .withHeader("Authorization", notMatching(".*" + segredoDoProvedor + ".*")));
        assertTrue(mensagensCapturadas.stream().noneMatch(mensagem -> mensagem.contains(segredoDoProvedor)));
    }
}
