package com.edualoca;

import com.edualoca.api.CabecalhosSegurancaFiltro;
import com.edualoca.domain.comum.Banco;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rotas que a interface web usa para simular os tres perfis: listagem de reservas por perfil, catalogo e cadastro do
 * Administrador, busca de professor e material, inicio de uso, retirada/devolucao, logout e cabecalhos de seguranca.
 * Todo token sai de {@code POST /auth/login} com as contas do seed; toda massa e criada pela propria API.
 */
@SpringBootTest
class InterfacePerfisApiTest {

    private static final String SOLICITANTE = "usuario@example.com:senhaValida123";
    private static final String OUTRO_SOLICITANTE = "novo@example.com:minhaSenha123";
    private static final String RESPONSAVEL_A = "responsavel@example.com:senhaResponsavel123";
    private static final String RESPONSAVEL_B = "responsavel.b@example.com:senhaResponsavelB123";
    private static final String ADMIN = "admin@example.com:senhaAdmin123";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private CabecalhosSegurancaFiltro filtroCabecalhos;

    private MockMvc api;

    @BeforeEach
    void preparar() {
        api = MockMvcBuilders.webAppContextSetup(contexto).addFilters(filtroCabecalhos).build();
        Banco.seedSeNecessario();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Listagem de reservas por perfil
    // ---------------------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /reservas")
    class Listagem {

        @Test
        @DisplayName("cada perfil ve so as reservas que lhe cabem")
        void filtraPorPerfilEPorCompetencia() throws Exception {
            String salaA = cadastrarSala("Lab A", "Bloco A", true);
            String salaB = cadastrarSala("Lab B", "Bloco B", true);
            String doDono = criarReserva(SOLICITANTE, salaA, "SALA", "2027-05-03T08:00", "2027-05-03T10:00");
            String doOutro = criarReserva(OUTRO_SOLICITANTE, salaB, "SALA", "2027-05-04T08:00", "2027-05-04T10:00");

            assertThat(idsListados(SOLICITANTE)).containsExactly(doDono);
            assertThat(idsListados(OUTRO_SOLICITANTE)).containsExactly(doOutro);
            assertThat(idsListados(RESPONSAVEL_A)).containsExactly(doDono);
            assertThat(idsListados(RESPONSAVEL_B)).containsExactly(doOutro);
            // Administrador ve todas, em ordem de inicio.
            assertThat(idsListados(ADMIN)).containsExactly(doDono, doOutro);
        }

        @Test
        @DisplayName("a reserva listada traz os recursos com nome, tipo e quantidade")
        void respostaTrazRecursos() throws Exception {
            String sala = cadastrarSala("Auditorio", "Bloco A", false);
            criarReserva(SOLICITANTE, sala, "SALA", "2027-05-05T08:00", "2027-05-05T10:00");

            chamar(get("/reservas"), SOLICITANTE)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].recursos[0].tipo").value("SALA"))
                    .andExpect(jsonPath("$[0].recursos[0].id").value(sala))
                    .andExpect(jsonPath("$[0].recursos[0].nome").value("Auditorio"))
                    .andExpect(jsonPath("$[0].recursos[0].quantidade").value(1));
        }

        @Test
        @DisplayName("sem token responde 401")
        void semToken() throws Exception {
            api.perform(get("/reservas")).andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Catalogo e cadastro do Administrador
    // ---------------------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Catalogo e cadastro")
    class Cadastro {

        @Test
        @DisplayName("Administrador cadastra sala, professor e material, e todos aparecem no catalogo")
        void cadastroApareceNoCatalogo() throws Exception {
            String sala = cadastrarSala("Lab Quimica", "Bloco B", true);
            String professor = JsonPath.read(corpo(chamar(json(post("/professores"),
                    "{\"nome\":\"Prof. Ana\",\"identificadorInstitucional\":\"P-77\",\"competencias\":[\"Quimica\",\" Fisica \"]}"),
                    ADMIN).andExpect(status().isCreated())), "$.id");
            String material = JsonPath.read(corpo(chamar(json(post("/materiais"),
                    "{\"nome\":\"Kit Arduino\",\"tipo\":\"kit\",\"quantidadeTotal\":5,\"restrito\":true}"), ADMIN)
                    .andExpect(status().isCreated())), "$.id");

            chamar(get("/recursos"), SOLICITANTE)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.salas[?(@.id=='" + sala + "')].restrito").value(hasItem(true)))
                    .andExpect(jsonPath("$.salas[?(@.id=='" + sala + "')].localizacao").value(hasItem("Bloco B")))
                    .andExpect(jsonPath("$.professores[?(@.id=='" + professor + "')].competencias[*]")
                            .value(org.hamcrest.Matchers.contains("Quimica", "Fisica")))
                    .andExpect(jsonPath("$.materiais[?(@.id=='" + material + "')].quantidadeTotal").value(hasItem(5)));
        }

        @Test
        @DisplayName("sala restrita cadastrada pela API faz a reserva nascer SOLICITADA (fluxo de aprovacao)")
        void salaRestritaExigeAprovacao() throws Exception {
            String sala = cadastrarSala("Lab Restrito", "Bloco A", true);
            String reserva = criarReserva(SOLICITANTE, sala, "SALA", "2027-05-06T08:00", "2027-05-06T10:00");

            chamar(get("/reservas/{id}", reserva), SOLICITANTE).andExpect(jsonPath("$.estado").value("SOLICITADA"));
            chamar(post("/reservas/{id}/aprovacao", reserva), RESPONSAVEL_A).andExpect(jsonPath("$.estado").value("APROVADA"));
        }

        @ParameterizedTest(name = "{0} {1} por {2} -> {3}")
        @CsvSource({
                "POST, /salas,      SOLICITANTE, 403",
                "POST, /salas,      RESPONSAVEL, 403",
                "POST, /professores, SOLICITANTE, 403",
                "POST, /materiais,  RESPONSAVEL, 403",
        })
        @DisplayName("so o Administrador cadastra; o perfil e checado antes do corpo")
        void soAdministradorCadastra(String metodo, String rota, String ator, int esperado) throws Exception {
            chamar(json(post(rota), "{}"), conta(ator))
                    .andExpect(status().is(esperado))
                    .andExpect(jsonPath("$.detail").value(containsString("Administrador")));
        }

        @Test
        @DisplayName("cadastro com campo obrigatorio ausente ou longo demais responde 400 e nomeia o problema")
        void validacaoDeEntrada() throws Exception {
            chamar(json(post("/salas"), "{\"tipo\":\"aula\",\"capacidade\":10,\"localizacao\":\"Bloco A\"}"), ADMIN)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("Nome da sala")));
            chamar(json(post("/salas"), "{\"nome\":\"S\",\"tipo\":\"aula\",\"localizacao\":\"Bloco A\"}"), ADMIN)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("Capacidade")));
            chamar(json(post("/materiais"), "{\"nome\":\"" + "x".repeat(121) + "\",\"tipo\":\"kit\",\"quantidadeTotal\":1}"), ADMIN)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("'nome' excede 120")));
            String muitas = "[" + String.join(",", java.util.Collections.nCopies(21, "\"c\"")) + "]";
            chamar(json(post("/professores"), "{\"nome\":\"P\",\"identificadorInstitucional\":\"X-1\",\"competencias\":" + muitas + "}"), ADMIN)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("no maximo 20")));
            chamar(json(post("/professores"), "{\"nome\":\"P\",\"identificadorInstitucional\":\"X-2\",\"competencias\":[\" \"]}"), ADMIN)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("'competencias'")));
        }

        @Test
        @DisplayName("recurso inativado some do catalogo e da busca; so o Administrador inativa")
        void inativacao() throws Exception {
            String sala = cadastrarSala("Sala Velha", "Bloco A", false);
            String material = JsonPath.read(corpo(chamar(json(post("/materiais"),
                    "{\"nome\":\"Lousa\",\"tipo\":\"lousa\",\"quantidadeTotal\":1}"), ADMIN)), "$.id");
            String professor = JsonPath.read(corpo(chamar(json(post("/professores"),
                    "{\"nome\":\"Prof. Rui\",\"identificadorInstitucional\":\"P-88\",\"competencias\":[]}"), ADMIN)), "$.id");

            chamar(post("/salas/{id}/inativacao", sala), SOLICITANTE).andExpect(status().isForbidden());
            chamar(post("/salas/{id}/inativacao", sala), ADMIN).andExpect(status().isNoContent());
            chamar(post("/materiais/{id}/inativacao", material), ADMIN).andExpect(status().isNoContent());
            chamar(post("/professores/{id}/inativacao", professor), ADMIN).andExpect(status().isNoContent());

            chamar(get("/recursos"), ADMIN)
                    .andExpect(jsonPath("$.salas[*].id").value(not(hasItem(sala))))
                    .andExpect(jsonPath("$.materiais[*].id").value(not(hasItem(material))))
                    .andExpect(jsonPath("$.professores[*].id").value(not(hasItem(professor))));
            chamar(get("/salas/disponiveis").param("inicio", "2027-05-07T08:00").param("fim", "2027-05-07T10:00"), SOLICITANTE)
                    .andExpect(jsonPath("$[*].id").value(not(hasItem(sala))));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Busca de professor e material
    // ---------------------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Busca de professor e material")
    class Busca {

        @Test
        @DisplayName("professor com a competencia aparece; ocupado no horario, nao")
        void professorPorCompetencia() throws Exception {
            String professor = JsonPath.read(corpo(chamar(json(post("/professores"),
                    "{\"nome\":\"Prof. Bia\",\"identificadorInstitucional\":\"P-90\",\"competencias\":[\"Logica\"]}"), ADMIN)), "$.id");
            MockHttpServletRequestBuilder busca = get("/professores/disponiveis").param("competencia", "logica")
                    .param("inicio", "2027-05-08T08:00").param("fim", "2027-05-08T10:00");

            chamar(busca, SOLICITANTE).andExpect(jsonPath("$[*].id").value(hasItem(professor)));
            criarReserva(SOLICITANTE, professor, "PROFESSOR", "2027-05-08T09:00", "2027-05-08T11:00");
            chamar(get("/professores/disponiveis").param("competencia", "logica")
                    .param("inicio", "2027-05-08T08:00").param("fim", "2027-05-08T10:00"), SOLICITANTE)
                    .andExpect(jsonPath("$[*].id").value(not(hasItem(professor))));
        }

        @Test
        @DisplayName("competencia ausente responde 400 nomeando o parametro")
        void competenciaObrigatoria() throws Exception {
            chamar(get("/professores/disponiveis").param("inicio", "2027-05-08T08:00").param("fim", "2027-05-08T10:00"), SOLICITANTE)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("'competencia'")));
            chamar(get("/professores/disponiveis").param("competencia", " ")
                    .param("inicio", "2027-05-08T08:00").param("fim", "2027-05-08T10:00"), SOLICITANTE)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("'competencia'")));
        }

        @Test
        @DisplayName("material aparece so com a quantidade livre pedida")
        void materialPorQuantidade() throws Exception {
            String material = JsonPath.read(corpo(chamar(json(post("/materiais"),
                    "{\"nome\":\"Notebook\",\"tipo\":\"notebook\",\"quantidadeTotal\":3}"), ADMIN)), "$.id");

            chamar(materiais(3), SOLICITANTE).andExpect(jsonPath("$[*].id").value(hasItem(material)));
            chamar(materiais(4), SOLICITANTE).andExpect(jsonPath("$[*].id").value(not(hasItem(material))));
            chamar(materiais(0), SOLICITANTE)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("Quantidade")));
        }

        private MockHttpServletRequestBuilder materiais(int quantidade) {
            return get("/materiais/disponiveis").param("tipo", "notebook").param("quantidade", String.valueOf(quantidade))
                    .param("inicio", "2027-05-09T08:00").param("fim", "2027-05-09T10:00");
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Inicio de uso, retirada e devolucao
    // ---------------------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Inicio de uso, retirada e devolucao")
    class UsoERetirada {

        private String material;
        private String reserva;

        @BeforeEach
        void reservaAprovadaComMaterial() throws Exception {
            String sala = cadastrarSala("Lab Maker", "Bloco A", false);
            material = JsonPath.read(corpo(chamar(json(post("/materiais"),
                    "{\"nome\":\"Kit Robotica\",\"tipo\":\"kit\",\"quantidadeTotal\":2}"), ADMIN)), "$.id");
            reserva = JsonPath.read(corpo(chamar(json(post("/reservas"),
                    "{\"inicio\":\"2027-05-10T08:00\",\"fim\":\"2027-05-10T10:00\",\"finalidade\":\"Oficina\",\"recursos\":["
                            + "{\"tipo\":\"SALA\",\"id\":\"" + sala + "\"},{\"tipo\":\"MATERIAL\",\"id\":\"" + material + "\"}]}"),
                    SOLICITANTE).andExpect(jsonPath("$.estado").value("APROVADA"))), "$.id");
        }

        @Test
        @DisplayName("Solicitante e Responsavel de outro bloco nao iniciam o uso; o Responsavel do bloco inicia")
        void inicioDeUso() throws Exception {
            chamar(post("/reservas/{id}/inicio-uso", reserva), SOLICITANTE).andExpect(status().isForbidden());
            chamar(post("/reservas/{id}/inicio-uso", reserva), RESPONSAVEL_B).andExpect(status().isForbidden());
            chamar(post("/reservas/{id}/inicio-uso", reserva), RESPONSAVEL_A)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("EM_USO"));
            chamar(get("/reservas/{id}/historico", reserva), ADMIN)
                    .andExpect(jsonPath("$[-1].estadoAnterior").value("APROVADA"))
                    .andExpect(jsonPath("$[-1].estadoNovo").value("EM_USO"));
        }

        @Test
        @DisplayName("ciclo completo: retirada, retirada repetida recusada, devolucao e devolucao repetida recusada")
        void cicloDeRetirada() throws Exception {
            chamar(post("/reservas/{id}/inicio-uso", reserva), RESPONSAVEL_A).andExpect(status().isOk());

            String retirada = JsonPath.read(corpo(chamar(json(post("/reservas/{id}/retiradas", reserva),
                    "{\"materialId\":\"" + material + "\"}"), RESPONSAVEL_A)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.materialId").value(material))
                    .andExpect(jsonPath("$.autorId").value(Banco.RESPONSAVEL_BLOCO_A_ID.toString()))
                    .andExpect(jsonPath("$.dataDevolucao").doesNotExist())), "$.id");
            chamar(json(post("/reservas/{id}/retiradas", reserva), "{\"materialId\":\"" + material + "\"}"), RESPONSAVEL_A)
                    .andExpect(status().isConflict());

            chamar(json(post("/retiradas/{id}/devolucao", retirada), "{\"condicao\":\"quebrado\"}"), RESPONSAVEL_A)
                    .andExpect(status().isBadRequest());
            chamar(json(post("/retiradas/{id}/devolucao", retirada), "{\"condicao\":\"danificado\"}"), ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.condicaoDevolucao").value("DANIFICADO"));
            chamar(json(post("/retiradas/{id}/devolucao", retirada), "{\"condicao\":\"INTEGRO\"}"), RESPONSAVEL_A)
                    .andExpect(status().isConflict());

            chamar(get("/reservas/{id}/retiradas", reserva), RESPONSAVEL_A)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].condicaoDevolucao").value("DANIFICADO"));
        }

        @Test
        @DisplayName("retirada antes do inicio do uso responde 409")
        void retiradaSoEmUso() throws Exception {
            chamar(json(post("/reservas/{id}/retiradas", reserva), "{\"materialId\":\"" + material + "\"}"), RESPONSAVEL_A)
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Solicitante e Responsavel de outro bloco nao veem nem registram retirada; devolucao alheia tambem nao")
        void autorizacaoDaRetirada() throws Exception {
            chamar(post("/reservas/{id}/inicio-uso", reserva), RESPONSAVEL_A).andExpect(status().isOk());
            String retirada = JsonPath.read(corpo(chamar(json(post("/reservas/{id}/retiradas", reserva),
                    "{\"materialId\":\"" + material + "\"}"), RESPONSAVEL_A)), "$.id");

            for (String ator : List.of(SOLICITANTE, RESPONSAVEL_B)) {
                chamar(get("/reservas/{id}/retiradas", reserva), ator).andExpect(status().isForbidden());
                chamar(json(post("/reservas/{id}/retiradas", reserva), "{\"materialId\":\"" + material + "\"}"), ator)
                        .andExpect(status().isForbidden());
                chamar(json(post("/retiradas/{id}/devolucao", retirada), "{\"condicao\":\"INTEGRO\"}"), ator)
                        .andExpect(status().isForbidden());
            }
            // reserva inexistente recebe o mesmo 403 da alheia (nao revela ids)
            chamar(get("/reservas/{id}/retiradas", java.util.UUID.randomUUID()), RESPONSAVEL_A).andExpect(status().isForbidden());
            chamar(json(post("/retiradas/{id}/devolucao", java.util.UUID.randomUUID()), "{\"condicao\":\"INTEGRO\"}"), RESPONSAVEL_A)
                    .andExpect(status().isConflict());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Sessao, interface e cabecalhos
    // ---------------------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Sessao e interface")
    class Sessao {

        @Test
        @DisplayName("GET /auth/eu devolve o perfil do token; logout revoga o token")
        void euELogout() throws Exception {
            String token = login(RESPONSAVEL_A);
            api.perform(get("/auth/eu").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.perfil").value("RESPONSAVEL"))
                    .andExpect(jsonPath("$.email").value("responsavel@example.com"));

            api.perform(post("/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
            api.perform(get("/auth/eu").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
            // sem token ou com token desconhecido, o logout responde igual (nao revela tokens validos)
            api.perform(post("/auth/logout")).andExpect(status().isNoContent());
            api.perform(post("/auth/logout").header("Authorization", "Bearer inexistente")).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("a interface e servida pela aplicacao, sem script inline, com CSP restritiva")
        void interfaceComCabecalhosDeSeguranca() throws Exception {
            String html = api.perform(get("/index.html"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                    .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(html).contains("<script src=\"js/app.js\"").doesNotContainPattern("<script>|on(click|load|submit)=");
            api.perform(get("/js/app.js")).andExpect(status().isOk());
            api.perform(get("/css/estilo.css")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("as respostas da API tambem levam os cabecalhos de seguranca")
        void cabecalhosNaApi() throws Exception {
            api.perform(get("/reservas"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().exists("Content-Security-Policy"));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Apoio
    // ---------------------------------------------------------------------------------------------------------------

    private static String conta(String ator) {
        return switch (ator) {
            case "SOLICITANTE" -> SOLICITANTE;
            case "RESPONSAVEL" -> RESPONSAVEL_A;
            case "ADMINISTRADOR" -> ADMIN;
            default -> throw new IllegalArgumentException(ator);
        };
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

    private String cadastrarSala(String nome, String localizacao, boolean restrito) throws Exception {
        return JsonPath.read(corpo(chamar(json(post("/salas"), "{\"nome\":\"" + nome + "\",\"tipo\":\"laboratorio\","
                + "\"capacidade\":20,\"localizacao\":\"" + localizacao + "\",\"restrito\":" + restrito + "}"), ADMIN)
                .andExpect(status().isCreated())), "$.id");
    }

    private String criarReserva(String conta, String recursoId, String tipo, String inicio, String fim) throws Exception {
        return JsonPath.read(corpo(chamar(json(post("/reservas"), "{\"inicio\":\"" + inicio + "\",\"fim\":\"" + fim
                + "\",\"finalidade\":\"Aula\",\"recursos\":[{\"tipo\":\"" + tipo + "\",\"id\":\"" + recursoId + "\"}]}"), conta)
                .andExpect(status().isCreated())), "$.id");
    }

    private List<String> idsListados(String conta) throws Exception {
        return JsonPath.read(corpo(chamar(get("/reservas"), conta).andExpect(status().isOk())), "$[*].id");
    }
}
