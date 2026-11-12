package com.edualoca;

import com.edualoca.api.seguranca.TokenService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Borda HTTP fora do texto dos cenarios BDD: autenticacao obrigatoria (RF-01/RF-02), entrada malformada
 * (RNF-04) e erros sem detalhe interno (RF-38, RNF-06).
 */
@SpringBootTest
class ApiSegurancaTest {

    @Autowired
    private WebApplicationContext contexto;
    @Autowired
    private TokenService tokenService;

    private MockMvc api;
    private String tokenSolicitante;

    @BeforeEach
    void configurar() {
        api = MockMvcBuilders.webAppContextSetup(contexto).build();
        tokenSolicitante = tokenService.emitir(new Usuario(UUID.randomUUID(), "sol@edualoca.test", "h", Perfil.SOLICITANTE));
    }

    @ParameterizedTest(name = "Authorization ''{0}'' -> 401")
    @ValueSource(strings = {"", "Basic dXN1YXJpbzpzZW5oYQ==", "Bearer token-que-nunca-foi-emitido", "Bearer "})
    @DisplayName("Requisicao sem token valido e recusada com 401 sem detalhe interno")
    void semTokenValido_recusaCom401(String cabecalho) throws Exception {
        var requisicao = get("/reservas/{id}", UUID.randomUUID());
        if (!cabecalho.isEmpty()) {
            requisicao.header("Authorization", cabecalho);
        }
        api.perform(requisicao)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").isString())
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    @DisplayName("Solicitante nao cadastra manutencao: 403 (RF-02, RF-21)")
    void solicitanteNaoCadastraManutencao() throws Exception {
        api.perform(post("/manutencoes").header("Authorization", "Bearer " + tokenSolicitante)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoRecurso\":\"SALA\",\"recursoId\":\"" + UUID.randomUUID()
                                + "\",\"inicio\":\"2026-03-10T08:00\",\"fim\":\"2026-03-10T18:00\",\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "corpo {0} -> 400")
    @ValueSource(strings = {
            "{nao-e-json",
            "{\"inicio\":\"10/03/2026\",\"fim\":\"2026-03-10T12:00\",\"finalidade\":\"Aula\",\"recursos\":[]}",
            "{\"inicio\":\"2026-03-10T10:00\",\"fim\":\"2026-03-10T12:00\",\"finalidade\":\"Aula\",\"recursos\":[{\"tipo\":\"NAVE\",\"id\":\"00000000-0000-0000-0000-000000000009\"}]}",
            "{\"inicio\":\"2026-03-10T10:00\",\"fim\":\"2026-03-10T12:00\",\"finalidade\":\"Aula\",\"recursos\":[{\"tipo\":\"SALA\",\"id\":\"00000000-0000-0000-0000-000000000009\"}]}",
            "{\"inicio\":\"2026-03-10T10:00\",\"fim\":\"2026-03-10T12:00\",\"finalidade\":\"Aula\",\"recursos\":[{\"tipo\":\"SALA\"}]}",
            "{\"inicio\":\"2026-03-10T10:00\",\"fim\":\"2026-03-10T12:00\",\"finalidade\":\"Aula\",\"recursos\":[null]}",
            "{\"inicio\":\"2026-03-10T10:00\",\"fim\":\"2026-03-10T12:00\",\"finalidade\":\"Aula\",\"recursos\":[{\"tipo\":\"SALA\",\"id\":\"00000000-0000-0000-0000-000000000001\",\"quantidade\":0}]}",
            "{\"inicio\":\"2026-03-10T10:00\",\"fim\":\"2026-03-10T12:00\",\"finalidade\":\"Aula\"}"
    })
    @DisplayName("Criacao de reserva com entrada malformada ou invalida e recusada com 400 (RNF-04)")
    void entradaInvalida_recusaCom400(String corpo) throws Exception {
        api.perform(post("/reservas").header("Authorization", "Bearer " + tokenSolicitante)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").isString())
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    @DisplayName("Busca sem parametro obrigatorio responde 400")
    void buscaSemParametro_recusaCom400() throws Exception {
        api.perform(get("/salas/disponiveis").header("Authorization", "Bearer " + tokenSolicitante))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rota inexistente e metodo nao suportado mantem 404/405 com mensagem generica")
    void errosDoFramework_mantemStatusSemDetalheInterno() throws Exception {
        api.perform(get("/nao-existe").header("Authorization", "Bearer " + tokenSolicitante))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Endereço da API não encontrado"));
        api.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/manutencoes"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.mensagem").value("Operação não permitida para este endereço"));
    }
}
