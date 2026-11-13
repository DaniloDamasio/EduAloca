package com.edualoca;

import com.edualoca.domain.usuario.AutenticacaoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bloqueio por tentativas pela API: 429 em ProblemDetail, sem token e sem detalhe interno (regras em SegurancaLoginTest). */
@SpringBootTest
class LoginBloqueioApiTest {

    @Autowired
    private WebApplicationContext contexto;

    @Test
    @DisplayName("Cinco senhas erradas e o login responde 429, inclusive com a senha certa")
    void loginResponde429() throws Exception {
        MockMvc api = MockMvcBuilders.webAppContextSetup(contexto).build();
        String errada = "{\"email\":\"novo@example.com\",\"senha\":\"errada\"}";
        for (int i = 0; i < AutenticacaoService.MAX_FALHAS; i++) {
            api.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(errada))
                    .andExpect(status().isUnauthorized());
        }

        api.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"novo@example.com\",\"senha\":\"minhaSenha123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Muitas tentativas"))
                .andExpect(jsonPath("$.detail").value(containsString("aguarde")))
                .andExpect(jsonPath("$.token").doesNotExist());
    }
}
