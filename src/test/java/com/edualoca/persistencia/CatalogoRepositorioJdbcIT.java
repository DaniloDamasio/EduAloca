package com.edualoca.persistencia;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Catalogo no PostgreSQL real (Testcontainers): gravacao com upsert, recarga no cache, o banco vencendo o seed e o seed
 * ausente sendo gravado sem sobrescrever nada.
 */
@Testcontainers
class CatalogoRepositorioJdbcIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static CatalogoRepositorioJdbc catalogo;

    @BeforeAll
    static void criarEsquema() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("db/postgres/schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        catalogo = new CatalogoRepositorioJdbc(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    @BeforeEach
    void limparBanco() {
        jdbc.update("DELETE FROM professor_competencia");
        jdbc.update("DELETE FROM professor");
        jdbc.update("DELETE FROM sala");
        jdbc.update("DELETE FROM material");
    }

    private int contar(String sql, Object... parametros) {
        return jdbc.queryForObject(sql, Integer.class, parametros);
    }

    @Test
    @DisplayName("Sala e material: upsert grava, edição e inativação atualizam a mesma linha")
    void upsertDeSalaEMaterial() {
        Sala sala = new Sala(UUID.randomUUID(), "Lab", "laboratorio", 20, "Bloco A", false);
        Material material = new Material(UUID.randomUUID(), "Kit", "kit", 3, false);
        catalogo.salvar(sala);
        catalogo.salvar(material);

        sala.atualizar("Lab Maker", "laboratorio", 25, "Bloco B", true);
        sala.inativar();
        material.atualizar("Kit Arduino", "kit", 5, true);
        catalogo.salvar(sala);
        catalogo.salvar(material);

        assertAll(
                () -> assertEquals(1, contar("SELECT count(*) FROM sala WHERE id = ?", sala.getId())),
                () -> assertEquals(1, contar("SELECT count(*) FROM sala WHERE id = ? AND nome = 'Lab Maker' AND capacidade = 25"
                        + " AND localizacao = 'Bloco B' AND restrito AND NOT ativo", sala.getId())),
                () -> assertEquals(1, contar("SELECT count(*) FROM material WHERE id = ? AND nome = 'Kit Arduino'"
                        + " AND quantidade_total = 5 AND restrito AND ativo", material.getId())));
    }

    @Test
    @DisplayName("Professor: competências substituídas a cada gravação, na ordem; identificador repetido vira 409")
    void professorECompetencias() {
        Professor professor = new Professor(UUID.randomUUID(), "Prof. Ana", "P-1", List.of(new Competencia("Química"), new Competencia("Física")));
        catalogo.salvar(professor);
        professor.atualizar("Prof. Ana Lima", List.of(new Competencia("Robótica")));
        catalogo.salvar(professor);

        List<String> competencias = jdbc.queryForList(
                "SELECT nome FROM professor_competencia WHERE professor_id = ? ORDER BY ordem", String.class, professor.getId());
        Professor outro = new Professor(UUID.randomUUID(), "Prof. Outro", "P-1", List.of());
        RegraNegocioException ex = assertThrows(RegraNegocioException.class, () -> catalogo.salvar(outro));
        assertAll(
                () -> assertEquals(List.of("Robótica"), competencias),
                () -> assertEquals(1, contar("SELECT count(*) FROM professor WHERE nome = 'Prof. Ana Lima'")),
                () -> assertEquals(409, ex.getStatusHttp()),
                () -> assertEquals(0, contar("SELECT count(*) FROM professor WHERE id = ?", outro.getId())));
    }

    @Test
    @DisplayName("Sincronização: o banco vence o seed (edição e inativação), o seed ausente é gravado e nada duplica")
    void sincronizacaoBancoVenceESeedAusenteEGravado() {
        // um "reinicio" anterior: seed gravado e editado no banco, mais uma sala cadastrada
        Banco.resetParaTeste();
        catalogo.sincronizarComOBanco();
        jdbc.update("UPDATE sala SET capacidade = 99, ativo = false WHERE id = ?", Banco.SALA_SEED_ID);
        Sala cadastrada = new Sala(UUID.randomUUID(), "Auditório", "auditorio", 200, "Bloco C", true);
        catalogo.salvar(cadastrada);
        Professor comCompetencias = new Professor(UUID.randomUUID(), "Prof. Bia", "P-2",
                List.of(new Competencia("Lógica"), new Competencia("Redes")));
        catalogo.salvar(comCompetencias);

        // nova "JVM": memória vazia, sincroniza de novo
        Banco.resetParaTeste();
        catalogo.sincronizarComOBanco();
        catalogo.sincronizarComOBanco();

        Sala seed = Banco.salas.get(Banco.SALA_SEED_ID);
        assertAll(
                () -> assertEquals(99, seed.getCapacidade(), "o banco vence o seed"),
                () -> assertFalse(seed.isAtivo()),
                () -> assertThat(Banco.salas).containsKey(cadastrada.getId()),
                () -> assertThat(Banco.salas.get(cadastrada.getId()).isRestrito()).isTrue(),
                () -> assertEquals(List.of("Lógica", "Redes"), Banco.professores.get(comCompetencias.getId()).getCompetencias()
                        .stream().map(Competencia::getNome).toList(), "ordem das competências preservada"),
                () -> assertEquals(comCompetencias.getId(), Banco.professorIdPorIdentificador.get("P-2")),
                () -> assertEquals(1, contar("SELECT count(*) FROM sala WHERE id = ?", Banco.SALA_SEED_ID), "seed não duplica"),
                () -> assertEquals(1, contar("SELECT count(*) FROM professor WHERE id = ?", Banco.PROFESSOR_SEED_ID)),
                () -> assertEquals(1, contar("SELECT count(*) FROM material WHERE id = ?", Banco.MATERIAL_SEED_ID)),
                () -> assertEquals(3, contar("SELECT count(*) FROM sala")),
                () -> assertEquals(List.of("Robotica"), jdbc.queryForList(
                        "SELECT nome FROM professor_competencia WHERE professor_id = ? ORDER BY ordem", String.class, Banco.PROFESSOR_SEED_ID)));
    }
}
