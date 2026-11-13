package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reinicio da aplicacao no modo PostgreSQL: a aplicacao inteira sobe (contexto A), cadastra e reserva, e desliga; a
 * memoria e zerada como numa nova JVM; a aplicacao sobe de novo (contexto B) no MESMO banco. Catalogo e reservas tem de
 * voltar, com as regras funcionando sobre eles.
 */
@Testcontainers
class PersistenciaReinicioIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final LocalDateTime INICIO = LocalDateTime.of(2027, 9, 6, 8, 0);
    private static final LocalDateTime FIM = INICIO.plusHours(2);

    private final Usuario administrador = new Usuario(Banco.ADMINISTRADOR_ID, "admin@example.com", "h", Perfil.ADMINISTRADOR);
    private final Usuario responsavelA = new Usuario(Banco.RESPONSAVEL_BLOCO_A_ID, "responsavel@example.com", "h", Perfil.RESPONSAVEL);
    private final Usuario solicitante = new Usuario(UUID.randomUUID(), "sol@example.com", "h", Perfil.SOLICITANTE);

    private static ConfigurableApplicationContext subirAplicacao() {
        return new SpringApplicationBuilder(EduAlocaApplication.class)
                .properties("server.port=0", "spring.main.banner-mode=off",
                        "edualoca.persistencia.url=" + POSTGRES.getJdbcUrl(),
                        "edualoca.persistencia.usuario=" + POSTGRES.getUsername(),
                        "edualoca.persistencia.senha=" + POSTGRES.getPassword())
                .run();
    }

    private static JdbcTemplate banco() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("Catálogo, reservas e histórico sobrevivem ao reinício, e as regras continuam valendo sobre eles")
    void catalogoEReservasSobrevivemAoReinicio() {
        UUID salaId;
        UUID kitId;
        UUID materialInativoId;
        UUID professorId;
        UUID aprovadaId;
        UUID rejeitadaId;

        try (ConfigurableApplicationContext a = subirAplicacao()) {
            CadastroRecursoService cadastro = a.getBean(CadastroRecursoService.class).por(administrador);
            ReservaService reservas = a.getBean(ReservaService.class);

            Sala lab = cadastro.cadastrarSala("Lab Persistente", "laboratorio", 20, "Bloco A", true);
            cadastro.atualizarSala(lab.getId(), "Lab Persistente", "laboratorio", 30, "Bloco A", true);
            Material kit = cadastro.cadastrarMaterial("Kit Persistente", "kit", 4, false);
            Material velho = cadastro.cadastrarMaterial("Material Velho", "kit", 1, false);
            cadastro.inativarMaterial(velho.getId());
            Professor professor = cadastro.cadastrarProfessor("Prof. Persistente", "PP-1",
                    List.of(new Competencia("Química"), new Competencia("Física")));

            Reserva aprovada = reservas.criar(solicitante, INICIO, FIM, "Aula persistente", List.of(
                    new ReservaRecurso(lab, 1), new ReservaRecurso(kit, 2), new ReservaRecurso(professor, 1)));
            assertEquals(EstadoReserva.SOLICITADA, aprovada.getEstado(), "pre-condicao: sala restrita");
            reservas.aprovar(responsavelA, aprovada.getId());
            Reserva rejeitada = reservas.criar(solicitante, INICIO.plusDays(1), FIM.plusDays(1), "Aula rejeitada",
                    List.of(new ReservaRecurso(lab, 1)));
            reservas.rejeitar(responsavelA, rejeitada.getId(), "laboratório em reforma");

            salaId = lab.getId();
            kitId = kit.getId();
            materialInativoId = velho.getId();
            professorId = professor.getId();
            aprovadaId = aprovada.getId();
            rejeitadaId = rejeitada.getId();
        }

        Banco.resetParaTeste(); // a memória de uma nova JVM: só o PostgreSQL guarda o que aconteceu antes

        try (ConfigurableApplicationContext b = subirAplicacao()) {
            ReservaService reservas = b.getBean(ReservaService.class);
            Sala lab = Banco.salas.get(salaId);
            Reserva aprovada = Banco.reservas.get(aprovadaId);
            Reserva rejeitada = Banco.reservas.get(rejeitadaId);

            // catálogo
            assertAll(
                    () -> assertEquals(30, lab.getCapacidade(), "edição feita antes do reinício"),
                    () -> assertThat(lab.isRestrito()).isTrue(),
                    () -> assertFalse(Banco.materiais.get(materialInativoId).isAtivo(), "inativação feita antes do reinício"),
                    () -> assertEquals(List.of("Química", "Física"), Banco.professores.get(professorId).getCompetencias()
                            .stream().map(Competencia::getNome).toList()),
                    () -> assertEquals(professorId, Banco.professorIdPorIdentificador.get("PP-1")));

            // reservas: estado, justificativa e recursos apontando para os MESMOS objetos do catálogo
            assertAll(
                    () -> assertEquals(EstadoReserva.APROVADA, aprovada.getEstado()),
                    () -> assertEquals(INICIO, aprovada.getInicio()),
                    () -> assertEquals(3, aprovada.getRecursos().size()),
                    () -> assertSame(lab, aprovada.getRecursos().stream().map(ReservaRecurso::getRecurso)
                            .filter(Sala.class::isInstance).findFirst().orElseThrow()),
                    () -> assertEquals(2, aprovada.getRecursos().stream()
                            .filter(rr -> rr.getRecurso() == Banco.materiais.get(kitId)).findFirst().orElseThrow().getQuantidade()),
                    () -> assertEquals(EstadoReserva.REJEITADA, rejeitada.getEstado()),
                    () -> assertEquals("laboratório em reforma", rejeitada.getJustificativaRejeicao()),
                    () -> assertEquals(2, reservas.consultarHistorico(administrador, aprovadaId).size(), "criação + aprovação, do banco"));

            // as regras continuam valendo sobre o que voltou
            Sala mesmaSala = lab;
            RegraNegocioException conflito = assertThrows(RegraNegocioException.class, () -> reservas.criar(solicitante,
                    INICIO.plusMinutes(30), FIM, "Conflito", List.of(new ReservaRecurso(mesmaSala, 1))));
            assertEquals(409, conflito.getStatusHttp());
            assertThat(reservas.listar(solicitante)).extracting(Reserva::getId).contains(aprovadaId, rejeitadaId);

            // cadastro depois do reinício continua gravando no banco
            b.getBean(CadastroRecursoService.class).por(administrador).inativarSala(lab);
        }

        JdbcTemplate jdbc = banco();
        assertAll(
                () -> assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM sala WHERE id = ?", Integer.class, Banco.SALA_SEED_ID),
                        "o seed não duplica a cada subida"),
                () -> assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT ativo FROM sala WHERE id = ?", Boolean.class, salaId),
                        "inativação feita depois do reinício foi gravada"));
    }

    @Test
    @DisplayName("Login real: depois do reinício, o Solicitante do seed continua dono das próprias reservas e pode cancelá-las")
    void solicitanteDoSeedContinuaDonoDepoisDoReinicio() {
        UUID reservaId;
        UUID idAntes;
        try (ConfigurableApplicationContext a = subirAplicacao()) {
            Usuario ana = a.getBean(com.edualoca.domain.usuario.AutenticacaoService.class)
                    .autenticar("usuario@example.com", "senhaValida123");
            Sala sala = a.getBean(CadastroRecursoService.class).por(administrador)
                    .cadastrarSala("Sala do Dono", "aula", 20, "Bloco A", false);
            reservaId = a.getBean(ReservaService.class)
                    .criar(ana, INICIO.plusDays(3), FIM.plusDays(3), "Aula da Ana", List.of(new ReservaRecurso(sala, 1))).getId();
            idAntes = ana.getId();
        }

        Banco.resetParaTeste();

        try (ConfigurableApplicationContext b = subirAplicacao()) {
            Usuario ana = b.getBean(com.edualoca.domain.usuario.AutenticacaoService.class)
                    .autenticar("usuario@example.com", "senhaValida123");
            ReservaService reservas = b.getBean(ReservaService.class);
            assertAll(
                    () -> assertEquals(idAntes, ana.getId(), "o id da conta não pode mudar a cada subida"),
                    () -> assertThat(reservas.listar(ana)).extracting(Reserva::getId).contains(reservaId));
            assertEquals(EstadoReserva.CANCELADA, reservas.cancelar(ana, reservaId, "remarcada").getEstado());
        }
    }
}
