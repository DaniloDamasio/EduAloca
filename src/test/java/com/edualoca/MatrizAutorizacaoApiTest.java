package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.recurso.Sala;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Executa a matriz de autorizacao de {@code DOCS/seguranca/matriz-autorizacao.md} (secao 3) celula a celula contra a
 * API, com MockMvc e <b>tokens reais</b>: todo token sai de {@code POST /auth/login} com as credenciais da conta de
 * cada perfil, nunca de {@code TokenService.emitir} direto. Assim o perfil que vale e o que o login emite (D-01).
 *
 * <h2>Contas usadas (seed do perfil padrao em memoria)</h2>
 * <pre>
 * SOLICITANTE                 usuario@example.com      dono das reservas da massa
 * OUTRO_SOLICITANTE           novo@example.com         tenta operar a reserva do dono (posse, P-1)
 * RESPONSAVEL                 responsavel@example.com  responsavel competente pelo "Bloco A" (P-2)
 * RESPONSAVEL_NAO_COMPETENTE  responsavel.b@example.com responsavel pelo "Bloco B", nao pelo "Bloco A"
 * ADMINISTRADOR               admin@example.com
 * </pre>
 *
 * <h2>Massa (criada pela API como o dono, antes de cada celula)</h2>
 * <pre>
 * Catalogo (semeado direto, nao ha rota de cadastro): "Sala Livre" e "Lab Restrito", ambas no Bloco A.
 * PROPRIA   Sala Livre    10/03/2027 08:00-10:00  APROVADA
 * RESTRITA  Lab Restrito  11/03/2027 08:00-10:00  SOLICITADA (aguarda o Responsavel do Bloco A)
 * INEXISTENTE  um UUID aleatorio que nunca foi reserva
 * </pre>
 *
 * <p>Toda resposta de erro tambem e conferida como ProblemDetail (RFC 9457) sem stack trace, SQL ou nome de classe
 * interna (RF-38, RNF-06), e a validacao de entrada (RNF-04) espera 400 com mensagem que nomeia o campo.
 */
@SpringBootTest
class MatrizAutorizacaoApiTest {

    enum Ator {
        ANONIMO(null, null),
        SOLICITANTE("usuario@example.com", "senhaValida123"),
        OUTRO_SOLICITANTE("novo@example.com", "minhaSenha123"),
        RESPONSAVEL("responsavel@example.com", "senhaResponsavel123"),
        RESPONSAVEL_NAO_COMPETENTE("responsavel.b@example.com", "senhaResponsavelB123"),
        ADMINISTRADOR("admin@example.com", "senhaAdmin123");

        final String email;
        final String senha;

        Ator(String email, String senha) {
            this.email = email;
            this.senha = senha;
        }
    }

    enum Alvo { NENHUM, PROPRIA, RESTRITA, INEXISTENTE }

    /** Operacoes da secao 3 da matriz; cada uma monta a requisicao com corpo valido para o alvo recebido. */
    enum Operacao {
        LOGIN(1, "POST /auth/login", id -> post("/auth/login")),
        BUSCAR_SALAS(2, "GET /salas/disponiveis",
                id -> get("/salas/disponiveis").param("inicio", "2027-03-10T08:00").param("fim", "2027-03-10T10:00")),
        CRIAR_RESERVA(3, "POST /reservas", id -> json(post("/reservas"), corpoReserva("2027-03-12T08:00", "2027-03-12T10:00"))),
        CONSULTAR_RESERVA(4, "GET /reservas/{id}", id -> get("/reservas/{id}", id)),
        ALTERAR_RESERVA(5, "PUT /reservas/{id}",
                id -> json(put("/reservas/{id}", id), "{\"inicio\":\"2027-03-10T14:00\",\"fim\":\"2027-03-10T16:00\"}")),
        CANCELAR_RESERVA(6, "POST /reservas/{id}/cancelamento",
                id -> json(post("/reservas/{id}/cancelamento", id), "{\"motivo\":\"aula remarcada\"}")),
        APROVAR_RESERVA(7, "POST /reservas/{id}/aprovacao", id -> post("/reservas/{id}/aprovacao", id)),
        REJEITAR_RESERVA(8, "POST /reservas/{id}/rejeicao",
                id -> json(post("/reservas/{id}/rejeicao", id), "{\"justificativa\":\"laboratorio reservado para banca\"}")),
        HISTORICO_RESERVA(9, "GET /reservas/{id}/historico", id -> get("/reservas/{id}/historico", id)),
        EXCLUIR_RESERVA(10, "DELETE /reservas/{id}", id -> delete("/reservas/{id}", id)),
        CADASTRAR_MANUTENCAO(11, "POST /manutencoes", id -> post("/manutencoes")),
        RELATORIO_UTILIZACAO(12, "GET /relatorios/utilizacao",
                id -> get("/relatorios/utilizacao").param("inicio", "2027-03-01T00:00").param("fim", "2027-04-01T00:00"));

