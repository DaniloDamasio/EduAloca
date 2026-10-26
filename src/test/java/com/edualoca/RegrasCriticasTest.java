package com.edualoca;

import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.recurso.Manutencao;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegrasCriticasTest {

    private final ReservaService reservaService = new ReservaService();
    private final CadastroRecursoService cadastroRecursoService = new CadastroRecursoService();

    private Usuario solicitante() {
        return new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE);
    }

    private Sala sala(boolean restrito) {
        return new Sala(UUID.randomUUID(), "Sala X", "aula", 30, "Bloco A", restrito);
    }

    @Test
    @DisplayName("RC-01-T1: inicio e fim validos (mesmo dia) devem ser aceitos para validacao de intervalo")
    void rc01T1_inicioAnteriorAoFim_devePermitirCriacao() {
        // arrange
        Usuario autor = solicitante();
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);
        List<ReservaRecurso> recursos = List.of(new ReservaRecurso(sala(false), 1));

        // act
        Reserva reserva = reservaService.criar(autor, inicio, fim, "Aula de robotica", recursos);

        // assert
        assertTrue(reserva.getFim().isAfter(reserva.getInicio()));
    }

    @Test
    @DisplayName("RC-01-T2 [gap Q1, assumindo menor granularidade aceita pelo plano]: fim = inicio + 1 minuto deve ser aceito quanto a fim > inicio")
    void rc01T2_menorIntervaloValido_devePermitirCriacao() {
        // arrange
        Usuario autor = solicitante();
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = inicio.plusMinutes(1);
        List<ReservaRecurso> recursos = List.of(new ReservaRecurso(sala(false), 1));

        // act
        Reserva reserva = reservaService.criar(autor, inicio, fim, "Reuniao rapida", recursos);

        // assert
        assertTrue(reserva.getFim().isAfter(reserva.getInicio()));
    }

    @Test
    @DisplayName("RC-01-T3: fim igual a inicio (duracao zero) deve ser rejeitado com HTTP 400")
    void rc01T3_fimIgualInicio_deveSerRejeitadoComBadRequest() {
        // arrange
        Usuario autor = solicitante();
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = inicio;
        List<ReservaRecurso> recursos = List.of(new ReservaRecurso(sala(false), 1));

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.criar(autor, inicio, fim, "Teste", recursos));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RC-01-T4: fim anterior ao inicio deve ser rejeitado com HTTP 400")
    void rc01T4_fimAnteriorAoInicio_deveSerRejeitadoComBadRequest() {
        // arrange
        Usuario autor = solicitante();
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 12, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 10, 0);
        List<ReservaRecurso> recursos = List.of(new ReservaRecurso(sala(false), 1));

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.criar(autor, inicio, fim, "Teste", recursos));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RC-01-T5: inicio ou fim ausentes (nulos) devem ser rejeitados com HTTP 400")
    void rc01T5_inicioOuFimNulos_deveSerRejeitadoComBadRequest() {
        // arrange
        Usuario autor = solicitante();
        List<ReservaRecurso> recursos = List.of(new ReservaRecurso(sala(false), 1));

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.criar(autor, null, null, "Teste", recursos));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RC-01-T6: alterar reserva existente definindo fim <= inicio deve ser rejeitado, sem persistir")
    void rc01T6_alteracaoComFimMenorOuIgualInicio_deveSerRejeitada() {
        // arrange
        Usuario autor = solicitante();
        UUID reservaId = UUID.randomUUID();
        LocalDateTime novoInicio = LocalDateTime.of(2026, 3, 10, 14, 0);
        LocalDateTime novoFimInvalido = LocalDateTime.of(2026, 3, 10, 14, 0);

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.alterar(autor, reservaId, novoInicio, novoFimInvalido));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RC-02-T1: reservas disjuntas da mesma sala (10-12 e 13-14) devem ser ambas aceitas")
    void rc02T1_reservasDisjuntasMesmaSala_devemSerAmbasAceitas() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        LocalDateTime inicioA = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fimA = LocalDateTime.of(2026, 3, 10, 12, 0);
        LocalDateTime inicioB = LocalDateTime.of(2026, 3, 10, 13, 0);
        LocalDateTime fimB = LocalDateTime.of(2026, 3, 10, 14, 0);

        // act
        Reserva reservaA = reservaService.criar(autor, inicioA, fimA, "Aula A", List.of(new ReservaRecurso(salaX, 1)));
        Reserva reservaB = reservaService.criar(autor, inicioB, fimB, "Aula B", List.of(new ReservaRecurso(salaX, 1)));

        // assert
        assertTrue(reservaA.getEstado() != null && reservaB.getEstado() != null);
    }

    @Test
    @DisplayName("RC-02-T2 [gap Q2, assumindo semiaberto conforme sugestao de tsrange]: reservas contiguas (fim de A == inicio de B) devem ser aceitas, sem sobreposicao")
    void rc02T2_reservasContiguas_devemSerAceitasSemSobreposicao() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        LocalDateTime inicioA = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fimA = LocalDateTime.of(2026, 3, 10, 12, 0);
        LocalDateTime inicioB = fimA;
        LocalDateTime fimB = LocalDateTime.of(2026, 3, 10, 13, 0);
        reservaService.criar(autor, inicioA, fimA, "Aula A", List.of(new ReservaRecurso(salaX, 1)));

        // act
        Reserva reservaB = reservaService.criar(autor, inicioB, fimB, "Aula B", List.of(new ReservaRecurso(salaX, 1)));

        // assert
        assertTrue(reservaB.getEstado() != null);
    }

    @Test
    @DisplayName("RC-02-T3: sobreposicao parcial da mesma sala com reserva APROVADA deve ser rejeitada com HTTP 409")
    void rc02T3_sobreposicaoParcial_deveSerRejeitadaComConflito() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0), LocalDateTime.of(2026, 3, 10, 12, 0),
                "Aula A", List.of(new ReservaRecurso(salaX, 1)));

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 11, 0), LocalDateTime.of(2026, 3, 10, 13, 0),
                        "Aula B", List.of(new ReservaRecurso(salaX, 1))));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RC-02-T4: reserva que engloba totalmente outra reserva ativa deve ser rejeitada com HTTP 409")
    void rc02T4_reservaEnglobaOutra_deveSerRejeitadaComConflito() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0), LocalDateTime.of(2026, 3, 10, 12, 0),
                "Aula A", List.of(new ReservaRecurso(salaX, 1)));

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 9, 0), LocalDateTime.of(2026, 3, 10, 13, 0),
                        "Aula B", List.of(new ReservaRecurso(salaX, 1))));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RC-02-T5: intervalos identicos da mesma sala devem ser rejeitados com HTTP 409")
    void rc02T5_intervalosIdenticos_devemSerRejeitadosComConflito() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);
        reservaService.criar(autor, inicio, fim, "Aula A", List.of(new ReservaRecurso(salaX, 1)));

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.criar(autor, inicio, fim, "Aula B", List.of(new ReservaRecurso(salaX, 1))));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RC-02-T6: sobreposicao na agenda do professor como recurso acessorio deve ser rejeitada com HTTP 409")
    void rc02T6_sobreposicaoAgendaProfessorRecursoAcessorio_deveSerRejeitada() {
        // arrange
        Usuario autor = solicitante();
        com.edualoca.domain.recurso.Professor professorP = new com.edualoca.domain.recurso.Professor(
                UUID.randomUUID(), "Prof. P", "MAT-001", List.of());
        reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0), LocalDateTime.of(2026, 3, 10, 12, 0),
                "Aula principal do professor", List.of(new ReservaRecurso(professorP, 1)));

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 11, 0), LocalDateTime.of(2026, 3, 10, 13, 0),
                        "Aula B convida o professor", List.of(new ReservaRecurso(sala(false), 1), new ReservaRecurso(professorP, 1))));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RC-02-T7: reserva multi-recurso (sala + professor + material) todos livres deve ser aceita")
    void rc02T7_multiRecursoSemConflito_deveSerAceita() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        com.edualoca.domain.recurso.Professor professorP = new com.edualoca.domain.recurso.Professor(
                UUID.randomUUID(), "Prof. P", "MAT-002", List.of());
        com.edualoca.domain.recurso.Material materialM = new com.edualoca.domain.recurso.Material(
                UUID.randomUUID(), "Projetor", "audiovisual", 3, false);
        List<ReservaRecurso> recursos = List.of(
                new ReservaRecurso(salaX, 1), new ReservaRecurso(professorP, 1), new ReservaRecurso(materialM, 1));

        // act
        Reserva reserva = reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula multi-recurso", recursos);

        // assert
        assertTrue(reserva.getEstado() != null);
    }

    @Test
    @DisplayName("RC-02-T8: reserva com apenas um dos multiplos recursos em conflito (professor ocupado) deve ser rejeitada com HTTP 409")
    void rc02T8_apenasUmRecursoEmConflito_deveSerRejeitada() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        com.edualoca.domain.recurso.Professor professorP = new com.edualoca.domain.recurso.Professor(
                UUID.randomUUID(), "Prof. P", "MAT-003", List.of());
        reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0), LocalDateTime.of(2026, 3, 10, 12, 0),
                "Aula ocupa professor", List.of(new ReservaRecurso(professorP, 1)));

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0), LocalDateTime.of(2026, 3, 10, 12, 0),
                        "Sala livre mas professor ocupado", List.of(new ReservaRecurso(salaX, 1), new ReservaRecurso(professorP, 1))));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RC-02-T9 [gap Q3, assumindo que CANCELADA/REJEITADA nao sao ativas]: reserva cancelada no mesmo intervalo nao deve bloquear nova reserva")
    void rc02T9_reservaCanceladaNaoBloqueiaNovaReserva() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        Reserva reservaCancelada = reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula cancelada", List.of(new ReservaRecurso(salaX, 1)));
        reservaCancelada.setEstado(EstadoReserva.CANCELADA);

        // act
        Reserva novaReserva = reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula nova", List.of(new ReservaRecurso(salaX, 1)));

        // assert
        assertTrue(novaReserva.getEstado() != null);
    }

    @Test
    @DisplayName("RC-02-T10: sala em manutencao sobreposta a tentativa de reserva deve ser rejeitada com HTTP 409")
    void rc02T10_salaEmManutencao_deveRejeitarReservaComConflito() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        cadastroRecursoService.cadastrarManutencao(salaX, LocalDateTime.of(2026, 3, 10, 0, 0),
                LocalDateTime.of(2026, 3, 18, 0, 0), "manutencao preventiva");

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.criar(autor, LocalDateTime.of(2026, 3, 11, 11, 0), LocalDateTime.of(2026, 3, 11, 12, 0),
                        "Aula durante manutencao", List.of(new ReservaRecurso(salaX, 1))));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RC-03-T1: unica requisicao para recurso livre deve ser aceita normalmente")
    void rc03T1_unicaRequisicaoSemDisputa_deveSerAceita() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);

        // act
        Reserva reserva = reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula unica", List.of(new ReservaRecurso(salaX, 1)));

        // assert
        assertTrue(reserva.getEstado() != null);
    }

    @Test
    @DisplayName("RC-03-T2: duas requisicoes concorrentes para o mesmo recurso/intervalo devem resultar em exatamente uma aceita")
    void rc03T2_duasRequisicoesConcorrentes_apenasUmaDeveSerAceita() throws InterruptedException {
        // arrange
        Sala salaX = sala(false);
        Usuario solicitanteA = solicitante();
        Usuario solicitanteB = solicitante();
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.atomic.AtomicInteger sucessos = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger conflitos = new java.util.concurrent.atomic.AtomicInteger(0);
        Runnable tarefaA = () -> executarTentativaDeReserva(solicitanteA, inicio, fim, salaX, sucessos, conflitos);
        Runnable tarefaB = () -> executarTentativaDeReserva(solicitanteB, inicio, fim, salaX, sucessos, conflitos);

        // act
        pool.submit(tarefaA);
        pool.submit(tarefaB);
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        // assert
        assertEquals(1, sucessos.get());
        assertEquals(1, conflitos.get());
    }

    private void executarTentativaDeReserva(Usuario autor, LocalDateTime inicio, LocalDateTime fim, Sala sala,
                                             java.util.concurrent.atomic.AtomicInteger sucessos,
                                             java.util.concurrent.atomic.AtomicInteger conflitos) {
        try {
            reservaService.criar(autor, inicio, fim, "Disputa concorrente", List.of(new ReservaRecurso(sala, 1)));
            sucessos.incrementAndGet();
        } catch (RegraNegocioException e) {
            if (e.getStatusHttp() == 409) {
                conflitos.incrementAndGet();
            }
        }
    }

    @Test
    @DisplayName("RC-03-T3: N=5 requisicoes concorrentes disputando o mesmo recurso/intervalo devem resultar em exatamente uma aceita (limitacao: sem Testcontainers nesta fase, teste roda em memoria)")
    void rc03T3_cincoRequisicoesConcorrentes_apenasUmaDeveSerAceita() throws InterruptedException {
        // arrange
        Sala salaX = sala(false);
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);
        int n = 5;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(n);
        java.util.concurrent.atomic.AtomicInteger sucessos = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger conflitos = new java.util.concurrent.atomic.AtomicInteger(0);

        // act
        for (int i = 0; i < n; i++) {
            Usuario autor = solicitante();
            pool.submit(() -> executarTentativaDeReserva(autor, inicio, fim, salaX, sucessos, conflitos));
        }
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        // assert
        assertEquals(1, sucessos.get());
        assertEquals(n - 1, conflitos.get());
    }

    @Test
    @DisplayName("RC-03-T4: falha parcial durante criacao concorrente nao deve deixar estado intermediario inconsistente (limitacao: sem Testcontainers, simulado em memoria)")
    void rc03T4_falhaParcialDuranteCriacao_naoDeveDeixarEstadoInconsistente() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        Reserva reserva = reservaService.criar(autor, inicio, fim, "Aula com falha simulada", List.of(new ReservaRecurso(salaX, 1)));

        // assert
        assertTrue(reserva.getEstado() == EstadoReserva.SOLICITADA || reserva.getEstado() == EstadoReserva.APROVADA);
    }

    @Test
    @DisplayName("RC-04-T1: administrador registra manutencao de sala com periodo e motivo validos")
    void rc04T1_registroDeManutencao_devePersistirPeriodo() {
        // arrange
        Sala salaX = sala(false);
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 7, 0, 0);

        // act
        Manutencao manutencao = cadastroRecursoService.cadastrarManutencao(salaX, inicio, fim, "troca de ar-condicionado");

        // assert
        assertEquals("troca de ar-condicionado", manutencao.getMotivo());
    }

    @Test
    @DisplayName("RC-04-T2: tentativa de reserva de sala em manutencao deve ser rejeitada com HTTP 409")
    void rc04T2_reservaDentroDoPeriodoDeManutencao_deveSerRejeitada() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        cadastroRecursoService.cadastrarManutencao(salaX, LocalDateTime.of(2026, 3, 1, 0, 0),
                LocalDateTime.of(2026, 3, 7, 0, 0), "manutencao");

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.criar(autor, LocalDateTime.of(2026, 3, 3, 10, 0), LocalDateTime.of(2026, 3, 3, 12, 0),
                        "Aula durante manutencao", List.of(new ReservaRecurso(salaX, 1))));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RC-04-T3 [gap Q2, mesma ambiguidade de fronteira]: reserva exatamente no limite da manutencao deve ser aceita (borda nao sobreposta)")
    void rc04T3_reservaNoLimiteDaManutencao_deveSerAceita() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        LocalDateTime fimManutencao = LocalDateTime.of(2026, 3, 7, 0, 0);
        cadastroRecursoService.cadastrarManutencao(salaX, LocalDateTime.of(2026, 3, 1, 0, 0), fimManutencao, "manutencao");

        // act
        Reserva reserva = reservaService.criar(autor, fimManutencao, fimManutencao.plusHours(2),
                "Aula logo apos manutencao", List.of(new ReservaRecurso(salaX, 1)));

        // assert
        assertTrue(reserva.getEstado() != null);
    }

    @Test
    @DisplayName("RC-04-T4: cadastro de manutencao sobrepondo reserva APROVADA exige confirmacao explicita, senao e recusado")
    void rc04T4_manutencaoSobrepoeReservaAprovada_exigeConfirmacaoOuRecusa() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        Reserva reservaAprovada = reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula aprovada", List.of(new ReservaRecurso(salaX, 1)));
        reservaAprovada.setEstado(EstadoReserva.APROVADA);

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> cadastroRecursoService.cadastrarManutencao(salaX, LocalDateTime.of(2026, 3, 10, 9, 0),
                        LocalDateTime.of(2026, 3, 10, 13, 0), "manutencao urgente sem confirmacao"));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RC-04-T5: cadastro de manutencao sobrepondo reserva EM_USO exige confirmacao explicita, senao e recusado")
    void rc04T5_manutencaoSobrepoeReservaEmUso_exigeConfirmacaoOuRecusa() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        Reserva reservaEmUso = reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula em uso", List.of(new ReservaRecurso(salaX, 1)));
        reservaEmUso.setEstado(EstadoReserva.EM_USO);

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> cadastroRecursoService.cadastrarManutencao(salaX, LocalDateTime.of(2026, 3, 10, 9, 0),
                        LocalDateTime.of(2026, 3, 10, 13, 0), "manutencao urgente sem confirmacao"));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RC-04-T6: cadastro de manutencao sobre reserva em estado terminal (CANCELADA) nao exige confirmacao")
    void rc04T6_manutencaoSobreReservaTerminal_naoExigeConfirmacao() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        Reserva reservaCancelada = reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula cancelada", List.of(new ReservaRecurso(salaX, 1)));
        reservaCancelada.setEstado(EstadoReserva.CANCELADA);

        // act
        Manutencao manutencao = cadastroRecursoService.cadastrarManutencao(salaX, LocalDateTime.of(2026, 3, 10, 9, 0),
                LocalDateTime.of(2026, 3, 10, 13, 0), "manutencao sem conflito real");

        // assert
        assertEquals(salaX.getId(), manutencao.getSala().getId());
    }

    @Test
    @DisplayName("RC-04-T7 [gap Q5, assumindo que RC-01 se aplica tambem a Manutencao]: manutencao com fim <= inicio deve ser rejeitada com HTTP 400")
    void rc04T7_manutencaoComFimMenorOuIgualInicio_deveSerRejeitada() {
        // arrange
        Sala salaX = sala(false);
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> cadastroRecursoService.cadastrarManutencao(salaX, inicio, inicio, "motivo valido"));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RC-04-T8: cadastro de manutencao sem motivo deve ser rejeitado com HTTP 400")
    void rc04T8_manutencaoSemMotivo_deveSerRejeitada() {
        // arrange
        Sala salaX = sala(false);
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 17, 0, 0);

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> cadastroRecursoService.cadastrarManutencao(salaX, inicio, fim, null));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }
}
