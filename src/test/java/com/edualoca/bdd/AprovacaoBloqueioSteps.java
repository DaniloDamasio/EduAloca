package com.edualoca.bdd;

import com.edualoca.api.seguranca.TokenService;
import com.edualoca.domain.auditoria.AuditoriaEvento;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Passos de aprovacao-bloqueio.feature.
 *
 * <p>Os passos "Quando" e os "Entao" que leem o estado da reserva passam pela API HTTP (MockMvc), cada um com o
 * token Bearer do perfil que executa a acao. A preparacao ("Dado") de cadastros sem endpoint (sala, usuario e
 * token) usa os servicos de dominio. A verificacao de auditoria le o registro append-only, porque nao ha
 * endpoint de consulta de auditoria nesta fase.
 */
public class AprovacaoBloqueioSteps {

    private static final int ANO = 2026;
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String SALA_DE_APOIO = "Sala de apoio";
    private static final String SALA_RESTRITA_DE_APOIO = "Laboratório de apoio";
    private static final String SOLICITANTE = "Solicitante";
    private static final String RESPONSAVEL = "Responsável";
    private static final String ADMINISTRADOR = "Administrador";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private WebApplicationContext contexto;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private Clock relogio;

    private MockMvc api;
    private final Map<String, Usuario> usuarios = new HashMap<>();
    private final Map<String, String> tokens = new HashMap<>();
    private final Map<String, Sala> salas = new HashMap<>();
    private final Map<String, UUID> reservaPorDono = new HashMap<>();
    private final List<MvcResult> tentativas = new ArrayList<>();
    private UUID reservaAtual;
    private String donoReservaAtual;
    private MvcResult ultimaResposta;
    private String justificativaInformada;
    private Usuario atorDaTransicao;
    private int proximoDiaLivre;