        final int linha;
        final String rota;
        final Function<UUID, MockHttpServletRequestBuilder> requisicao;

        Operacao(int linha, String rota, Function<UUID, MockHttpServletRequestBuilder> requisicao) {
            this.linha = linha;
            this.rota = rota;
            this.requisicao = requisicao;
        }
    }

    private static final Pattern DETALHE_INTERNO = Pattern.compile(String.join("|",
            "exception", "\\bat [a-z_$][\\w$]*\\.", "\\.java:\\d+", "com\\.edualoca", "\\bjava\\.", "\\bjakarta\\.",
            "org\\.springframework", "org\\.postgresql", "com\\.zaxxer", "\\bsql", "\\bselect\\b.*\\bfrom\\b",
            "\\binsert into\\b", "\\bupdate\\b.+\\bset\\b", "\\bdelete from\\b", "\"trace\"", "\"exception\""),
            Pattern.CASE_INSENSITIVE);

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc api;
    private final Map<Ator, String> tokens = new EnumMap<>(Ator.class);
    private final Map<Alvo, UUID> alvos = new EnumMap<>(Alvo.class);

    private static final Sala SALA_LIVRE = new Sala(UUID.fromString("00000000-0000-0000-0000-00000000a001"),
            "Sala Livre", "aula", 30, "Bloco A", false);
    private static final Sala LAB_RESTRITO = new Sala(UUID.fromString("00000000-0000-0000-0000-00000000a002"),
            "Lab Restrito", "laboratorio", 20, "Bloco A", true);

