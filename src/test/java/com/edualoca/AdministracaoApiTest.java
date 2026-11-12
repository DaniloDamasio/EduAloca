package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.retirada.RetiradaMaterialService;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Funcoes do Administrador e do acompanhamento que antes nao tinham rota: lista de manutencoes e confirmacao explicita
 * de colisao (RF-21, RF-23), consulta de auditoria (RF-28, RF-29), gestao de usuarios (escopo do Administrador) e
 * devolucoes em atraso (RF-26). Tokens sempre de {@code POST /auth/login}; massa criada pela propria API.
 */
@SpringBootTest
class AdministracaoApiTest {

    private static final String SOLICITANTE = "usuario@example.com:senhaValida123";
    private static final String RESPONSAVEL_A = "responsavel@example.com:senhaResponsavel123";
    private static final String RESPONSAVEL_B = "responsavel.b@example.com:senhaResponsavelB123";
    private static final String ADMIN = "admin@example.com:senhaAdmin123";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private RetiradaMaterialService retiradaService;

    private MockMvc api;

    @BeforeEach
    void preparar() {
        api = MockMvcBuilders.webAppContextSetup(contexto).build();
        Banco.seedSeNecessario();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Manutencoes (RF-21, RF-23)
    // ---------------------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Manutencoes")
    class Manutencoes {

        @Test
        @DisplayName("GET /manutencoes lista as manutencoes com o recurso; so o Administrador consulta")
        void listagem() throws Exception {
            String sala = cadastrarSala("Lab Eletrica", "Bloco A", false);
            chamar(json(post("/manutencoes"), manutencao(sala, "2027-06-01T08:00", "2027-06-01T12:00", "troca de fiacao", null)), ADMIN)
                    .andExpect(status().isCreated());

            chamar(get("/manutencoes"), ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.recursoId=='" + sala + "')].recursoNome").value(hasItem("Lab Eletrica")))
                    .andExpect(jsonPath("$[?(@.recursoId=='" + sala + "')].tipoRecurso").value(hasItem("SALA")))
                    .andExpect(jsonPath("$[?(@.recursoId=='" + sala + "')].motivo").value(hasItem("troca de fiacao")));
            chamar(get("/manutencoes"), SOLICITANTE).andExpect(status().isForbidden());
            chamar(get("/manutencoes"), RESPONSAVEL_A).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("RF-23: sem confirmar, a colisao com reserva APROVADA e recusada e a reserva continua APROVADA")
        void semConfirmacaoRecusa() throws Exception {
            String sala = cadastrarSala("Lab Quimica", "Bloco A", false);
            String reserva = criarReserva(SOLICITANTE, sala, "2027-06-02T10:00", "2027-06-02T12:00");

            for (String confirmacao : new String[]{null, "false"}) {
                chamar(json(post("/manutencoes"), manutencao(sala, "2027-06-02T09:00", "2027-06-02T13:00", "vazamento", confirmacao)), ADMIN)
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.detail").value(containsString("confirmação")));
            }
            chamar(get("/reservas/{id}", reserva), SOLICITANTE).andExpect(jsonPath("$.estado").value("APROVADA"));
        }

        @Test
        @DisplayName("RF-23: confirmando, a reserva APROVADA e cancelada com o motivo e a manutencao e registrada")
        void comConfirmacaoCancelaERegistra() throws Exception {
            String sala = cadastrarSala("Lab Fisica", "Bloco A", false);
            String afetada = criarReserva(SOLICITANTE, sala, "2027-06-03T10:00", "2027-06-03T12:00");
            String foraDoPeriodo = criarReserva(SOLICITANTE, sala, "2027-06-03T14:00", "2027-06-03T16:00");

            chamar(json(post("/manutencoes"), manutencao(sala, "2027-06-03T09:00", "2027-06-03T13:00", "vazamento", "true")), ADMIN)
                    .andExpect(status().isCreated());

            chamar(get("/reservas/{id}", afetada), SOLICITANTE).andExpect(jsonPath("$.estado").value("CANCELADA"));
            chamar(get("/reservas/{id}", foraDoPeriodo), SOLICITANTE).andExpect(jsonPath("$.estado").value("APROVADA"));
            chamar(get("/reservas/{id}/historico", afetada), ADMIN)
                    .andExpect(jsonPath("$[-1].estadoNovo").value("CANCELADA"))
                    .andExpect(jsonPath("$[-1].usuarioId").value(Banco.ADMINISTRADOR_ID.toString()))
                    .andExpect(jsonPath("$[-1].motivo").value(containsString("vazamento")));
            chamar(get("/auditoria").param("acao", "CANCELAMENTO_POR_MANUTENCAO"), ADMIN)
                    .andExpect(jsonPath("$[*].entidadeId").value(hasItem(afetada)));
            // a sala fica bloqueada no periodo: nova reserva no mesmo horario e recusada
            chamar(json(post("/reservas"), corpoReserva(sala, "2027-06-03T10:00", "2027-06-03T11:00")), SOLICITANTE)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("manutenção")));
        }

