package com.edualoca.persistencia;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.HistoricoReservaRepositorio;
import com.edualoca.domain.reserva.RegistroHistoricoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RF-27 com PostgreSQL real: a mudanca de estado da reserva e o registro de historico sao gravados na mesma
 * transacao. Uma falha depois de o historico ja ter sido escrito desfaz as duas escritas, sem deixar auditoria
 * orfa nem estado sem auditoria. O resultado e conferido por SQL, nao pelo objeto em memoria.
 */
@Testcontainers
class HistoricoReservaTransacaoIT {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Instant AGORA = Instant.parse("2027-03-08T13:00:00Z");
    private static final LocalDateTime INICIO = LocalDateTime.of(2027, 3, 15, 8, 0);
    private static final LocalDateTime FIM = INICIO.plusHours(2);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactionTemplate;
    private static ReservaRepositorioJdbc reservas;
    private static HistoricoReservaRepositorioJdbc historico;

    private final Clock relogio = Clock.fixed(AGORA, FUSO);
    private final Usuario solicitante = new Usuario(UUID.randomUUID(), "sol@edualoca.test", "h", Perfil.SOLICITANTE);
    private final Usuario responsavel = new Usuario(UUID.randomUUID(), "resp@edualoca.test", "h", Perfil.RESPONSAVEL);
    private final Sala salaRestrita = new Sala(UUID.randomUUID(), "Lab Quimica", "laboratorio", 20, "Bloco B", true);

    @BeforeAll
    static void criarEsquema() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("db/postgres/schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        reservas = new ReservaRepositorioJdbc(jdbc, transactionTemplate, FUSO);
        historico = new HistoricoReservaRepositorioJdbc(jdbc);
    }

    @BeforeEach
    void limpar() {
        jdbc.execute("TRUNCATE reserva_historico, reserva_recurso, reserva");
    }

    @Test
    @DisplayName("Aprovacao confirmada grava no banco o novo estado e o registro com usuario, instante do Clock, estados e motivo")
    void aprovacaoGravaEstadoEHistorico() {
        ReservaService servico = servico(historico);
        Reserva reserva = servico.criar(solicitante, INICIO, FIM, "Aula pratica", List.of(new ReservaRecurso(salaRestrita, 1)));

        servico.aprovar(responsavel, reserva.getId());

        assertEquals("APROVADA", estadoNoBanco(reserva.getId()));
        List<Map<String, Object>> linhas = historicoNoBanco(reserva.getId());
        assertEquals(2, linhas.size());
        Map<String, Object> aprovacao = linhas.get(1);
        assertAll(
                () -> assertEquals(responsavel.getId(), aprovacao.get("usuario_id")),
                () -> assertEquals(AGORA, ((Timestamp) aprovacao.get("instante")).toInstant()),
                () -> assertEquals("SOLICITADA", aprovacao.get("estado_anterior")),
                () -> assertEquals("APROVADA", aprovacao.get("estado_novo")),
                () -> assertEquals("Aprovacao da solicitacao", aprovacao.get("motivo")),
                () -> assertNull(linhas.get(0).get("estado_anterior")),
                () -> assertEquals("SOLICITADA", linhas.get(0).get("estado_novo")));
        assertEquals(List.of(EstadoReserva.SOLICITADA, EstadoReserva.APROVADA),
                servico.consultarHistorico(responsavel, reserva.getId()).stream()
                        .map(RegistroHistoricoReserva::estadoNovo).toList(),
                "a consulta pelo repositorio JDBC le o que foi gravado, na ordem");
    }

    @Test
    @DisplayName("Falha depois de gravar o historico da aprovacao: rollback desfaz estado e historico, sem auditoria orfa")
    void rollbackNaTransicaoNaoDeixaAuditoriaOrfa() {
        Reserva reserva = servico(historico).criar(solicitante, INICIO, FIM, "Aula pratica",
                List.of(new ReservaRecurso(salaRestrita, 1)));
        FalhaAposGravar falha = new FalhaAposGravar(r -> r.estadoNovo() == EstadoReserva.APROVADA);
        ReservaService servicoComFalha = servico(falha);

        assertThrows(IllegalStateException.class, () -> servicoComFalha.aprovar(responsavel, reserva.getId()));

        assertAll("dentro da transacao as duas escritas aconteceram na mesma conexao",
                () -> assertEquals("APROVADA", falha.estadoVistoDentroDaTransacao),
                () -> assertEquals(2, falha.historicoVistoDentroDaTransacao));
        assertAll("depois do rollback nenhuma das duas ficou",
                () -> assertEquals("SOLICITADA", estadoNoBanco(reserva.getId())),
                () -> assertEquals(List.of("SOLICITADA"), historicoNoBanco(reserva.getId()).stream()
                        .map(l -> l.get("estado_novo")).toList()),
                () -> assertEquals(EstadoReserva.SOLICITADA, reserva.getEstado(), "objeto em memoria tambem volta"));
    }

