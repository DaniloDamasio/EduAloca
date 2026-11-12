package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RF-04 / RF-05 / RF-06 pela API: {@code PUT /salas|professores|materiais/{id}} so para o Administrador. */
@SpringBootTest
class EdicaoCadastroApiTest {

    private static final String SOLICITANTE = "usuario@example.com:senhaValida123";
    private static final String RESPONSAVEL = "responsavel@example.com:senhaResponsavel123";
    private static final String ADMIN = "admin@example.com:senhaAdmin123";

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc api;

    @BeforeEach
    void preparar() {
        Banco.resetParaTeste();
        api = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    @Test
    @DisplayName("Sala: Administrador edita e a busca reflete; Solicitante e Responsavel 403; campo vazio 400; inexistente 404")
    void edicaoDeSala() throws Exception {
        String id = JsonPath.read(corpo(chamar(json(post("/salas"), "{\"nome\":\"Sala 1\",\"tipo\":\"aula\",\"capacidade\":20,"
                + "\"localizacao\":\"Bloco A\",\"restrito\":false}"), ADMIN).andExpect(status().isCreated())), "$.id");
        String edicao = "{\"nome\":\"Sala 1 ampliada\",\"tipo\":\"aula\",\"capacidade\":45,\"localizacao\":\"Bloco A\",\"restrito\":false}";

        chamar(json(put("/salas/{id}", id), edicao), SOLICITANTE).andExpect(status().isForbidden());
        chamar(json(put("/salas/{id}", id), edicao), RESPONSAVEL).andExpect(status().isForbidden());
        chamar(json(put("/salas/{id}", id), "{\"nome\":\"Sala 1\",\"tipo\":\" \",\"capacidade\":20,\"localizacao\":\"Bloco A\"}"), ADMIN)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("Tipo")));
        chamar(json(put("/salas/{id}", UUID.randomUUID()), edicao), ADMIN).andExpect(status().isNotFound());
        chamar(json(put("/salas/{id}", id), edicao), ADMIN)
                .andExpect(status().isOk()).andExpect(jsonPath("$.capacidade").value(45));

        chamar(get("/salas/disponiveis").param("inicio", "2027-12-01T08:00").param("fim", "2027-12-01T10:00")
                .param("capacidadeMinima", "40"), SOLICITANTE)
                .andExpect(jsonPath("$[*].nome", hasItem("Sala 1 ampliada")));
    }

    @Test
    @DisplayName("Professor e material: edicao reflete na busca por competencia e por tipo")
    void edicaoDeProfessorEMaterial() throws Exception {
        String prof = JsonPath.read(corpo(chamar(json(post("/professores"), "{\"nome\":\"Prof. Ana\","
                + "\"identificadorInstitucional\":\"ID-" + UUID.randomUUID() + "\",\"competencias\":[\"Fisica\"]}"), ADMIN)), "$.id");
        String mat = JsonPath.read(corpo(chamar(json(post("/materiais"), "{\"nome\":\"Kit\",\"tipo\":\"kit\",\"quantidadeTotal\":1}"), ADMIN)), "$.id");

        chamar(json(put("/professores/{id}", prof), "{\"nome\":\"Prof. Ana Lima\",\"competencias\":[\"Robotica\"]}"), ADMIN)
                .andExpect(status().isOk()).andExpect(jsonPath("$.competencias[0]").value("Robotica"));
        chamar(json(put("/materiais/{id}", mat), "{\"nome\":\"Kit grande\",\"tipo\":\"kit-maker\",\"quantidadeTotal\":3}"), ADMIN)
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantidadeTotal").value(3));

        chamar(get("/professores/disponiveis").param("competencia", "Robotica").param("inicio", "2027-12-01T08:00")
                .param("fim", "2027-12-01T10:00"), SOLICITANTE).andExpect(jsonPath("$[*].nome", hasItem("Prof. Ana Lima")));
        chamar(get("/materiais/disponiveis").param("tipo", "kit-maker").param("quantidade", "3").param("inicio", "2027-12-01T08:00")
                .param("fim", "2027-12-01T10:00"), SOLICITANTE).andExpect(jsonPath("$[*].nome", hasItem("Kit grande")));
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
