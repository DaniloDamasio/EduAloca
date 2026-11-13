package com.edualoca.persistencia;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.CatalogoRepositorio;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Catalogo no PostgreSQL. {@link #salvar} grava cada mudanca de cadastro (upsert); {@link #sincronizarComOBanco} roda na
 * subida da aplicacao: o que esta no banco vence (edicoes e inativacoes feitas antes do reinicio) e o seed que ainda nao
 * existe no banco e inserido. O {@link Banco} continua sendo o cache de leitura, entao o dominio segue comparando
 * recursos por referencia de objeto.
 */
public class CatalogoRepositorioJdbc implements CatalogoRepositorio {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;

    public CatalogoRepositorioJdbc(JdbcTemplate jdbc, TransactionTemplate transacao) {
        this.jdbc = jdbc;
        this.transacao = transacao;
    }

    @Override
    public void salvar(Sala sala) {
        jdbc.update("""
                        INSERT INTO sala (id, nome, tipo, capacidade, localizacao, restrito, ativo) VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET nome = EXCLUDED.nome, tipo = EXCLUDED.tipo,
                            capacidade = EXCLUDED.capacidade, localizacao = EXCLUDED.localizacao,
                            restrito = EXCLUDED.restrito, ativo = EXCLUDED.ativo""",
                sala.getId(), sala.getNome(), sala.getTipo(), sala.getCapacidade(), sala.getLocalizacao(),
                sala.isRestrito(), sala.isAtivo());
    }

    @Override
    public void salvar(Professor professor) {
        try {
            transacao.executeWithoutResult(status -> {
                jdbc.update("""
                                INSERT INTO professor (id, nome, identificador_institucional, ativo) VALUES (?, ?, ?, ?)
                                ON CONFLICT (id) DO UPDATE SET nome = EXCLUDED.nome, ativo = EXCLUDED.ativo""",
                        professor.getId(), professor.getNome(), professor.getIdentificadorInstitucional(), professor.isAtivo());
                jdbc.update("DELETE FROM professor_competencia WHERE professor_id = ?", professor.getId());
                inserirCompetencias(professor);
            });
        } catch (DuplicateKeyException e) {
            // Outra instancia ja gravou um professor com o mesmo identificador institucional.
            throw new RegraNegocioException(409, "Identificador institucional já cadastrado");
        }
    }

    @Override
    public void salvar(Material material) {
        jdbc.update("""
                        INSERT INTO material (id, nome, tipo, quantidade_total, restrito, ativo) VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET nome = EXCLUDED.nome, tipo = EXCLUDED.tipo,
                            quantidade_total = EXCLUDED.quantidade_total, restrito = EXCLUDED.restrito, ativo = EXCLUDED.ativo""",
                material.getId(), material.getNome(), material.getTipo(), material.getQuantidadeTotal(),
                material.isRestrito(), material.isAtivo());
    }

    /**
     * Subida no modo PostgreSQL: semeia o cache, sobrepoe nele o catalogo gravado (o banco vence) e grava no banco o seed
     * que faltar, sem sobrescrever nada que ja exista (ON CONFLICT DO NOTHING: seguro com duas instancias subindo juntas).
     */
    public void sincronizarComOBanco() {
        Banco.seedSeNecessario();
        Set<UUID> salasNoBanco = carregarSalas();
        Set<UUID> materiaisNoBanco = carregarMateriais();
        Set<UUID> professoresNoBanco = carregarProfessores();

        Banco.salas.values().stream().filter(s -> !salasNoBanco.contains(s.getId())).forEach(this::inserirSeAusente);
        Banco.materiais.values().stream().filter(m -> !materiaisNoBanco.contains(m.getId())).forEach(this::inserirSeAusente);
        Banco.professores.values().stream().filter(p -> !professoresNoBanco.contains(p.getId())).forEach(this::inserirSeAusente);
    }

    private Set<UUID> carregarSalas() {
        Set<UUID> ids = new HashSet<>();
        jdbc.query("SELECT id, nome, tipo, capacidade, localizacao, restrito, ativo FROM sala", rs -> {
            Sala sala = new Sala(rs.getObject("id", UUID.class), rs.getString("nome"), rs.getString("tipo"),
                    rs.getInt("capacidade"), rs.getString("localizacao"), rs.getBoolean("restrito"));
            if (!rs.getBoolean("ativo")) {
                sala.inativar();
            }
            Banco.salas.put(sala.getId(), sala);
            ids.add(sala.getId());
        });
        return ids;
    }

    private Set<UUID> carregarMateriais() {
        Set<UUID> ids = new HashSet<>();
        jdbc.query("SELECT id, nome, tipo, quantidade_total, restrito, ativo FROM material", rs -> {
            Material material = new Material(rs.getObject("id", UUID.class), rs.getString("nome"), rs.getString("tipo"),
                    rs.getInt("quantidade_total"), rs.getBoolean("restrito"));
            if (!rs.getBoolean("ativo")) {
                material.inativar();
            }
            Banco.materiais.put(material.getId(), material);
            ids.add(material.getId());
        });
        return ids;
    }

    private Set<UUID> carregarProfessores() {
        Map<UUID, List<Competencia>> competencias = new HashMap<>();
        jdbc.query("SELECT professor_id, nome FROM professor_competencia ORDER BY professor_id, ordem", rs -> {
            competencias.computeIfAbsent(rs.getObject("professor_id", UUID.class), id -> new ArrayList<>())
                    .add(new Competencia(rs.getString("nome")));
        });
        Set<UUID> ids = new HashSet<>();
        jdbc.query("SELECT id, nome, identificador_institucional, ativo FROM professor", rs -> {
            UUID id = rs.getObject("id", UUID.class);
            Professor professor = new Professor(id, rs.getString("nome"), rs.getString("identificador_institucional"),
                    competencias.getOrDefault(id, List.of()));
            if (!rs.getBoolean("ativo")) {
                professor.inativar();
            }
            Banco.professores.put(id, professor);
            Banco.professorIdPorIdentificador.put(professor.getIdentificadorInstitucional(), id);
            ids.add(id);
        });
        return ids;
    }

    private void inserirSeAusente(Sala sala) {
        jdbc.update("""
                        INSERT INTO sala (id, nome, tipo, capacidade, localizacao, restrito, ativo) VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO NOTHING""",
                sala.getId(), sala.getNome(), sala.getTipo(), sala.getCapacidade(), sala.getLocalizacao(),
                sala.isRestrito(), sala.isAtivo());
    }

    private void inserirSeAusente(Material material) {
        jdbc.update("""
                        INSERT INTO material (id, nome, tipo, quantidade_total, restrito, ativo) VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO NOTHING""",
                material.getId(), material.getNome(), material.getTipo(), material.getQuantidadeTotal(),
                material.isRestrito(), material.isAtivo());
    }

    private void inserirSeAusente(Professor professor) {
        transacao.executeWithoutResult(status -> {
            int inseridos = jdbc.update("""
                            INSERT INTO professor (id, nome, identificador_institucional, ativo) VALUES (?, ?, ?, ?)
                            ON CONFLICT DO NOTHING""",
                    professor.getId(), professor.getNome(), professor.getIdentificadorInstitucional(), professor.isAtivo());
            if (inseridos == 1) {
                inserirCompetencias(professor);
            }
        });
    }

    private void inserirCompetencias(Professor professor) {
        List<Competencia> lista = professor.getCompetencias();
        for (int ordem = 0; ordem < lista.size(); ordem++) {
            jdbc.update("INSERT INTO professor_competencia (professor_id, ordem, nome) VALUES (?, ?, ?)",
                    professor.getId(), ordem, lista.get(ordem).getNome());
        }
    }
}
