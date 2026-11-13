package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.perf.SeedUsuariosPerf;
import com.edualoca.suporte.BancoResetExtension;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login HTTP (RF-01): credencial valida gera token Bearer utilizavel; invalida responde 401 sem detalhe. */
@SpringBootTest
@ExtendWith(BancoResetExtension.class)
class LoginApiTest {

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc api;

    @BeforeEach
    void configurar() {
        api = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    @Test
    @DisplayName("Credencial valida retorna token que autentica as demais rotas")
    void credencialValida_retornaTokenUtilizavel() throws Exception {
        String token = login("usuario@example.com", "senhaValida123");

        api.perform(get("/salas/disponiveis").header("Authorization", "Bearer " + token)
                        .param("inicio", "2030-01-10T08:00").param("fim", "2030-01-10T09:00"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "senha ''{0}'' -> 401")
    @ValueSource(strings = {"senhaErrada", "SENHAVALIDA123"})
    @DisplayName("Senha incorreta responde 401 com mensagem generica")
    void senhaIncorreta_401(String senha) throws Exception {
        api.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"usuario@example.com\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Credenciais inválidas"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @DisplayName("E-mail inexistente responde o mesmo 401 (nao revela cadastro)")
    void emailInexistente_401() throws Exception {
        api.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ninguem@example.com\",\"senha\":\"qualquer\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Credenciais inválidas"));
    }

    @ParameterizedTest(name = "corpo {0} -> 400")
    @ValueSource(strings = {"{}", "{\"email\":\"usuario@example.com\"}", "{\"email\":\" \",\"senha\":\"x\"}", "null"})
    @DisplayName("Corpo sem e-mail ou senha responde 400 sem detalhe interno")
    void camposAusentes_400(String corpo) throws Exception {
        api.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    @DisplayName("Usuarios ficticios de carga nao existem fora do perfil perf")
    void usuarioDeCarga_foraDoPerfilPerf_401() throws Exception {
        api.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"perf.usuario01@edualoca.test\",\"senha\":\"Perf-Senha-01\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String email, String senha) throws Exception {
        String corpo = api.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(corpo, "$.token");
        assertThat(token).isNotBlank();
        return token;
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("perf")
    @DisplayName("Perfil perf")
    class PerfilPerf {

        @Autowired
        private SeedUsuariosPerf seed;

        @BeforeEach
        void semear() throws Exception {
            Banco.resetParaTeste();
            seed.run(null);
            api = MockMvcBuilders.webAppContextSetup(contexto).build();
        }

        @Test
        @DisplayName("Carrega os usuarios de perf/usuarios.csv e permite login")
        void carregaUsuariosDoCsv() throws Exception {
            assertThat(login("perf.usuario01@edualoca.test", "Perf-Senha-01")).isNotBlank();
            assertThat(login("perf.usuario50@edualoca.test", "Perf-Senha-50")).isNotBlank();
            assertThat(Banco.credenciais).doesNotContainKey("email");
        }
    }
}
