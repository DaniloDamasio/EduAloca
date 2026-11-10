package com.edualoca.persistencia;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaRepositorio;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reservas gravadas no PostgreSQL; a verificacao de conflito consulta o banco. Os demais servicos
 * (relatorio, pesquisa, retirada) ainda leem {@link Banco#reservas}, por isso a reserva gravada tambem e
 * mantida nesse cache em memoria apos o commit.
 */
public class ReservaRepositorioJdbc implements ReservaRepositorio {

    private static final UUID NENHUMA = new UUID(0L, 0L);

    /** Nomes de constantes do enum (nao vem de entrada do usuario), embutidos com seguranca no SQL. */
    private static final String ESTADOS_ATIVOS = Arrays.stream(EstadoReserva.values())
            .filter(EstadoReserva::isAtiva)
            .map(e -> "'" + e.name() + "'")
            .collect(Collectors.joining(", "));

    private static final String SQL_CONFLITO = """
            SELECT EXISTS (
                SELECT 1
                  FROM reserva r
                  JOIN reserva_recurso rr ON rr.reserva_id = r.id
                 WHERE rr.tipo_recurso = ?
                   AND rr.recurso_id = ?
                   AND r.id <> ?
                   AND r.estado IN (%s)
                   AND r.inicio < ?
                   AND ? < r.fim)
            """.formatted(ESTADOS_ATIVOS);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;

    public ReservaRepositorioJdbc(JdbcTemplate jdbc, TransactionTemplate transacao) {
        this.jdbc = jdbc;
        this.transacao = transacao;
    }

    @Override
    public void inserir(Reserva reserva) {
        transacao.executeWithoutResult(status -> {
            jdbc.update("""
                            INSERT INTO reserva (id, solicitante_id, inicio, fim, finalidade, estado, justificativa_rejeicao)
                            VALUES (?, ?, ?, ?, ?, ?, ?)""",
                    reserva.getId(), reserva.getSolicitanteId(), ts(reserva.getInicio()), ts(reserva.getFim()),
                    reserva.getFinalidade(), reserva.getEstado().name(), reserva.getJustificativaRejeicao());
            for (ReservaRecurso rr : reserva.getRecursos()) {
                jdbc.update("INSERT INTO reserva_recurso (reserva_id, tipo_recurso, recurso_id, quantidade) VALUES (?, ?, ?, ?)",
                        reserva.getId(), tipo(rr.getRecurso()), id(rr.getRecurso()), rr.getQuantidade());
            }
        });
        Banco.reservas.put(reserva.getId(), reserva);
    }

    @Override
    public Reserva buscar(UUID id) {
        return Banco.reservas.get(id);
    }

    @Override
    public void atualizar(Reserva reserva) {
        jdbc.update("UPDATE reserva SET inicio = ?, fim = ?, estado = ?, justificativa_rejeicao = ? WHERE id = ?",
                ts(reserva.getInicio()), ts(reserva.getFim()), reserva.getEstado().name(),
                reserva.getJustificativaRejeicao(), reserva.getId());
    }

    @Override
    public boolean existeConflito(Object recurso, LocalDateTime inicio, LocalDateTime fim, UUID reservaIgnorada) {
        Boolean existe = jdbc.queryForObject(SQL_CONFLITO, Boolean.class,
                tipo(recurso), id(recurso), reservaIgnorada == null ? NENHUMA : reservaIgnorada, ts(fim), ts(inicio));
        return Boolean.TRUE.equals(existe);
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

    private static Timestamp ts(LocalDateTime instante) {
        return Timestamp.valueOf(instante);
    }
}
