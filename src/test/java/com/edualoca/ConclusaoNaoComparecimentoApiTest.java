package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.suporte.RelogioAjustavel;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rotas {@code POST /reservas/{id}/conclusao} e {@code /nao-comparecimento} em caixa-preta, com o relogio do servidor
 * controlado pelo teste (a tolerancia de 15 min depende dele).
 */
@SpringBootTest
class ConclusaoNaoComparecimentoApiTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime INICIO = LocalDateTime.of(2027, 9, 6, 8, 0);

    private static final String SOLICITANTE = "usuario@example.com:senhaValida123";
    private static final String RESPONSAVEL_A = "responsavel@example.com:senhaResponsavel123";
    private static final String RESPONSAVEL_B = "responsavel.b@example.com:senhaResponsavelB123";
    private static final String ADMIN = "admin@example.com:senhaAdmin123";

    @TestBean
    private Clock clock;

    static Clock clock() {
        return new RelogioAjustavel(INICIO.minusDays(1).atZone(FUSO).toInstant(), FUSO);
    }

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc api;
    private String reserva;

    @BeforeEach
    void preparar() throws Exception {
        Banco.resetParaTeste();
        relogio(INICIO.minusDays(1));
        api = MockMvcBuilders.webAppContextSetup(contexto).build();
        String sala = JsonPath.read(corpo(chamar(json(post("/salas"), "{\"nome\":\"Sala 9\",\"tipo\":\"aula\","
                + "\"capacidade\":30,\"localizacao\":\"Bloco A\",\"restrito\":false}"), ADMIN).andExpect(status().isCreated())), "$.id");
        reserva = JsonPath.read(corpo(chamar(json(post("/reservas"), "{\"inicio\":\"" + INICIO + "\",\"fim\":\""
                + INICIO.plusHours(2) + "\",\"finalidade\":\"Aula\",\"recursos\":[{\"tipo\":\"SALA\",\"id\":\"" + sala + "\"}]}"),
                SOLICITANTE).andExpect(jsonPath("$.estado").value("APROVADA"))), "$.id");
    }

    @Test
    @DisplayName("Conclusao: 403 para Solicitante e outro bloco; 409 antes do inicio do uso; 200 e historico EM_USO -> CONCLUIDA")
    void conclusao() throws Exception {
        chamar(post("/reservas/{id}/conclusao", reserva), RESPONSAVEL_A).andExpect(status().isConflict());
        chamar(post("/reservas/{id}/inicio-uso", reserva), RESPONSAVEL_A).andExpect(status().isOk());

        chamar(post("/reservas/{id}/conclusao", reserva), SOLICITANTE).andExpect(status().isForbidden());
        chamar(post("/reservas/{id}/conclusao", reserva), RESPONSAVEL_B).andExpect(status().isForbidden());
        chamar(post("/reservas/{id}/conclusao", reserva), RESPONSAVEL_A)
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONCLUIDA"));
        chamar(get("/reservas/{id}/historico", reserva), ADMIN)
                .andExpect(jsonPath("$[-1].estadoAnterior").value("EM_USO"))
                .andExpect(jsonPath("$[-1].estadoNovo").value("CONCLUIDA"));
    }

    @Test
    @DisplayName("Nao comparecimento: 409 em ProblemDetail ate inicio + 14 min; 200 em inicio + 15 min; Solicitante 403")
    void naoComparecimento() throws Exception {
        relogio(INICIO.plusMinutes(14));
        chamar(post("/reservas/{id}/nao-comparecimento", reserva), RESPONSAVEL_A)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("15 minutos")));

        relogio(INICIO.plusMinutes(15));
        chamar(post("/reservas/{id}/nao-comparecimento", reserva), SOLICITANTE).andExpect(status().isForbidden());
        chamar(post("/reservas/{id}/nao-comparecimento", reserva), ADMIN)
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("NAO_COMPARECEU"));
        chamar(post("/reservas/{id}/inicio-uso", reserva), ADMIN).andExpect(status().isConflict());
    }

    private void relogio(LocalDateTime instante) {
        ((RelogioAjustavel) clock).definir(instante.atZone(FUSO).toInstant());
    }

    private String login(String conta) throws Exception {
        String[] partes = conta.split(":");
        return JsonPath.read(corpo(api.perform(json(post("/auth/login"),
                "{\"email\":\"" + partes[0] + "\",\"senha\":\"" + partes[1] + "\"}")).andExpect(status().isOk())), "$.token");
    }

    private ResultActions chamar(MockHttpServletRequestBuilder requisicao, String conta) throws Exception {
        return api.perform(requisicao.header("Authorization", "Bearer " + login(conta)));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder requisicao, String corpo) {
        return requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private static String corpo(ResultActions resultado) throws Exception {
        return resultado.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