    @Before
    public void prepararCenario() {
        Banco.resetParaTeste();
        api = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    // ------------------------------------------------------------------ Dado: cadastros

    @Dado("^a sala \"([^\"]+)\" marcada como (restrita|não restrita)$")
    public void aSalaMarcadaComo(String nome, String restricao) {
        cadastrarSala(nome, restricao.equals("restrita"));
    }

    @Dado("^a sala restrita \"([^\"]+)\"$")
    public void aSalaRestrita(String nome) {
        cadastrarSala(nome, true);
    }

    @Dado("^a sala \"([^\"]+)\" não restrita$")
    public void aSalaNaoRestrita(String nome) {
        cadastrarSala(nome, false);
    }

    @Dado("^o usuário \"([^\"]+)\" com perfil (SOLICITANTE|RESPONSAVEL|ADMINISTRADOR)$")
    public void oUsuarioComPerfil(String nome, String perfil) {
        cadastrarUsuario(nome, Perfil.valueOf(perfil));
    }

    @Dado("^uma manutenção da \"([^\"]+)\" de (\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}) até (\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}), motivo \"([^\"]*)\"$")
    public void umaManutencao(String sala, String inicio, String fim, String motivo) throws Exception {
        MvcResult r = cadastrarManutencao(sala, LocalDateTime.parse(inicio, DATA_HORA), LocalDateTime.parse(fim, DATA_HORA), motivo);
        assertEquals(201, r.getResponse().getStatus(), corpo(r));
    }

    // ------------------------------------------------------------------ Dado: reservas no estado de origem

    @Dado("^uma reserva de \"([^\"]+)\"(?: \\((SOLICITANTE)\\))? na \"([^\"]+)\" no estado SOLICITADA$")
    public void umaReservaSolicitada(String dono, String perfil, String sala) throws Exception {
        if (perfil != null) {
            cadastrarUsuario(dono, Perfil.valueOf(perfil));
        }
        prepararReserva(dono, sala, proximoHorarioLivre(), EstadoReserva.SOLICITADA);
    }

    @Dado("^uma reserva APROVADA de \"([^\"]+)\"$")
    public void umaReservaAprovadaDe(String dono) throws Exception {
        prepararReserva(dono, salaDeApoio(false), proximoHorarioLivre(), EstadoReserva.APROVADA);
    }

    @Dado("^uma reserva APROVADA da \"([^\"]+)\" em (\\d{2}/\\d{2}) (\\d{2}:\\d{2}) a (\\d{2}:\\d{2})$")
    public void umaReservaAprovadaDaSala(String sala, String dia, String inicio, String fim) throws Exception {
        prepararReserva(SOLICITANTE, sala, intervalo(dia, inicio, fim), EstadoReserva.APROVADA);
    }

    @Dado("^uma reserva no estado (SOLICITADA|APROVADA|EM_USO)$")
    public void umaReservaNoEstado(String estado) throws Exception {
        EstadoReserva origem = EstadoReserva.valueOf(estado);
        boolean restrita = origem == EstadoReserva.SOLICITADA;
        EstadoReserva aoCriar = restrita ? EstadoReserva.SOLICITADA : EstadoReserva.APROVADA;
        prepararReserva(SOLICITANTE, salaDeApoio(restrita), proximoHorarioLivre(), aoCriar);
        if (origem == EstadoReserva.EM_USO) {
            // Nao ha endpoint para APROVADA -> EM_USO: o gatilho esta em aberto (P-07).
            Banco.reservas.get(reservaAtual).setEstado(EstadoReserva.EM_USO);
        }
    }

    // ------------------------------------------------------------------ Quando: chamadas a API

    @Quando("^\"([^\"]+)\" cria uma reserva da \"([^\"]+)\" de (\\d{2}/\\d{2}) (\\d{2}:\\d{2}) a (\\d{2}:\\d{2})(?: com finalidade \"([^\"]*)\")?$")
    public void criaUmaReserva(String usuario, String sala, String dia, String inicio, String fim, String finalidade) throws Exception {
        LocalDateTime[] horario = intervalo(dia, inicio, fim);
        ultimaResposta = criarReserva(usuario, sala, horario, finalidade == null ? "Aula" : finalidade);
        if (ultimaResposta.getResponse().getStatus() == 201) {
            reservaAtual = UUID.fromString(JsonPath.read(corpo(ultimaResposta), "$.id"));
            donoReservaAtual = usuario;
        }
    }

    @Quando("^\"([^\"]+)\" aprova a (?:reserva|própria reserva)$")
    public void aprovaAReserva(String usuario) throws Exception {
        ultimaResposta = chamar(post("/reservas/{id}/aprovacao", reservaAtual), usuario);
    }

    @Quando("^\"([^\"]+)\" aprova a reserva de \"([^\"]+)\"$")
    public void aprovaAReservaDe(String usuario, String dono) throws Exception {
        ultimaResposta = chamar(post("/reservas/{id}/aprovacao", reservaPorDono.get(dono)), usuario);
    }

    @Quando("^\"([^\"]+)\" rejeita a (?:reserva|própria reserva) com justificativa \"(.*)\"$")
    public void rejeitaAReserva(String usuario, String justificativa) throws Exception {
        justificativaInformada = valorDoExemplo(justificativa);
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("justificativa", justificativaInformada);
        ultimaResposta = chamar(post("/reservas/{id}/rejeicao", reservaAtual), usuario, corpo);
    }

    @Quando("^\"([^\"]+)\" cancela a reserva sem informar motivo$")
    public void cancelaSemMotivo(String usuario) throws Exception {
        ultimaResposta = chamar(post("/reservas/{id}/cancelamento", reservaAtual), usuario, Map.of());
    }

    @Quando("^\"([^\"]+)\" cancela a reserva de \"([^\"]+)\" com motivo \"([^\"]*)\"$")
    public void cancelaAReservaDe(String usuario, String dono, String motivo) throws Exception {
        ultimaResposta = chamar(post("/reservas/{id}/cancelamento", reservaPorDono.get(dono)), usuario, Map.of("motivo", motivo));
    }

    @Quando("^(Responsável|Solicitante) executa \"(aprovar|rejeitar \\(com justificativa\\)|cancelar \\(com motivo\\))\"$")
    public void atorExecuta(String ator, String acao) throws Exception {
        atorDaTransicao = usuario(ator);
        ultimaResposta = switch (acao) {
            case "aprovar" -> chamar(post("/reservas/{id}/aprovacao", reservaAtual), ator);
            case "rejeitar (com justificativa)" ->
                    chamar(post("/reservas/{id}/rejeicao", reservaAtual), ator, Map.of("justificativa", "Laboratório reservado para banca"));
            default -> chamar(post("/reservas/{id}/cancelamento", reservaAtual), ator, Map.of("motivo", "Aula remarcada"));
        };
    }

    @Quando("^qualquer usuário tenta excluí-la$")
    public void qualquerUsuarioTentaExcluir() throws Exception {
        for (String usuario : List.of(donoReservaAtual, RESPONSAVEL, ADMINISTRADOR)) {
            tentativas.add(chamar(delete("/reservas/{id}", reservaAtual), usuario));
        }
    }

    @Quando("^\"([^\"]+)\" busca salas disponíveis de (\\d{2}/\\d{2}) (\\d{2}:\\d{2}) a (\\d{2}:\\d{2})$")
    public void buscaSalasDisponiveis(String usuario, String dia, String inicio, String fim) throws Exception {
        LocalDateTime[] horario = intervalo(dia, inicio, fim);
        ultimaResposta = chamar(get("/salas/disponiveis")
                .param("inicio", horario[0].toString())
                .param("fim", horario[1].toString()), usuario);
    }

    @Quando("^o Administrador cadastra manutenção da \"([^\"]+)\" de (\\d{2}/\\d{2}) (\\d{2}:\\d{2}) a (\\d{2}:\\d{2}) sem confirmar$")
    public void administradorCadastraManutencaoSemConfirmar(String sala, String dia, String inicio, String fim) throws Exception {
        LocalDateTime[] horario = intervalo(dia, inicio, fim);
        ultimaResposta = cadastrarManutencao(sala, horario[0], horario[1], "Manutenção preventiva");
    }

    // ------------------------------------------------------------------ Entao

    @Entao("^a reserva é criada no estado (SOLICITADA|APROVADA)$")
    public void aReservaECriadaNoEstado(String estado) throws Exception {
        assertEquals(201, ultimaResposta.getResponse().getStatus(), corpo(ultimaResposta));
        assertEquals(estado, JsonPath.read(corpo(ultimaResposta), "$.estado"));
        assertEquals(estado, estadoConsultadoNaApi());
    }

    @Entao("^a reserva passa ao estado ([A-Z_]+)$")
    public void aReservaPassaAoEstado(String estado) throws Exception {
        assertEquals(200, ultimaResposta.getResponse().getStatus(), corpo(ultimaResposta));
        assertEquals(estado, JsonPath.read(corpo(ultimaResposta), "$.estado"));
        assertEquals(estado, estadoConsultadoNaApi());
    }

    @Entao("^a reserva (?:permanece no estado|permanece|continua) ([A-Z_]+)$")
    public void aReservaPermanece(String estado) throws Exception {
        assertEquals(estado, estadoConsultadoNaApi());
    }

    @Entao("^a reserva continua existindo$")
    public void aReservaContinuaExistindo() throws Exception {
        MvcResult r = chamar(get("/reservas/{id}", reservaAtual), donoReservaAtual);
        assertEquals(200, r.getResponse().getStatus(), corpo(r));
        assertTrue(Banco.reservas.containsKey(reservaAtual));
    }

    @Entao("^a operação é recusada com HTTP (\\d{3})$")
    public void aOperacaoERecusadaCom(int status) throws Exception {
        assertEquals(status, ultimaResposta.getResponse().getStatus(), corpo(ultimaResposta));
    }

    @Entao("^a operação é recusada com HTTP (\\d{3}) ou (\\d{3})$")
    public void aOperacaoERecusadaComUmDosStatus(int status, int alternativo) throws Exception {
        assertFalse(tentativas.isEmpty());
        for (MvcResult r : tentativas) {
            int obtido = r.getResponse().getStatus();
            assertTrue(obtido == status || obtido == alternativo, "HTTP " + obtido + ": " + corpo(r));
        }
    }

    @Entao("^o cadastro é recusado$")
    public void oCadastroERecusado() throws Exception {
        int status = ultimaResposta.getResponse().getStatus();
        assertTrue(status >= 400 && status < 500, "HTTP " + status + ": " + corpo(ultimaResposta));
        Sala sala = salas.get("Lab 3");
        assertTrue(Banco.manutencoes.values().stream()
                .noneMatch(m -> m.getRecurso() == sala && m.getInicio().getDayOfMonth() == 12));
    }

    @Entao("^um evento de auditoria \"([A-Z_]+)\" é registrado com autor \"([^\"]+)\"$")
    public void umEventoDeAuditoriaERegistrado(String acao, String autor) {
        List<AuditoriaEvento> eventos = eventosDaReservaAtual().stream().filter(e -> e.getAcao().equals(acao)).toList();
        assertEquals(1, eventos.size(), "eventos " + acao + ": " + eventos.size());
        assertEquals(usuario(autor).getId(), eventos.get(0).getAutorId());
    }

    @Entao("^nenhum evento de auditoria \"([A-Z_]+)\" é registrado$")
    public void nenhumEventoDeAuditoria(String acao) {
        assertTrue(eventosDaReservaAtual().stream().noneMatch(e -> e.getAcao().equals(acao)));
    }

    @Entao("^exatamente um evento de auditoria é registrado para essa transição, com o ator e o instante$")
    public void exatamenteUmEventoDeAuditoria() {
        List<AuditoriaEvento> transicoes = eventosDaReservaAtual().stream()
                .filter(e -> !e.getAcao().equals("CRIACAO"))
                .toList();
        assertEquals(1, transicoes.size(), "eventos de transicao: " + transicoes.size());
        assertEquals(atorDaTransicao.getId(), transicoes.get(0).getAutorId());
        assertEquals(LocalDateTime.now(relogio), transicoes.get(0).getDataHora());
    }

    @Entao("^a justificativa fica visível para \"([^\"]+)\"$")
    public void aJustificativaFicaVisivelPara(String usuario) throws Exception {
        MvcResult r = chamar(get("/reservas/{id}", reservaAtual), usuario);
        assertEquals(200, r.getResponse().getStatus(), corpo(r));
        assertEquals(justificativaInformada, JsonPath.read(corpo(r), "$.justificativaRejeicao"));
    }

    @Entao("^a resposta de erro explica em linguagem natural que só Responsável ou Administrador aprovam$")
    public void aRespostaExplicaQuemAprova() throws Exception {
        String mensagem = semAcento(JsonPath.read(corpo(ultimaResposta), "$.mensagem"));
        assertTrue(mensagem.contains("responsavel") && mensagem.contains("administrador") && mensagem.contains("aprov"), mensagem);
    }

    @Entao("^a resposta não contém stacktrace nem nome de classe interna$")
    public void aRespostaNaoContemDetalhesInternos() throws Exception {
        String corpo = corpo(ultimaResposta);
        for (String vazamento : List.of("Exception", "com.edualoca", "java.", "\tat ", "trace", "RegraNegocio")) {
            assertFalse(corpo.contains(vazamento), "resposta expoe '" + vazamento + "': " + corpo);
        }
    }

    @Entao("^a mensagem informa que o recurso está em manutenção no intervalo$")
    public void aMensagemInformaManutencao() throws Exception {
        String mensagem = semAcento(JsonPath.read(corpo(ultimaResposta), "$.mensagem"));
        assertTrue(mensagem.contains("manutencao") && mensagem.contains("intervalo"), mensagem);
    }

    @Entao("^nenhuma reserva é persistida$")
    public void nenhumaReservaEPersistida() {
        assertTrue(Banco.reservas.isEmpty(), "reservas persistidas: " + Banco.reservas.size());
    }

    @Entao("^\"([^\"]+)\" não aparece no resultado$")
    public void naoApareceNoResultado(String sala) throws Exception {
        assertEquals(200, ultimaResposta.getResponse().getStatus(), corpo(ultimaResposta));
        List<String> nomes = JsonPath.read(corpo(ultimaResposta), "$[*].nome");
        assertFalse(nomes.isEmpty(), "a busca nao devolveu nenhuma sala; o teste nao distinguiria o filtro");
        assertFalse(nomes.contains(sala), "resultado: " + nomes);
    }

    // ------------------------------------------------------------------ apoio

    private void cadastrarSala(String nome, boolean restrita) {
        salas.put(nome, new CadastroRecursoService(relogio).cadastrarSala(nome, "laboratorio", 30, "Bloco B", restrita));
    }

    private String salaDeApoio(boolean restrita) {
        String nome = restrita ? SALA_RESTRITA_DE_APOIO : SALA_DE_APOIO;
        if (!salas.containsKey(nome)) {
            cadastrarSala(nome, restrita);
        }
        return nome;
    }

    private Usuario cadastrarUsuario(String nome, Perfil perfil) {
        Usuario usuario = new Usuario(UUID.randomUUID(), nome + "@edualoca.test", "hash-de-teste", perfil);
        usuarios.put(nome, usuario);
        tokens.put(nome, tokenService.emitir(usuario));
        return usuario;
    }

    /** Usuario citado no cenario sem passo de cadastro ("beto", "o Administrador"...): perfil pelo papel citado. */
    private Usuario usuario(String nome) {
        Usuario existente = usuarios.get(nome);
        if (existente != null) {
            return existente;
        }
        Perfil perfil = switch (nome) {
            case RESPONSAVEL -> Perfil.RESPONSAVEL;
            case ADMINISTRADOR -> Perfil.ADMINISTRADOR;
            default -> Perfil.SOLICITANTE;
        };
        return cadastrarUsuario(nome, perfil);
    }

    private void prepararReserva(String dono, String sala, LocalDateTime[] horario, EstadoReserva estadoEsperado) throws Exception {
        MvcResult r = criarReserva(dono, sala, horario, "Aula");
        assertEquals(201, r.getResponse().getStatus(), "preparacao da reserva: " + corpo(r));
        assertEquals(estadoEsperado.name(), JsonPath.read(corpo(r), "$.estado"));
        reservaAtual = UUID.fromString(JsonPath.read(corpo(r), "$.id"));
        donoReservaAtual = dono;
        reservaPorDono.put(dono, reservaAtual);
    }

    private MvcResult criarReserva(String usuario, String sala, LocalDateTime[] horario, String finalidade) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("inicio", horario[0].toString());
        corpo.put("fim", horario[1].toString());
        corpo.put("finalidade", finalidade);
        corpo.put("recursos", List.of(Map.of("tipo", "SALA", "id", salas.get(sala).getId().toString(), "quantidade", 1)));
        return chamar(post("/reservas"), usuario, corpo);
    }

