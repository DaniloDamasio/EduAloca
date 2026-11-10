package com.edualoca.persistencia;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaRepositorio;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Reservas gravadas no PostgreSQL. A exclusividade recurso/intervalo e garantida pela constraint
 * {@code ex_reserva_recurso_sem_sobreposicao} (EXCLUDE USING gist sobre tstzrange, ADR-009); a violacao
 * vira 409. A consulta {@link #existeConflito} continua existindo para dar a resposta cedo no caso comum.
 *
 * <p>Os demais servicos (relatorio, pesquisa, retirada) ainda leem {@link Banco#reservas}, por isso a
 * reserva gravada tambem e mantida nesse cache em memoria apos o commit (inclusive o de uma transacao externa).
 */
public class ReservaRepositorioJdbc implements ReservaRepositorio {

    /** SQLSTATE do PostgreSQL para violacao de constraint de exclusao. */
    static final String EXCLUSION_VIOLATION = "23P01";
    static final String MENSAGEM_CONFLITO = "Recurso indisponivel: sobreposicao com reserva ativa";

    private static final UUID NENHUMA = new UUID(0L, 0L);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final ZoneId fusoHorario;

    public ReservaRepositorioJdbc(JdbcTemplate jdbc, TransactionTemplate transacao, ZoneId fusoHorario) {
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.fusoHorario = fusoHorario;
    }

    @Override
    public void inserir(Reserva reserva) {
        traduzindoSobreposicao(() -> transacao.executeWithoutResult(status -> {
            jdbc.update("""
                            INSERT INTO reserva (id, solicitante_id, inicio, fim, finalidade, estado, justificativa_rejeicao)
                            VALUES (?, ?, ?, ?, ?, ?, ?)""",
                    reserva.getId(), reserva.getSolicitanteId(), ts(reserva.getInicio()), ts(reserva.getFim()),
                    reserva.getFinalidade(), reserva.getEstado().name(), reserva.getJustificativaRejeicao());
            for (ReservaRecurso rr : reserva.getRecursos()) {
                jdbc.update("""
                                INSERT INTO reserva_recurso (reserva_id, tipo_recurso, recurso_id, quantidade, periodo, ativa)
                                VALUES (?, ?, ?, ?, tstzrange(?, ?, '[)'), ?)""",
                        reserva.getId(), tipo(rr.getRecurso()), id(rr.getRecurso()), rr.getQuantidade(),
                        instante(reserva.getInicio()), instante(reserva.getFim()), reserva.getEstado().isAtiva());
            }
        }));
        aposCommit(() -> Banco.reservas.put(reserva.getId(), reserva));
    }

    @Override
    public Reserva buscar(UUID id) {
        return Banco.reservas.get(id);
    }

    @Override
    public void atualizar(Reserva reserva) {
        traduzindoSobreposicao(() -> transacao.executeWithoutResult(status -> {
            jdbc.update("UPDATE reserva SET inicio = ?, fim = ?, estado = ?, justificativa_rejeicao = ? WHERE id = ?",
                    ts(reserva.getInicio()), ts(reserva.getFim()), reserva.getEstado().name(),
                    reserva.getJustificativaRejeicao(), reserva.getId());
            jdbc.update("UPDATE reserva_recurso SET periodo = tstzrange(?, ?, '[)'), ativa = ? WHERE reserva_id = ?",
                    instante(reserva.getInicio()), instante(reserva.getFim()), reserva.getEstado().isAtiva(), reserva.getId());
        }));
    }

    @Override
    public boolean existeConflito(Object recurso, LocalDateTime inicio, LocalDateTime fim, UUID reservaIgnorada) {
        Boolean existe = jdbc.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM reserva_recurso
                             WHERE tipo_recurso = ? AND recurso_id = ? AND ativa AND reserva_id <> ?
                               AND periodo && tstzrange(?, ?, '[)'))""",
                Boolean.class, tipo(recurso), id(recurso), reservaIgnorada == null ? NENHUMA : reservaIgnorada,
                instante(inicio), instante(fim));
        return Boolean.TRUE.equals(existe);
    }

    /**
     * Dentro de uma transacao externa (ex.: reserva + historico), so publica no cache depois do commit, para que
     * um rollback nao deixe no cache uma reserva que nao existe no banco.
     */
    private static void aposCommit(Runnable acao) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    acao.run();
                }
            });
        } else {
            acao.run();
        }
    }

    private static void traduzindoSobreposicao(Runnable gravacao) {
        try {
            gravacao.run();
        } catch (DataIntegrityViolationException e) {
            if (e.getMostSpecificCause() instanceof SQLException sql && EXCLUSION_VIOLATION.equals(sql.getSQLState())) {
                throw new RegraNegocioException(409, MENSAGEM_CONFLITO);
            }
            throw e;
        }
    }

    static String tipo(Object recurso) {
        return switch (recurso) {
            case Sala s -> "SALA";
            case Professor p -> "PROFESSOR";
            case Material m -> "MATERIAL";
            default -> throw new IllegalArgumentException("Tipo de recurso nao persistivel: " + recurso.getClass().getSimpleName());
        };
    }

    static UUID id(Object recurso) {
        return switch (recurso) {
            case Sala s -> s.getId();
            case Professor p -> p.getId();
            case Material m -> m.getId();
            default -> throw new IllegalArgumentException("Tipo de recurso nao persistivel: " + recurso.getClass().getSimpleName());
        };
    }

    private static Timestamp ts(LocalDateTime dataHora) {
        return Timestamp.valueOf(dataHora);
    }

    /** O dominio usa hora local da instituicao; o range guarda o instante absoluto (sem ambiguidade de horario de verao). */
    private OffsetDateTime instante(LocalDateTime dataHora) {
        return dataHora.atZone(fusoHorario).toOffsetDateTime();
    }
}
