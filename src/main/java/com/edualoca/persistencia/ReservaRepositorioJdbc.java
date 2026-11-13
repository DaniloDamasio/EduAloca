package com.edualoca.persistencia;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reservas gravadas no PostgreSQL. A exclusividade recurso/intervalo e garantida pela constraint
 * {@code ex_reserva_recurso_sem_sobreposicao} (EXCLUDE USING gist sobre tstzrange, ADR-009); a violacao
 * vira 409. A consulta {@link #existeConflito} continua existindo para dar a resposta cedo no caso comum.
 *
 * <p>Os demais servicos (relatorio, pesquisa, retirada) ainda leem {@link Banco#reservas}, por isso a
 * reserva gravada tambem e mantida nesse cache em memoria apos o commit (inclusive o de uma transacao externa). Na subida,
 * {@link #carregarNoCache} recarrega do banco as reservas gravadas antes do reinicio.
 */
public class ReservaRepositorioJdbc implements ReservaRepositorio {

    /** SQLSTATE do PostgreSQL para violacao de constraint de exclusao. */
    static final String EXCLUSION_VIOLATION = "23P01";
    static final String MENSAGEM_CONFLITO = "Recurso indisponivel: sobreposicao com reserva ativa";

    private static final UUID NENHUMA = new UUID(0L, 0L);
    private static final Logger LOG = LoggerFactory.getLogger(ReservaRepositorioJdbc.class);

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

    /**
     * Recarrega no cache as reservas do banco, resolvendo cada recurso por tipo e id no catalogo ja carregado
     * ({@link CatalogoRepositorioJdbc#sincronizarComOBanco}), para que a reserva aponte para o mesmo objeto do catalogo.
     * Reserva com recurso que nao esta no catalogo (ex.: gravada por teste sem cadastro) e ignorada, sem impedir a subida.
     *
     * @return quantas reservas foram recarregadas
     */
    public int carregarNoCache() {
        Map<UUID, Reserva> lidas = new LinkedHashMap<>();
        Set<UUID> incompletas = new HashSet<>();
        Map<UUID, List<ReservaRecurso>> recursos = new HashMap<>();
        jdbc.query("SELECT reserva_id, tipo_recurso, recurso_id, quantidade FROM reserva_recurso ORDER BY id", rs -> {
            UUID reservaId = rs.getObject("reserva_id", UUID.class);
            Object recurso = resolver(rs.getString("tipo_recurso"), rs.getObject("recurso_id", UUID.class));
            if (recurso == null) {
                incompletas.add(reservaId);
            } else {
                recursos.computeIfAbsent(reservaId, id -> new ArrayList<>()).add(new ReservaRecurso(recurso, rs.getInt("quantidade")));
            }
        });
        jdbc.query("SELECT id, solicitante_id, inicio, fim, finalidade, estado, justificativa_rejeicao FROM reserva", rs -> {
            UUID id = rs.getObject("id", UUID.class);
            if (incompletas.contains(id) || !recursos.containsKey(id)) {
                return;
            }
            EstadoReserva estado = EstadoReserva.valueOf(rs.getString("estado"));
            Reserva reserva = new Reserva(id, rs.getObject("solicitante_id", UUID.class),
                    rs.getTimestamp("inicio").toLocalDateTime(), rs.getTimestamp("fim").toLocalDateTime(),
                    rs.getString("finalidade"), recursos.get(id), estado);
            if (estado == EstadoReserva.REJEITADA) {
                reserva.rejeitar(rs.getString("justificativa_rejeicao"));
            }
            lidas.put(id, reserva);
        });
        if (!incompletas.isEmpty()) {
            LOG.warn("{} reserva(s) do banco nao recarregadas: recurso fora do catalogo", incompletas.size());
        }
        Banco.reservas.putAll(lidas);
        return lidas.size();
    }

    private static Object resolver(String tipo, UUID id) {
        return switch (tipo) {
            case "SALA" -> Banco.salas.get(id);
            case "PROFESSOR" -> Banco.professores.get(id);
            case "MATERIAL" -> Banco.materiais.get(id);
            default -> null;
        };
    }

    @Override
    public Reserva buscar(UUID id) {
        return Banco.reservas.get(id);
    }

    /** Mesma fonte de {@link #buscar}: o cache preenchido depois do commit. */
    @Override
    public List<Reserva> listar() {
        return List.copyOf(Banco.reservas.values());
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