    private MvcResult cadastrarManutencao(String sala, LocalDateTime inicio, LocalDateTime fim, String motivo) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("tipoRecurso", "SALA");
        corpo.put("recursoId", salas.get(sala).getId().toString());
        corpo.put("inicio", inicio.toString());
        corpo.put("fim", fim.toString());
        corpo.put("motivo", motivo);
        return chamar(post("/manutencoes"), ADMINISTRADOR, corpo);
    }

    private MvcResult chamar(MockHttpServletRequestBuilder requisicao, String usuario) throws Exception {
        return executar(autenticada(requisicao, usuario));
    }

    private MvcResult chamar(MockHttpServletRequestBuilder requisicao, String usuario, Map<String, ?> corpo) throws Exception {
        return executar(autenticada(requisicao, usuario)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(corpo)));
    }

    private MockHttpServletRequestBuilder autenticada(MockHttpServletRequestBuilder requisicao, String usuario) {
        usuario(usuario);
        return requisicao.header("Authorization", "Bearer " + tokens.get(usuario));
    }

    private MvcResult executar(RequestBuilder requisicao) throws Exception {
        return api.perform(requisicao).andReturn();
    }

    private String estadoConsultadoNaApi() throws Exception {
        MvcResult r = chamar(get("/reservas/{id}", reservaAtual), donoReservaAtual);
        assertEquals(200, r.getResponse().getStatus(), corpo(r));
        return JsonPath.read(corpo(r), "$.estado");
    }

    private List<AuditoriaEvento> eventosDaReservaAtual() {
        Reserva reserva = Banco.reservas.get(reservaAtual);
        assertTrue(reserva != null, "reserva do cenario nao foi persistida");
        return Banco.auditoria.stream().filter(e -> e.getEntidadeId().equals(reservaAtual)).toList();
    }

    private LocalDateTime[] proximoHorarioLivre() {
        LocalDate dia = LocalDate.of(ANO, 4, 1).plusDays(proximoDiaLivre++);
        return new LocalDateTime[]{dia.atTime(10, 0), dia.atTime(12, 0)};
    }

    private static LocalDateTime[] intervalo(String diaMes, String inicio, String fim) {
        LocalDate dia = LocalDate.parse(diaMes + "/" + ANO, DIA_MES);
        return new LocalDateTime[]{dia.atTime(LocalTime.parse(inicio)), dia.atTime(LocalTime.parse(fim))};
    }

    /** Valor da tabela de Exemplos: "(nula)" e ausencia; aspas externas delimitam texto vazio ou em branco. */
    private static String valorDoExemplo(String bruto) {
        if (bruto.equals("(nula)")) {
            return null;
        }
        if (bruto.length() >= 2 && bruto.startsWith("\"") && bruto.endsWith("\"")) {
            return bruto.substring(1, bruto.length() - 1);
        }
        return bruto;
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static String corpo(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
