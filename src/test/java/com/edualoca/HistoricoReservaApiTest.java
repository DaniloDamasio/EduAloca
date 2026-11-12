package com.edualoca;

import com.edualoca.api.seguranca.TokenService;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /reservas/{id}/historico (RF-27, RF-29): so Responsavel e Administrador consultam; Solicitante recebe 403,
 * mesmo sendo o dono. Nao ha rota para alterar ou apagar registros do historico.
 */
@SpringBootTest
class HistoricoReservaApiTest {

    @Autowired
    private WebApplicationContext contexto;
    @Autowired
    private TokenService tokenService;

    private MockMvc api;
    private final Usuario solicitante = usuario(Perfil.SOLICITANTE);
    private final Usuario responsavel = usuario(Perfil.RESPONSAVEL);
    private final Usuario administrador = usuario(Perfil.ADMINISTRADOR);
    private String reservaId;

    @BeforeEach
    void criarReservaAprovada() throws Exception {
        api = MockMvcBuilders.webAppContextSetup(contexto).build();
        Sala lab = new Sala(UUID.randomUUID(), "Lab Quimica", "laboratorio", 20, "Bloco B", true);
        Banco.salas.put(lab.getId(), lab);
        String corpo = api.perform(post("/reservas").header("Authorization", bearer(solicitante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inicio\":\"2027-03-15T08:00\",\"fim\":\"2027-03-15T10:00\",\"finalidade\":\"Aula pratica\","
                                + "\"recursos\":[{\"tipo\":\"SALA\",\"id\":\"" + lab.getId() + "\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        reservaId = JsonPath.read(corpo, "$.id");
        api.perform(post("/reservas/{id}/aprovacao", reservaId).header("Authorization", bearer(responsavel)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} -> 200")
    @EnumSource(value = Perfil.class, names = {"RESPONSAVEL", "ADMINISTRADOR"})
    @DisplayName("Responsavel e Administrador recebem o historico com usuario, instante, estados e motivo")
    void perfisAutorizadosConsultam(Perfil perfil) throws Exception {
        api.perform(get("/reservas/{id}/historico", reservaId).header("Authorization", bearer(usuario(perfil))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].reservaId").value(reservaId))
                .andExpect(jsonPath("$[0].usuarioId").value(solicitante.getId().toString()))
                .andExpect(jsonPath("$[0].estadoAnterior").value(nullValue()))
                .andExpect(jsonPath("$[0].estadoNovo").value("SOLICITADA"))
                .andExpect(jsonPath("$[1].usuarioId").value(responsavel.getId().toString()))
                .andExpect(jsonPath("$[1].estadoAnterior").value("SOLICITADA"))
                .andExpect(jsonPath("$[1].estadoNovo").value("APROVADA"))
                .andExpect(jsonPath("$[1].motivo").value("Aprovação da solicitação"))
                .andExpect(jsonPath("$[1].instante").isString());
    }

    @Test
    @DisplayName("Solicitante dono da reserva recebe 403 sem detalhe interno e sem nenhum registro do historico")
    void solicitanteRecebe403() throws Exception {
        api.perform(get("/reservas/{id}/historico", reservaId).header("Authorization", bearer(solicitante)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").isString())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("APROVADA"))));
    }

    @Test
    @DisplayName("Sem token a consulta do historico e recusada com 401")
    void semToken401() throws Exception {
        api.perform(get("/reservas/{id}/historico", reservaId))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} no historico -> 405")
    @ValueSource(strings = {"PUT", "PATCH", "DELETE", "POST"})
    @DisplayName("Nenhum metodo de escrita e aceito sobre o historico, nem para o Administrador")
    void historicoNaoPodeSerAlteradoNemApagado(String metodo) throws Exception {
        api.perform(request(HttpMethod.valueOf(metodo), "/reservas/{id}/historico", reservaId)
                        .header("Authorization", bearer(administrador))
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isMethodNotAllowed());

        api.perform(get("/reservas/{id}/historico", reservaId).header("Authorization", bearer(administrador)))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.emitir(usuario);
    }

    /** O Responsavel e o competente pelo Bloco B, onde fica o laboratorio restrito (P-2, DEF-010). */
    private static Usuario usuario(Perfil perfil) {
        UUID id = perfil == Perfil.RESPONSAVEL ? Banco.RESPONSAVEL_BLOCO_B_ID : UUID.randomUUID();
        return new Usuario(id, perfil.name().toLowerCase() + "@edualoca.test", "h", perfil);
    }
}
