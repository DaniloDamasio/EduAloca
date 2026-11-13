package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF-11 do enunciado com integracao habilitada: a aplicacao montada pelo Spring, com
 * {@code edualoca.notificacao.externa.habilitada=true}, chama o provedor (WireMock) quando uma reserva muda de estado
 * pela API. Provedor fora do ar (500) nao desfaz a aprovacao (RF-33).
 */
@SpringBootTest
class NotificacaoExternaIntegradaApiTest {

    private static final String CHAVE = "chave-de-teste-nao-usar-em-producao";
    private static final WireMockServer PROVEDOR = new WireMockServer(0);

    static {
        PROVEDOR.start();
    }

    @DynamicPropertySource
    static void apontarParaOProvedor(DynamicPropertyRegistry registro) {
        registro.add("edualoca.notificacao.externa.habilitada", () -> "true");
        registro.add("edualoca.notificacao.externa.url", () -> "http://localhost:" + PROVEDOR.port());
        registro.add("edualoca.notificacao.externa.api-key", () -> CHAVE);
    }

    @AfterAll
    static void pararProvedor() {
        PROVEDOR.stop();
    }

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc api;

    @BeforeEach
    void preparar() {
        Banco.resetParaTeste();
        PROVEDOR.resetAll();
        api = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    @Test
    @DisplayName("Aprovacao pela API envia POST /notificacoes ao provedor externo com a chave e o estado APROVADA")
    void aprovacaoPelaApi_chamaOProvedor() throws Exception {
        PROVEDOR.stubFor(post(urlEqualTo("/notificacoes")).willReturn(aResponse().withStatus(200)));
        String reservaId = reservaRestritaCriadaPelaApi();

        chamar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/reservas/" + reservaId + "/aprovacao"),
                "responsavel@example.com", "senhaResponsavel123", null, 200);

        PROVEDOR.verify(1, postRequestedFor(urlEqualTo("/notificacoes"))
                .withHeader("Authorization", equalTo("Bearer " + CHAVE))
                .withRequestBody(containing(reservaId))
                .withRequestBody(containing("APROVADA")));
    }

    @Test
    @DisplayName("Provedor com 500 nao desfaz a aprovacao feita pela API (RF-33)")
    void provedorFora_naoDesfazAprovacao() throws Exception {
        PROVEDOR.stubFor(post(urlEqualTo("/notificacoes")).willReturn(aResponse().withStatus(500)));
        String reservaId = reservaRestritaCriadaPelaApi();

        chamar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/reservas/" + reservaId + "/aprovacao"),
                "responsavel@example.com", "senhaResponsavel123", null, 200);

        api.perform(get("/reservas/" + reservaId).header("Authorization", "Bearer " + login("responsavel@example.com", "senhaResponsavel123")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROVADA"));
    }

    private String reservaRestritaCriadaPelaApi() throws Exception {
        String salaId = JsonPath.read(chamar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/salas"),
                "admin@example.com", "senhaAdmin123",
                "{\"nome\":\"Lab Notificacao\",\"tipo\":\"laboratorio\",\"capacidade\":20,\"localizacao\":\"Bloco A\",\"restrito\":true}",
                201), "$.id");
        return JsonPath.read(chamar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/reservas"),
                "usuario@example.com", "senhaValida123",
                "{\"inicio\":\"2027-08-02T08:00\",\"fim\":\"2027-08-02T10:00\",\"finalidade\":\"Aula\","
                        + "\"recursos\":[{\"tipo\":\"SALA\",\"id\":\"" + salaId + "\"}]}", 201), "$.id");
    }

    private String login(String email, String senha) throws Exception {
        MvcResult r = api.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
    }

    private String chamar(MockHttpServletRequestBuilder requisicao, String email, String senha, String corpo,
                          int statusEsperado) throws Exception {
        requisicao.header("Authorization", "Bearer " + login(email, senha));
        if (corpo != null) {
            requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return api.perform(requisicao).andExpect(status().is(statusEsperado)).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