        @Test
        @DisplayName("RF-23: com reserva EM_USO no periodo, nem com confirmacao a manutencao entra e nada e cancelado")
        void emUsoImpedeMesmoConfirmando() throws Exception {
            String sala = cadastrarSala("Lab Robotica", "Bloco A", false);
            String emUso = criarReserva(SOLICITANTE, sala, "2027-06-04T10:00", "2027-06-04T12:00");
            String aprovada = criarReserva(SOLICITANTE, sala, "2027-06-04T12:00", "2027-06-04T13:00");
            chamar(post("/reservas/{id}/inicio-uso", emUso), RESPONSAVEL_A).andExpect(status().isOk());

            chamar(json(post("/manutencoes"), manutencao(sala, "2027-06-04T09:00", "2027-06-04T14:00", "vazamento", "true")), ADMIN)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("em uso")));

            chamar(get("/reservas/{id}", emUso), SOLICITANTE).andExpect(jsonPath("$.estado").value("EM_USO"));
            chamar(get("/reservas/{id}", aprovada), SOLICITANTE).andExpect(jsonPath("$.estado").value("APROVADA"));
            chamar(get("/manutencoes"), ADMIN).andExpect(jsonPath("$[*].recursoId").value(not(hasItem(sala))));
        }

        @Test
        @DisplayName("Confirmacao com motivo vazio responde 400 sem cancelar a reserva")
        void confirmacaoSemMotivo() throws Exception {
            String sala = cadastrarSala("Lab Biologia", "Bloco A", false);
            String reserva = criarReserva(SOLICITANTE, sala, "2027-06-05T10:00", "2027-06-05T12:00");

            chamar(json(post("/manutencoes"), manutencao(sala, "2027-06-05T09:00", "2027-06-05T13:00", " ", "true")), ADMIN)
                    .andExpect(status().isBadRequest());
            chamar(get("/reservas/{id}", reserva), SOLICITANTE).andExpect(jsonPath("$.estado").value("APROVADA"));
        }
    }

    @Nested
    @DisplayName("Encerramento de manutencao e autor na auditoria de cadastro")
    class EncerramentoEAutor {

        @Test
        @DisplayName("Encerrar a manutencao libera o recurso na hora e registra ENCERRAMENTO com o Administrador como autor")
        void encerrarLiberaORecurso() throws Exception {
            String sala = cadastrarSala("Lab Pintura", "Bloco A", false);
            String manutencao = JsonPath.read(corpo(chamar(json(post("/manutencoes"),
                    manutencao(sala, "2027-06-09T08:00", "2027-06-09T12:00", "pintura", null)), ADMIN)
                    .andExpect(status().isCreated())), "$.id");
            chamar(json(post("/reservas"), corpoReserva(sala, "2027-06-09T09:00", "2027-06-09T10:00")), SOLICITANTE)
                    .andExpect(status().isConflict());

            chamar(post("/manutencoes/{id}/encerramento", manutencao), SOLICITANTE).andExpect(status().isForbidden());
            chamar(post("/manutencoes/{id}/encerramento", manutencao), RESPONSAVEL_A).andExpect(status().isForbidden());
            chamar(post("/manutencoes/{id}/encerramento", manutencao), ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recursoId").value(sala));

            chamar(get("/manutencoes"), ADMIN).andExpect(jsonPath("$[*].id").value(not(hasItem(manutencao))));
            chamar(json(post("/reservas"), corpoReserva(sala, "2027-06-09T09:00", "2027-06-09T10:00")), SOLICITANTE)
                    .andExpect(status().isCreated());
            chamar(get("/auditoria").param("entidade", "Manutencao").param("acao", "ENCERRAMENTO"), ADMIN)
                    .andExpect(jsonPath("$[0].entidadeId").value(manutencao))
                    .andExpect(jsonPath("$[0].autorId").value(Banco.ADMINISTRADOR_ID.toString()));
            chamar(post("/manutencoes/{id}/encerramento", manutencao), ADMIN).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("RF-28: criacao, edicao, inativacao e manutencao pela API gravam o Administrador como autor")
        void autorNoCadastro() throws Exception {
            String sala = cadastrarSala("Lab Autor", "Bloco A", false);
            chamar(json(put("/salas/{id}", sala),
                    "{\"nome\":\"Lab Autor 2\",\"tipo\":\"laboratorio\",\"capacidade\":25,\"localizacao\":\"Bloco A\",\"restrito\":false}"),
                    ADMIN).andExpect(status().isOk());
            String manutencao = JsonPath.read(corpo(chamar(json(post("/manutencoes"),
                    manutencao(sala, "2027-06-10T08:00", "2027-06-10T09:00", "limpeza", null)), ADMIN)), "$.id");
            chamar(post("/salas/{id}/inativacao", sala), ADMIN).andExpect(status().isNoContent());

            for (String acao : List.of("CRIACAO", "ATUALIZACAO", "INATIVACAO")) {
                chamar(get("/auditoria").param("entidade", "Sala").param("acao", acao), ADMIN)
                        .andExpect(jsonPath("$[?(@.entidadeId=='" + sala + "')].autorId")
                                .value(hasItem(Banco.ADMINISTRADOR_ID.toString())));
            }
            chamar(get("/auditoria").param("entidade", "Manutencao").param("acao", "CRIACAO"), ADMIN)
                    .andExpect(jsonPath("$[?(@.entidadeId=='" + manutencao + "')].autorId")
                            .value(hasItem(Banco.ADMINISTRADOR_ID.toString())));
        }

        @Test
        @DisplayName("RF-28: retirada e devolucao entram na auditoria com o Responsavel como autor e a condicao na acao")
        void retiradaAuditada() throws Exception {
            String sala = cadastrarSala("Lab Retirada", "Bloco A", false);
            String material = JsonPath.read(corpo(chamar(json(post("/materiais"),
                    "{\"nome\":\"Camera\",\"tipo\":\"camera\",\"quantidadeTotal\":1}"), ADMIN)), "$.id");
            String reserva = JsonPath.read(corpo(chamar(json(post("/reservas"),
                    "{\"inicio\":\"2027-06-11T08:00\",\"fim\":\"2027-06-11T10:00\",\"finalidade\":\"Gravacao\",\"recursos\":["
                            + "{\"tipo\":\"SALA\",\"id\":\"" + sala + "\"},{\"tipo\":\"MATERIAL\",\"id\":\"" + material + "\"}]}"),
                    SOLICITANTE)), "$.id");
            chamar(post("/reservas/{id}/inicio-uso", reserva), RESPONSAVEL_A).andExpect(status().isOk());
            String retirada = JsonPath.read(corpo(chamar(json(post("/reservas/{id}/retiradas", reserva),
                    "{\"materialId\":\"" + material + "\"}"), RESPONSAVEL_A)), "$.id");
            chamar(json(post("/retiradas/{id}/devolucao", retirada), "{\"condicao\":\"DANIFICADO\"}"), RESPONSAVEL_A)
                    .andExpect(status().isOk());

            chamar(get("/auditoria").param("entidade", "Retirada"), ADMIN)
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].acao").value("DEVOLUCAO_DANIFICADO"))
                    .andExpect(jsonPath("$[1].acao").value("RETIRADA"))
                    .andExpect(jsonPath("$[*].entidadeId").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(retirada))))
                    .andExpect(jsonPath("$[*].autorId").value(org.hamcrest.Matchers.everyItem(
                            org.hamcrest.Matchers.is(Banco.RESPONSAVEL_BLOCO_A_ID.toString()))));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Auditoria (RF-28, RF-29)
    // ---------------------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Auditoria")
    class Auditoria {

        @Test
        @DisplayName("Administrador consulta eventos de cadastro e de reserva, do mais recente ao mais antigo, com filtros")
        void consultaComFiltros() throws Exception {
            String sala = cadastrarSala("Sala Auditada", "Bloco A", false);
            String reserva = criarReserva(SOLICITANTE, sala, "2027-06-06T08:00", "2027-06-06T10:00");

            List<String> entidades = JsonPath.read(corpo(chamar(get("/auditoria"), ADMIN).andExpect(status().isOk())), "$[*].entidade");
            assertThat(entidades).as("mais recente primeiro: a reserva foi criada depois da sala").startsWith("Reserva");

            chamar(get("/auditoria").param("entidade", "sala").param("acao", "criacao"), ADMIN)
                    .andExpect(jsonPath("$[*].entidadeId").value(hasItem(sala)))
                    .andExpect(jsonPath("$[*].entidade").value(not(hasItem("Reserva"))));
            chamar(get("/auditoria").param("entidade", "Reserva").param("limite", "1"), ADMIN)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].entidadeId").value(reserva));
        }

        @ParameterizedTest(name = "{0} -> 403")
        @CsvSource({"SOLICITANTE", "RESPONSAVEL"})
        @DisplayName("Solicitante e Responsavel nao consultam a auditoria geral")
        void soAdministrador(String ator) throws Exception {
            chamar(get("/auditoria"), "SOLICITANTE".equals(ator) ? SOLICITANTE : RESPONSAVEL_A)
                    .andExpect(status().isForbidden());
        }

        @ParameterizedTest(name = "limite={0} -> 400")
        @CsvSource({"0", "501"})
        @DisplayName("Limite fora de 1..500 responde 400")
        void limiteInvalido(String limite) throws Exception {
            chamar(get("/auditoria").param("limite", limite), ADMIN)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("500")));
        }

        @Test
        @DisplayName("RF-29: a auditoria nao tem rota de escrita")
        void semEscrita() throws Exception {
            chamar(json(post("/auditoria"), "{}"), ADMIN).andExpect(status().isMethodNotAllowed());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Usuarios
    // ---------------------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Usuarios")
    class Usuarios {

        @Test
        @DisplayName("Administrador cadastra Responsavel com local; a conta faz login com o perfil e decide reservas do local")
        void cadastraResponsavelQueDecide() throws Exception {
            String id = JsonPath.read(corpo(chamar(json(post("/usuarios"),
                    "{\"email\":\" Nova.Resp@Example.com \",\"senha\":\"senhaForte123\",\"perfil\":\"responsavel\",\"localizacao\":\"Bloco C\"}"),
                    ADMIN).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("nova.resp@example.com"))
                    .andExpect(jsonPath("$.perfil").value("RESPONSAVEL"))
                    .andExpect(jsonPath("$.localizacao").value("Bloco C"))
                    .andExpect(jsonPath("$.ativo").value(true))
                    .andExpect(jsonPath("$.senha").doesNotExist())
                    .andExpect(jsonPath("$.senhaHash").doesNotExist())), "$.id");

            String novaResp = "nova.resp@example.com:senhaForte123";
            chamar(get("/auth/eu"), novaResp).andExpect(jsonPath("$.perfil").value("RESPONSAVEL")).andExpect(jsonPath("$.id").value(id));

            String sala = cadastrarSala("Lab C", "Bloco C", true);
            String reserva = criarReserva(SOLICITANTE, sala, "2027-06-07T08:00", "2027-06-07T10:00");
            chamar(post("/reservas/{id}/aprovacao", reserva), RESPONSAVEL_A).andExpect(status().isForbidden());
            chamar(post("/reservas/{id}/aprovacao", reserva), novaResp).andExpect(jsonPath("$.estado").value("APROVADA"));

            chamar(get("/auditoria").param("entidade", "Usuario"), ADMIN)
                    .andExpect(jsonPath("$[0].entidadeId").value(id))
                    .andExpect(jsonPath("$[0].acao").value("CRIACAO"))
                    .andExpect(jsonPath("$[0].autorId").value(Banco.ADMINISTRADOR_ID.toString()));
            // RNF-05: a senha nao fica em claro
            assertThat(Banco.credenciais.get("nova.resp@example.com")).isNotEqualTo("senhaForte123").startsWith("pbkdf2");
        }

        @Test
        @DisplayName("Listagem traz as contas do seed com perfil e local; nunca a senha")
        void listagem() throws Exception {
            chamar(get("/usuarios"), ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.email=='responsavel.b@example.com')].localizacao").value(hasItem("Bloco B")))
                    .andExpect(jsonPath("$[?(@.email=='admin@example.com')].perfil").value(hasItem("ADMINISTRADOR")))
                    .andExpect(jsonPath("$[?(@.email=='usuario@example.com')].perfil").value(hasItem("SOLICITANTE")))
                    .andExpect(jsonPath("$[*].senha").isEmpty())
                    .andExpect(jsonPath("$[*].senhaHash").isEmpty());
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "e-mail invalido      | {\"email\":\"sem-arroba\",\"senha\":\"senhaForte123\",\"perfil\":\"SOLICITANTE\"}                     | E-mail inválido",
                "senha curta          | {\"email\":\"a@b.com\",\"senha\":\"curta\",\"perfil\":\"SOLICITANTE\"}                               | Senha deve ter",
                "perfil ausente       | {\"email\":\"a@b.com\",\"senha\":\"senhaForte123\"}                                                 | Perfil obrigatório",
                "perfil invalido      | {\"email\":\"a@b.com\",\"senha\":\"senhaForte123\",\"perfil\":\"ROOT\"}                              | Perfil inválido",
                "responsavel sem local| {\"email\":\"a@b.com\",\"senha\":\"senhaForte123\",\"perfil\":\"RESPONSAVEL\"}                       | local sob sua responsabilidade",
        })
        @DisplayName("Entrada invalida responde 400 e nomeia o problema")
        void validacao(String caso, String corpo, String mensagem) throws Exception {
            chamar(json(post("/usuarios"), corpo), ADMIN)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString(mensagem)));
        }

        @Test
        @DisplayName("E-mail repetido (inclusive com outra caixa) e local que ja tem Responsavel respondem 409")
        void duplicidade() throws Exception {
            chamar(json(post("/usuarios"), "{\"email\":\"ADMIN@example.com\",\"senha\":\"senhaForte123\",\"perfil\":\"SOLICITANTE\"}"), ADMIN)
                    .andExpect(status().isConflict());
            chamar(json(post("/usuarios"), "{\"email\":\"outro@example.com\",\"senha\":\"senhaForte123\",\"perfil\":\"RESPONSAVEL\",\"localizacao\":\"Bloco A\"}"), ADMIN)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("já tem um Responsável")));
        }

        @Test
        @DisplayName("Inativar: o token da conta deixa de valer, o login falha e o e-mail nao pode ser recadastrado")
        void inativacao() throws Exception {
            String id = JsonPath.read(corpo(chamar(json(post("/usuarios"),
                    "{\"email\":\"temp@example.com\",\"senha\":\"senhaForte123\",\"perfil\":\"SOLICITANTE\"}"), ADMIN)), "$.id");
            String token = login("temp@example.com:senhaForte123");

            chamar(post("/usuarios/{id}/inativacao", id), ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ativo").value(false));

            api.perform(get("/auth/eu").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
            api.perform(json(post("/auth/login"), "{\"email\":\"temp@example.com\",\"senha\":\"senhaForte123\"}"))
                    .andExpect(status().isUnauthorized());
            chamar(json(post("/usuarios"), "{\"email\":\"temp@example.com\",\"senha\":\"senhaForte123\",\"perfil\":\"SOLICITANTE\"}"), ADMIN)
                    .andExpect(status().isConflict());
            chamar(post("/usuarios/{id}/inativacao", id), ADMIN).andExpect(status().isNotFound());
            chamar(get("/auditoria").param("entidade", "Usuario").param("acao", "INATIVACAO"), ADMIN)
                    .andExpect(jsonPath("$[0].entidadeId").value(id));
        }

        @Test
        @DisplayName("Responsavel inativado deixa de responder pelo local")
        void responsavelInativadoPerdeOLocal() throws Exception {
            chamar(post("/usuarios/{id}/inativacao", Banco.RESPONSAVEL_BLOCO_B_ID), ADMIN).andExpect(status().isOk());

            assertThat(Banco.responsavelPorLocalizacao).doesNotContainKey("Bloco B");
            chamar(get("/usuarios"), ADMIN)
                    .andExpect(jsonPath("$[?(@.email=='responsavel.b@example.com')].ativo").value(hasItem(false)));
        }

        @Test
        @DisplayName("Administrador nao inativa a propria conta (409)")
        void naoInativaASiMesmo() throws Exception {
            chamar(post("/usuarios/{id}/inativacao", Banco.ADMINISTRADOR_ID), ADMIN).andExpect(status().isConflict());
        }

        @ParameterizedTest(name = "{0} {1} -> 403")
        @CsvSource({"GET, /usuarios", "POST, /usuarios"})
        @DisplayName("Solicitante e Responsavel nao gerenciam usuarios; o perfil e checado antes do corpo")
        void soAdministrador(String metodo, String rota) throws Exception {
            for (String ator : List.of(SOLICITANTE, RESPONSAVEL_A)) {
                MockHttpServletRequestBuilder requisicao = "GET".equals(metodo) ? get(rota) : json(post(rota), "{\"perfil\":\"ROOT\"}");
                chamar(requisicao, ator).andExpect(status().isForbidden());
            }
            chamar(post("/usuarios/{id}/inativacao", Banco.ADMINISTRADOR_ID), RESPONSAVEL_A).andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Devolucoes em atraso (RF-26)
    // ---------------------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Devolucoes em atraso")
    class Pendencias {

        @Test
        @DisplayName("Retirada nao devolvida apos o fim da reserva aparece como pendente para quem acompanha o local")
        void pendenteVisivelParaOResponsavelDoLocal() throws Exception {
            String sala = cadastrarSala("Lab Maker", "Bloco A", false);
            String material = JsonPath.read(corpo(chamar(json(post("/materiais"),
                    "{\"nome\":\"Kit Solda\",\"tipo\":\"kit\",\"quantidadeTotal\":2}"), ADMIN)), "$.id");
            String reserva = JsonPath.read(corpo(chamar(json(post("/reservas"),
                    "{\"inicio\":\"2027-06-08T08:00\",\"fim\":\"2027-06-08T10:00\",\"finalidade\":\"Oficina\",\"recursos\":["
                            + "{\"tipo\":\"SALA\",\"id\":\"" + sala + "\"},{\"tipo\":\"MATERIAL\",\"id\":\"" + material + "\"}]}"),
                    SOLICITANTE)), "$.id");
            chamar(post("/reservas/{id}/inicio-uso", reserva), RESPONSAVEL_A).andExpect(status().isOk());
            String retirada = JsonPath.read(corpo(chamar(json(post("/reservas/{id}/retiradas", reserva),
                    "{\"materialId\":\"" + material + "\"}"), RESPONSAVEL_A)), "$.id");

            chamar(get("/retiradas/pendentes"), RESPONSAVEL_A).andExpect(jsonPath("$[*].id").value(not(hasItem(retirada))));

            retiradaService.sinalizarPendencias(LocalDateTime.of(2027, 6, 8, 10, 1));

            chamar(get("/retiradas/pendentes"), RESPONSAVEL_A)
                    .andExpect(jsonPath("$[*].id").value(hasItem(retirada)))
                    .andExpect(jsonPath("$[?(@.id=='" + retirada + "')].pendente").value(hasItem(true)));
            chamar(get("/retiradas/pendentes"), ADMIN).andExpect(jsonPath("$[*].id").value(hasItem(retirada)));
            chamar(get("/retiradas/pendentes"), RESPONSAVEL_B).andExpect(jsonPath("$[*].id").value(not(hasItem(retirada))));
            chamar(get("/retiradas/pendentes"), SOLICITANTE).andExpect(status().isForbidden());

            chamar(json(post("/retiradas/{id}/devolucao", retirada), "{\"condicao\":\"INTEGRO\"}"), RESPONSAVEL_A)
                    .andExpect(jsonPath("$.pendente").value(false));
            chamar(get("/retiradas/pendentes"), RESPONSAVEL_A).andExpect(jsonPath("$[*].id").value(not(hasItem(retirada))));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Apoio
    // ---------------------------------------------------------------------------------------------------------------

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

    private static String manutencao(String salaId, String inicio, String fim, String motivo, String confirmar) {
        return "{\"tipoRecurso\":\"SALA\",\"recursoId\":\"" + salaId + "\",\"inicio\":\"" + inicio + "\",\"fim\":\"" + fim
                + "\",\"motivo\":\"" + motivo + "\"" + (confirmar == null ? "" : ",\"confirmarColisao\":" + confirmar) + "}";
    }

    private static String corpoReserva(String salaId, String inicio, String fim) {
        return "{\"inicio\":\"" + inicio + "\",\"fim\":\"" + fim + "\",\"finalidade\":\"Aula\",\"recursos\":[{\"tipo\":\"SALA\",\"id\":\""
                + salaId + "\"}]}";
    }

    private String cadastrarSala(String nome, String localizacao, boolean restrito) throws Exception {
        return JsonPath.read(corpo(chamar(json(post("/salas"), "{\"nome\":\"" + nome + "\",\"tipo\":\"laboratorio\","
                + "\"capacidade\":20,\"localizacao\":\"" + localizacao + "\",\"restrito\":" + restrito + "}"), ADMIN)
                .andExpect(status().isCreated())), "$.id");
    }

    private String criarReserva(String conta, String salaId, String inicio, String fim) throws Exception {
        return JsonPath.read(corpo(chamar(json(post("/reservas"), corpoReserva(salaId, inicio, fim)), conta)
                .andExpect(status().isCreated())), "$.id");
    }
}
