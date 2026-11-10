package com.edualoca.persistencia;

import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A constraint ex_reserva_recurso_sem_sobreposicao (ADR-009) exercitada sem a checagem previa do servico:
 * grava direto pelo repositorio, como faria uma segunda instancia que passou pela checagem ao mesmo tempo.
 */
@Testcontainers
class ReservaRepositorioJdbcIT {

    private static final LocalDateTime OITO = LocalDateTime.of(2027, 3, 15, 8, 0);
    private static final LocalDateTime DEZ = OITO.plusHours(2);
    private static final LocalDateTime DOZE = OITO.plusHours(4);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static ReservaRepositorioJdbc repositorio;

    private final Sala sala = new Sala(UUID.randomUUID(), "Sala 202", "aula", 30, "Bloco C", false);

    @BeforeAll
    static void criarEsquema() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("db/postgres/schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        repositorio = new ReservaRepositorioJdbc(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                ZoneId.of("America/Sao_Paulo"));
    }

    @BeforeEach
    void limpar() {
        jdbc.execute("TRUNCATE reserva_recurso, reserva");
    }

    @Test
    @DisplayName("Segunda reserva ativa sobreposta do mesmo recurso e recusada pelo banco com 409, sem gravar nada")
    void sobreposicaoRecusadaPeloBanco() {
        repositorio.inserir(reserva(sala, OITO, DEZ, EstadoReserva.APROVADA));

        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> repositorio.inserir(reserva(sala, OITO.plusMinutes(30), DEZ.plusMinutes(30), EstadoReserva.SOLICITADA)));

        assertEquals(409, erro.getStatusHttp());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM reserva", Integer.class), "transacao recusada nao deixa linha");
    }

    @Test
    @DisplayName("Professor tambem e protegido pela constraint")
    void professorProtegido() {
        Professor professor = new Professor(UUID.randomUUID(), "Prof. X", "X-1", List.of(new Competencia("Fisica")));
        repositorio.inserir(reserva(professor, OITO, DEZ, EstadoReserva.APROVADA));

        assertThrows(RegraNegocioException.class, () -> repositorio.inserir(reserva(professor, OITO, DEZ, EstadoReserva.APROVADA)));
    }

    @Test
    @DisplayName("Intervalos que apenas se encostam [08,10) e [10,12) nao conflitam")
    void intervalosEncostadosPermitidos() {
        repositorio.inserir(reserva(sala, OITO, DEZ, EstadoReserva.APROVADA));

        assertDoesNotThrow(() -> repositorio.inserir(reserva(sala, DEZ, DOZE, EstadoReserva.APROVADA)));
    }

    @Test
    @DisplayName("Reserva cancelada libera o horario; reativar sobre outra reserva e recusado")
    void canceladaLiberaHorario() {
        Reserva primeira = reserva(sala, OITO, DEZ, EstadoReserva.APROVADA);
        repositorio.inserir(primeira);
        primeira.setEstado(EstadoReserva.CANCELADA);
        repositorio.atualizar(primeira);

        assertDoesNotThrow(() -> repositorio.inserir(reserva(sala, OITO, DEZ, EstadoReserva.APROVADA)));

        primeira.setEstado(EstadoReserva.APROVADA);
        assertThrows(RegraNegocioException.class, () -> repositorio.atualizar(primeira));
    }

    @Test
    @DisplayName("Outra violacao de integridade (id duplicado) nao e mascarada como 409 de sobreposicao")
    void outraViolacaoDeIntegridade_naoViraConflito() {
        Reserva reserva = reserva(sala, OITO, DEZ, EstadoReserva.APROVADA);
        repositorio.inserir(reserva);

        assertThrows(DataIntegrityViolationException.class, () -> repositorio.inserir(reserva));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM reserva", Integer.class));
    }

    @Test
    @DisplayName("Checagem de conflito ignora a propria reserva (alteracao), mas enxerga as demais")
    void existeConflito_ignoraApenasAReservaInformada() {
        Reserva reserva = reserva(sala, OITO, DEZ, EstadoReserva.APROVADA);
        repositorio.inserir(reserva);

        assertTrue(repositorio.existeConflito(sala, OITO.plusMinutes(30), DEZ, null));
        assertFalse(repositorio.existeConflito(sala, OITO.plusMinutes(30), DEZ, reserva.getId()));
        assertTrue(repositorio.existeConflito(sala, OITO.plusMinutes(30), DEZ, UUID.randomUUID()));
        assertSame(reserva, repositorio.buscar(reserva.getId()));
    }

    private static Reserva reserva(Object recurso, LocalDateTime inicio, LocalDateTime fim, EstadoReserva estado) {
        return new Reserva(UUID.randomUUID(), UUID.randomUUID(), inicio, fim, "Aula",
                List.of(new ReservaRecurso(recurso, 1)), estado);
    }
}