    @BeforeEach
    void semearMassa() throws Exception {
        api = MockMvcBuilders.webAppContextSetup(contexto).build();
        Banco.seedSeNecessario();
        Banco.salas.put(SALA_LIVRE.getId(), SALA_LIVRE);
        Banco.salas.put(LAB_RESTRITO.getId(), LAB_RESTRITO);
        alvos.put(Alvo.PROPRIA, criarReservaDoDono(SALA_LIVRE, "2027-03-10T08:00", "2027-03-10T10:00", "APROVADA"));
        alvos.put(Alvo.RESTRITA, criarReservaDoDono(LAB_RESTRITO, "2027-03-11T08:00", "2027-03-11T10:00", "SOLICITADA"));
        alvos.put(Alvo.INEXISTENTE, UUID.randomUUID());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Matriz (secao 3): uma linha de dados por celula
    // ------------------------------------------------------------------------------------------------------------

    static Stream<Arguments> celulas() {
        return Stream.of(
                // 1 Login: publico; o token emitido carrega o perfil da conta (D-01)
                celula(Operacao.LOGIN, Ator.ANONIMO, Alvo.NENHUM, 200),
                celula(Operacao.LOGIN, Ator.SOLICITANTE, Alvo.NENHUM, 200),
                celula(Operacao.LOGIN, Ator.RESPONSAVEL, Alvo.NENHUM, 200),
                celula(Operacao.LOGIN, Ator.ADMINISTRADOR, Alvo.NENHUM, 200),
                // 2 Buscar salas disponiveis
                celula(Operacao.BUSCAR_SALAS, Ator.ANONIMO, Alvo.NENHUM, 401),
                celula(Operacao.BUSCAR_SALAS, Ator.SOLICITANTE, Alvo.NENHUM, 200),
                celula(Operacao.BUSCAR_SALAS, Ator.RESPONSAVEL, Alvo.NENHUM, 200),
                celula(Operacao.BUSCAR_SALAS, Ator.ADMINISTRADOR, Alvo.NENHUM, 200),
                // 3 Criar reserva: so o Solicitante (RF-12)
                celula(Operacao.CRIAR_RESERVA, Ator.ANONIMO, Alvo.NENHUM, 401),
                celula(Operacao.CRIAR_RESERVA, Ator.SOLICITANTE, Alvo.NENHUM, 201),
                celula(Operacao.CRIAR_RESERVA, Ator.RESPONSAVEL, Alvo.NENHUM, 403),
                celula(Operacao.CRIAR_RESERVA, Ator.ADMINISTRADOR, Alvo.NENHUM, 403),
                // 4 Consultar reserva: Solicitante so a propria, 403 tambem para id inexistente (P-1)
                celula(Operacao.CONSULTAR_RESERVA, Ator.ANONIMO, Alvo.PROPRIA, 401),
                celula(Operacao.CONSULTAR_RESERVA, Ator.SOLICITANTE, Alvo.PROPRIA, 200),
                celula(Operacao.CONSULTAR_RESERVA, Ator.OUTRO_SOLICITANTE, Alvo.PROPRIA, 403),
                celula(Operacao.CONSULTAR_RESERVA, Ator.SOLICITANTE, Alvo.INEXISTENTE, 403),
                celula(Operacao.CONSULTAR_RESERVA, Ator.RESPONSAVEL, Alvo.PROPRIA, 200),
                celula(Operacao.CONSULTAR_RESERVA, Ator.ADMINISTRADOR, Alvo.PROPRIA, 200),
                // 5 Alterar reserva (RF-13)
                celula(Operacao.ALTERAR_RESERVA, Ator.ANONIMO, Alvo.PROPRIA, 401),
                celula(Operacao.ALTERAR_RESERVA, Ator.SOLICITANTE, Alvo.PROPRIA, 200),
                celula(Operacao.ALTERAR_RESERVA, Ator.OUTRO_SOLICITANTE, Alvo.PROPRIA, 403),
                celula(Operacao.ALTERAR_RESERVA, Ator.SOLICITANTE, Alvo.INEXISTENTE, 403),
                celula(Operacao.ALTERAR_RESERVA, Ator.RESPONSAVEL, Alvo.PROPRIA, 403),
                celula(Operacao.ALTERAR_RESERVA, Ator.ADMINISTRADOR, Alvo.PROPRIA, 403),
                // 6 Cancelar reserva
                celula(Operacao.CANCELAR_RESERVA, Ator.ANONIMO, Alvo.PROPRIA, 401),
                celula(Operacao.CANCELAR_RESERVA, Ator.SOLICITANTE, Alvo.PROPRIA, 200),
                celula(Operacao.CANCELAR_RESERVA, Ator.OUTRO_SOLICITANTE, Alvo.PROPRIA, 403),
                celula(Operacao.CANCELAR_RESERVA, Ator.SOLICITANTE, Alvo.INEXISTENTE, 403),
                celula(Operacao.CANCELAR_RESERVA, Ator.RESPONSAVEL, Alvo.PROPRIA, 403),
                celula(Operacao.CANCELAR_RESERVA, Ator.ADMINISTRADOR, Alvo.PROPRIA, 403),
                // 7 Aprovar reserva restrita: Responsavel competente (P-2) ou Administrador (RF-19, decisao de 11/11/2026)
                celula(Operacao.APROVAR_RESERVA, Ator.ANONIMO, Alvo.RESTRITA, 401),
                celula(Operacao.APROVAR_RESERVA, Ator.SOLICITANTE, Alvo.RESTRITA, 403),
                celula(Operacao.APROVAR_RESERVA, Ator.RESPONSAVEL, Alvo.RESTRITA, 200),
                celula(Operacao.APROVAR_RESERVA, Ator.RESPONSAVEL_NAO_COMPETENTE, Alvo.RESTRITA, 403),
                celula(Operacao.APROVAR_RESERVA, Ator.ADMINISTRADOR, Alvo.RESTRITA, 200),
                // 8 Rejeitar reserva restrita: mesma regra da aprovacao
                celula(Operacao.REJEITAR_RESERVA, Ator.ANONIMO, Alvo.RESTRITA, 401),
                celula(Operacao.REJEITAR_RESERVA, Ator.SOLICITANTE, Alvo.RESTRITA, 403),
                celula(Operacao.REJEITAR_RESERVA, Ator.RESPONSAVEL, Alvo.RESTRITA, 200),
                celula(Operacao.REJEITAR_RESERVA, Ator.RESPONSAVEL_NAO_COMPETENTE, Alvo.RESTRITA, 403),
                celula(Operacao.REJEITAR_RESERVA, Ator.ADMINISTRADOR, Alvo.RESTRITA, 200),
                // 9 Historico auditavel
                celula(Operacao.HISTORICO_RESERVA, Ator.ANONIMO, Alvo.PROPRIA, 401),
                celula(Operacao.HISTORICO_RESERVA, Ator.SOLICITANTE, Alvo.PROPRIA, 403),
                celula(Operacao.HISTORICO_RESERVA, Ator.RESPONSAVEL, Alvo.PROPRIA, 200),
                celula(Operacao.HISTORICO_RESERVA, Ator.ADMINISTRADOR, Alvo.PROPRIA, 200),
                // 10 Excluir reserva: 403 para todo perfil autenticado, inclusive em SOLICITADA (P-3)
                celula(Operacao.EXCLUIR_RESERVA, Ator.ANONIMO, Alvo.RESTRITA, 401),
                celula(Operacao.EXCLUIR_RESERVA, Ator.SOLICITANTE, Alvo.RESTRITA, 403),
                celula(Operacao.EXCLUIR_RESERVA, Ator.SOLICITANTE, Alvo.PROPRIA, 403),
                celula(Operacao.EXCLUIR_RESERVA, Ator.RESPONSAVEL, Alvo.RESTRITA, 403),
                celula(Operacao.EXCLUIR_RESERVA, Ator.ADMINISTRADOR, Alvo.RESTRITA, 403),
                // 11 Cadastrar manutencao/bloqueio
                celula(Operacao.CADASTRAR_MANUTENCAO, Ator.ANONIMO, Alvo.NENHUM, 401),
                celula(Operacao.CADASTRAR_MANUTENCAO, Ator.SOLICITANTE, Alvo.NENHUM, 403),
                celula(Operacao.CADASTRAR_MANUTENCAO, Ator.RESPONSAVEL, Alvo.NENHUM, 403),
                celula(Operacao.CADASTRAR_MANUTENCAO, Ator.ADMINISTRADOR, Alvo.NENHUM, 201),
                // 12 Relatorio de utilizacao
                celula(Operacao.RELATORIO_UTILIZACAO, Ator.ANONIMO, Alvo.NENHUM, 401),
                celula(Operacao.RELATORIO_UTILIZACAO, Ator.SOLICITANTE, Alvo.NENHUM, 403),
                celula(Operacao.RELATORIO_UTILIZACAO, Ator.RESPONSAVEL, Alvo.NENHUM, 200),
                celula(Operacao.RELATORIO_UTILIZACAO, Ator.ADMINISTRADOR, Alvo.NENHUM, 200));
    }

    static Stream<Arguments> celulasDeErro() {
        return celulas().filter(argumentos -> (int) argumentos.get()[4] >= 400);
    }

    @ParameterizedTest(name = "linha {0}: {1} como {2} ({3}) -> {4}")
    @MethodSource("celulas")
    @DisplayName("Matriz de autorizacao: cada celula (operacao, perfil, status esperado)")
    void celulaDaMatriz(Operacao operacao, String rota, Ator ator, Alvo alvo, int esperado) throws Exception {
        MvcResult resposta = executar(operacao, ator, alvo);
        assertEquals(esperado, resposta.getResponse().getStatus(),
                () -> "linha " + operacao.linha + " " + rota + " como " + ator + " (" + alvo + "): corpo " + corpo(resposta));
    }

    @ParameterizedTest(name = "linha {0}: {1} como {2} ({3}) -> {4}")
    @MethodSource("celulasDeErro")
    @DisplayName("Toda celula de erro responde ProblemDetail sem stack trace, SQL ou nome de classe interna")
    void celulaDeErroEmProblemDetail(Operacao operacao, String rota, Ator ator, Alvo alvo, int esperado) throws Exception {
        MvcResult resposta = executar(operacao, ator, alvo);
        assertThat(resposta.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        assertProblemDetailSemDetalheInterno(resposta);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Posse (P-1): Solicitante contra a reserva de outro Solicitante, pelo id
    // ------------------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Posse: Solicitante nao altera nem cancela a reserva de outro Solicitante pelo id")
    class Posse {

        @Test
        @DisplayName("Alterar a reserva alheia: 403 e a reserva do dono continua no mesmo horario")
        void alterarReservaAlheia() throws Exception {
            UUID reservaDoDono = alvos.get(Alvo.PROPRIA);
            MvcResult resposta = api.perform(comToken(json(put("/reservas/{id}", reservaDoDono),
                    "{\"inicio\":\"2027-03-10T18:00\",\"fim\":\"2027-03-10T20:00\"}"), Ator.OUTRO_SOLICITANTE)).andReturn();

            assertEquals(403, resposta.getResponse().getStatus(), () -> corpo(resposta));
            assertProblemDetailSemDetalheInterno(resposta);
            assertReservaDoDonoIntacta(reservaDoDono);
        }

        @Test
        @DisplayName("Cancelar a reserva alheia: 403 e a reserva do dono continua APROVADA")
        void cancelarReservaAlheia() throws Exception {
            UUID reservaDoDono = alvos.get(Alvo.PROPRIA);
            MvcResult resposta = api.perform(comToken(json(post("/reservas/{id}/cancelamento", reservaDoDono),
                    "{\"motivo\":\"nao preciso mais\"}"), Ator.OUTRO_SOLICITANTE)).andReturn();

            assertEquals(403, resposta.getResponse().getStatus(), () -> corpo(resposta));
            assertProblemDetailSemDetalheInterno(resposta);
            assertReservaDoDonoIntacta(reservaDoDono);
        }

        @Test
        @DisplayName("Id alheio e id inexistente recebem a mesma resposta: o 403 nao revela quais ids existem")
        void idAlheioEIdInexistenteSaoIndistinguiveis() throws Exception {
            for (Operacao operacao : List.of(Operacao.CONSULTAR_RESERVA, Operacao.ALTERAR_RESERVA, Operacao.CANCELAR_RESERVA)) {
                MvcResult alheia = api.perform(comToken(operacao.requisicao.apply(alvos.get(Alvo.PROPRIA)),
                        Ator.OUTRO_SOLICITANTE)).andReturn();
                MvcResult inexistente = api.perform(comToken(operacao.requisicao.apply(alvos.get(Alvo.INEXISTENTE)),
                        Ator.OUTRO_SOLICITANTE)).andReturn();

                assertEquals(403, alheia.getResponse().getStatus(), () -> operacao.rota + " alheia: " + corpo(alheia));
                assertEquals(alheia.getResponse().getStatus(), inexistente.getResponse().getStatus(),
                        () -> operacao.rota + " inexistente: " + corpo(inexistente));
                // "instance" traz o proprio id da rota e difere por natureza; o que nao pode diferir e o problema.
                for (String campo : List.of("$.status", "$.title", "$.detail")) {
                    assertEquals((Object) JsonPath.read(corpo(alheia), campo), JsonPath.read(corpo(inexistente), campo),
                            () -> operacao.rota + ": " + campo + " diferente revela se o id existe");
                }
            }
        }

        private void assertReservaDoDonoIntacta(UUID reservaDoDono) throws Exception {
            MvcResult consulta = api.perform(comToken(get("/reservas/{id}", reservaDoDono), Ator.SOLICITANTE)).andReturn();
            assertEquals(200, consulta.getResponse().getStatus(), () -> corpo(consulta));
            String json = corpo(consulta);
            assertEquals("APROVADA", JsonPath.read(json, "$.estado"));
            assertEquals("2027-03-10T08:00:00", JsonPath.read(json, "$.inicio"));
            assertEquals("2027-03-10T10:00:00", JsonPath.read(json, "$.fim"));
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Validacao de entrada (RNF-04): 400 com mensagem que nomeia o campo, em ProblemDetail
    // ------------------------------------------------------------------------------------------------------------

    static Stream<Arguments> entradasInvalidas() {
        return Stream.of(
                // termino antes do inicio
                invalida("criar reserva com fim antes do inicio", Ator.SOLICITANTE, Alvo.NENHUM, "fim",
                        id -> json(post("/reservas"), corpoReserva("2027-03-12T10:00", "2027-03-12T08:00"))),
                invalida("criar reserva com fim igual ao inicio", Ator.SOLICITANTE, Alvo.NENHUM, "fim",
                        id -> json(post("/reservas"), corpoReserva("2027-03-12T08:00", "2027-03-12T08:00"))),
                invalida("alterar reserva com fim antes do inicio", Ator.SOLICITANTE, Alvo.PROPRIA, "fim",
                        id -> json(put("/reservas/{id}", id), "{\"inicio\":\"2027-03-10T16:00\",\"fim\":\"2027-03-10T14:00\"}")),
                invalida("buscar salas com fim antes do inicio", Ator.SOLICITANTE, Alvo.NENHUM, "fim",
                        id -> get("/salas/disponiveis").param("inicio", "2027-03-10T10:00").param("fim", "2027-03-10T08:00")),
                invalida("manutencao com fim antes do inicio", Ator.ADMINISTRADOR, Alvo.NENHUM, "fim",
                        id -> json(post("/manutencoes"), corpoManutencao("2027-03-20T12:00", "2027-03-20T08:00", "\"troca de piso\""))),
                invalida("relatorio com fim antes do inicio", Ator.RESPONSAVEL, Alvo.NENHUM, "fim",
                        id -> get("/relatorios/utilizacao").param("inicio", "2027-04-01T00:00").param("fim", "2027-03-01T00:00")),
                // capacidade e quantidade negativas
                invalida("buscar salas com capacidade minima negativa", Ator.SOLICITANTE, Alvo.NENHUM, "capacidade",
                        id -> get("/salas/disponiveis").param("inicio", "2027-03-10T08:00").param("fim", "2027-03-10T10:00")
                                .param("capacidadeMinima", "-5")),
                invalida("criar reserva com quantidade negativa de recurso", Ator.SOLICITANTE, Alvo.NENHUM, "quantidade",
                        id -> json(post("/reservas"), "{\"inicio\":\"2027-03-12T08:00\",\"fim\":\"2027-03-12T10:00\","
                                + "\"finalidade\":\"Aula\",\"recursos\":[{\"tipo\":\"SALA\",\"id\":\"" + SALA_LIVRE.getId()
                                + "\",\"quantidade\":-2}]}")),
                // campos ausentes
                invalida("criar reserva sem inicio", Ator.SOLICITANTE, Alvo.NENHUM, "inicio",
                        id -> json(post("/reservas"), "{\"fim\":\"2027-03-12T10:00\",\"finalidade\":\"Aula\",\"recursos\":["
                                + recursoSalaLivre() + "]}")),
                invalida("criar reserva sem fim", Ator.SOLICITANTE, Alvo.NENHUM, "fim",
                        id -> json(post("/reservas"), "{\"inicio\":\"2027-03-12T08:00\",\"finalidade\":\"Aula\",\"recursos\":["
                                + recursoSalaLivre() + "]}")),
                invalida("criar reserva sem finalidade", Ator.SOLICITANTE, Alvo.NENHUM, "finalidade",
                        id -> json(post("/reservas"), "{\"inicio\":\"2027-03-12T08:00\",\"fim\":\"2027-03-12T10:00\",\"recursos\":["
                                + recursoSalaLivre() + "]}")),
                invalida("criar reserva sem recursos", Ator.SOLICITANTE, Alvo.NENHUM, "recurso",
                        id -> json(post("/reservas"), "{\"inicio\":\"2027-03-12T08:00\",\"fim\":\"2027-03-12T10:00\",\"finalidade\":\"Aula\"}")),
                invalida("alterar reserva sem fim", Ator.SOLICITANTE, Alvo.PROPRIA, "fim",
                        id -> json(put("/reservas/{id}", id), "{\"inicio\":\"2027-03-10T14:00\"}")),
                invalida("cancelar reserva sem motivo", Ator.SOLICITANTE, Alvo.PROPRIA, "motivo",
                        id -> json(post("/reservas/{id}/cancelamento", id), "{}")),
                invalida("rejeitar reserva sem justificativa", Ator.RESPONSAVEL, Alvo.RESTRITA, "justificativa",
                        id -> json(post("/reservas/{id}/rejeicao", id), "{}")),
                invalida("manutencao sem motivo", Ator.ADMINISTRADOR, Alvo.NENHUM, "motivo",
                        id -> json(post("/manutencoes"), corpoManutencao("2027-03-20T08:00", "2027-03-20T12:00", "null"))),
                invalida("buscar salas sem inicio", Ator.SOLICITANTE, Alvo.NENHUM, "inicio",
                        id -> get("/salas/disponiveis").param("fim", "2027-03-10T10:00")),
                invalida("relatorio sem fim", Ator.RESPONSAVEL, Alvo.NENHUM, "fim",
                        id -> get("/relatorios/utilizacao").param("inicio", "2027-03-01T00:00")),
                invalida("login sem senha", Ator.ANONIMO, Alvo.NENHUM, "senha",
                        id -> json(post("/auth/login"), "{\"email\":\"usuario@example.com\"}")));
    }

    @ParameterizedTest(name = "{0} -> 400 citando ''{3}''")
    @MethodSource("entradasInvalidas")
    @DisplayName("Entrada invalida: 400 com mensagem compreensivel, em ProblemDetail, sem detalhe interno")
    void entradaInvalida(String caso, Ator ator, Alvo alvo, String campo,
                         Function<UUID, MockHttpServletRequestBuilder> requisicao) throws Exception {
        MvcResult resposta = api.perform(comToken(requisicao.apply(alvos.get(alvo)), ator)).andReturn();

        assertEquals(400, resposta.getResponse().getStatus(), () -> caso + ": corpo " + corpo(resposta));
        assertProblemDetailSemDetalheInterno(resposta);
        String detalhe = JsonPath.read(corpo(resposta), "$.detail");
        assertThat(detalhe.toLowerCase(Locale.ROOT)).as("%s: a mensagem deve nomear o campo '%s'", caso, campo)
                .contains(campo);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------------------------------------------------

    private MvcResult executar(Operacao operacao, Ator ator, Alvo alvo) throws Exception {
        if (operacao == Operacao.LOGIN) {
            Ator conta = ator == Ator.ANONIMO ? Ator.SOLICITANTE : ator;
            return api.perform(json(post("/auth/login"), credenciais(conta))).andReturn();
        }
        MockHttpServletRequestBuilder requisicao = operacao.requisicao.apply(alvos.get(alvo));
        if (operacao == Operacao.CADASTRAR_MANUTENCAO) {
            json(requisicao, corpoManutencao("2027-03-20T08:00", "2027-03-20T12:00", "\"troca de piso\""));
        }
        return api.perform(comToken(requisicao, ator)).andReturn();
    }

    private MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder requisicao, Ator ator) throws Exception {
        if (ator != Ator.ANONIMO) {
            requisicao.header("Authorization", "Bearer " + token(ator));
        }
        return requisicao;
    }

    /** Token real: sai do login da API com as credenciais da conta. Falha de login vira falha da celula, com o motivo. */
    private String token(Ator ator) throws Exception {
        String token = tokens.get(ator);
        if (token == null) {
            MvcResult login = api.perform(json(post("/auth/login"), credenciais(ator))).andReturn();
            assertEquals(200, login.getResponse().getStatus(),
                    () -> "login de " + ator + " (" + ator.email + ") falhou, sem token real para o perfil: " + corpo(login));
            token = JsonPath.read(corpo(login), "$.token");
            tokens.put(ator, token);
        }
        return token;
    }

    private UUID criarReservaDoDono(Sala sala, String inicio, String fim, String estadoEsperado) throws Exception {
        MvcResult resposta = api.perform(comToken(json(post("/reservas"),
                "{\"inicio\":\"" + inicio + "\",\"fim\":\"" + fim + "\",\"finalidade\":\"Aula\",\"recursos\":[{\"tipo\":\"SALA\",\"id\":\""
                        + sala.getId() + "\"}]}"), Ator.SOLICITANTE)).andReturn();
        assertEquals(201, resposta.getResponse().getStatus(), () -> "massa: " + corpo(resposta));
        assertEquals(estadoEsperado, JsonPath.read(corpo(resposta), "$.estado"));
        return UUID.fromString(JsonPath.read(corpo(resposta), "$.id"));
    }

    private static void assertProblemDetailSemDetalheInterno(MvcResult resposta) throws Exception {
        String corpo = corpo(resposta);
        int status = resposta.getResponse().getStatus();
        assertThat(resposta.getResponse().getContentType()).as("Content-Type do erro %s: %s", status, corpo)
                .isNotNull();
        assertThat(MediaType.parseMediaType(resposta.getResponse().getContentType())
                .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .as("erro %s deve vir como application/problem+json, veio %s: %s", status,
                        resposta.getResponse().getContentType(), corpo)
                .isTrue();
        assertThat((Integer) JsonPath.read(corpo, "$.status")).as("status do ProblemDetail: %s", corpo).isEqualTo(status);
        assertThat((String) JsonPath.read(corpo, "$.title")).as("title do ProblemDetail: %s", corpo).isNotBlank();
        assertThat((String) JsonPath.read(corpo, "$.detail")).as("detail do ProblemDetail: %s", corpo).isNotBlank();
        assertThat(DETALHE_INTERNO.matcher(corpo).find())
                .as("resposta de erro com stack trace, SQL ou nome de classe interna: %s", corpo).isFalse();
    }

    private static Arguments celula(Operacao operacao, Ator ator, Alvo alvo, int esperado) {
        return Arguments.of(operacao, operacao.rota, ator, alvo, esperado);
    }

    private static Arguments invalida(String caso, Ator ator, Alvo alvo, String campo,
                                      Function<UUID, MockHttpServletRequestBuilder> requisicao) {
        return Arguments.of(caso, ator, alvo, campo, requisicao);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder requisicao, String corpo) {
        return requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private static String credenciais(Ator ator) {
        return "{\"email\":\"" + ator.email + "\",\"senha\":\"" + ator.senha + "\"}";
    }

    private static String corpoReserva(String inicio, String fim) {
        return "{\"inicio\":\"" + inicio + "\",\"fim\":\"" + fim + "\",\"finalidade\":\"Aula\",\"recursos\":["
                + recursoSalaLivre() + "]}";
    }

    private static String recursoSalaLivre() {
        return "{\"tipo\":\"SALA\",\"id\":\"" + SALA_LIVRE.getId() + "\"}";
    }

    private static String corpoManutencao(String inicio, String fim, String motivoJson) {
        return "{\"tipoRecurso\":\"SALA\",\"recursoId\":\"" + SALA_LIVRE.getId() + "\",\"inicio\":\"" + inicio
                + "\",\"fim\":\"" + fim + "\",\"motivo\":" + motivoJson + "}";
    }

    private static String corpo(MvcResult resposta) {
        try {
            return resposta.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "<corpo ilegivel>";
        }
    }
}