    @Test
    @DisplayName("Falha depois de gravar o historico da criacao: nenhuma reserva, recurso ou historico fica no banco nem no cache")
    void rollbackNaCriacaoNaoDeixaNada() {
        FalhaAposGravar falha = new FalhaAposGravar(r -> r.estadoAnterior() == null);

        assertThrows(IllegalStateException.class, () -> servico(falha).criar(solicitante, INICIO, FIM, "Aula pratica",
                List.of(new ReservaRecurso(salaRestrita, 1))));

        assertEquals(1, falha.historicoVistoDentroDaTransacao, "o historico chegou a ser escrito antes da falha");
        assertAll(
                () -> assertEquals(0, contar("reserva")),
                () -> assertEquals(0, contar("reserva_recurso")),
                () -> assertEquals(0, contar("reserva_historico")),
                () -> assertTrue(Banco.reservas.isEmpty(), "reserva desfeita nao entra no cache em memoria"));
        assertDoesNotThrow(() -> servico(historico).criar(solicitante, INICIO, FIM, "Aula pratica",
                List.of(new ReservaRecurso(salaRestrita, 1))), "o horario continua livre");
    }

    @Test
    @DisplayName("O banco recusa UPDATE e DELETE direto em reserva_historico (registro append-only)")
    void bancoRecusaAlterarOuApagarHistorico() {
        Reserva reserva = servico(historico).criar(solicitante, INICIO, FIM, "Aula pratica",
                List.of(new ReservaRecurso(salaRestrita, 1)));
        assertEquals(1, historicoNoBanco(reserva.getId()).size());

        assertAll(
                () -> assertThrows(DataAccessException.class, () -> jdbc.update(
                        "UPDATE reserva_historico SET motivo = 'adulterado' WHERE reserva_id = ?", reserva.getId())),
                () -> assertThrows(DataAccessException.class, () -> jdbc.update(
                        "DELETE FROM reserva_historico WHERE reserva_id = ?", reserva.getId())));
        assertEquals("Criacao: recurso restrito aguarda aprovacao", historicoNoBanco(reserva.getId()).get(0).get("motivo"));
    }

    private ReservaService servico(HistoricoReservaRepositorio repositorioHistorico) {
        return new ReservaService(relogio, reservas, repositorioHistorico, new TransacaoJdbc(transactionTemplate));
    }

    private static String estadoNoBanco(UUID reservaId) {
        return jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, reservaId);
    }

    private static List<Map<String, Object>> historicoNoBanco(UUID reservaId) {
        return jdbc.queryForList("""
                SELECT usuario_id, instante, estado_anterior, estado_novo, motivo
                  FROM reserva_historico WHERE reserva_id = ? ORDER BY sequencia""", reservaId);
    }

    private static int contar(String tabela) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabela, Integer.class);
    }

    /**
     * Grava o registro de verdade e, ainda dentro da transacao, olha o banco pela mesma conexao; depois falha.
     * Simula um erro que acontece apos a auditoria ja ter sido escrita.
     */
    private static final class FalhaAposGravar implements HistoricoReservaRepositorio {

        private final Predicate<RegistroHistoricoReserva> quandoFalhar;
        String estadoVistoDentroDaTransacao;
        Integer historicoVistoDentroDaTransacao;

        FalhaAposGravar(Predicate<RegistroHistoricoReserva> quandoFalhar) {
            this.quandoFalhar = quandoFalhar;
        }

        @Override
        public void registrar(RegistroHistoricoReserva registro) {
            historico.registrar(registro);
            if (quandoFalhar.test(registro)) {
                estadoVistoDentroDaTransacao = jdbc.query("SELECT estado FROM reserva WHERE id = ?",
                        rs -> rs.next() ? rs.getString(1) : null, registro.reservaId());
                historicoVistoDentroDaTransacao = jdbc.queryForObject(
                        "SELECT count(*) FROM reserva_historico WHERE reserva_id = ?", Integer.class, registro.reservaId());
                throw new IllegalStateException("falha simulada apos gravar o historico");
            }
        }

        @Override
        public List<RegistroHistoricoReserva> listarPorReserva(UUID reservaId) {
            return historico.listarPorReserva(reservaId);
        }
    }
}
