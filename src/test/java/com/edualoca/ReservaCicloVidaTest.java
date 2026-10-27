package com.edualoca;

import com.edualoca.domain.comum.RegraNegocioException;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservaCicloVidaTest {

    private final ReservaService reservaService = new ReservaService();

    private Usuario solicitante() {
        return new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE);
    }

    private Sala sala(boolean restrito) {
        return new Sala(UUID.randomUUID(), "Sala X", "aula", 30, "Bloco A", restrito);
    }

    @Test
    @DisplayName("RF-12-T1: criar reserva com sala nao restrita deve nascer em estado APROVADA")
    void rf12T1_reservaComSalaNaoRestrita_deveNascerAprovada() {
        // arrange
        Usuario autor = solicitante();
        Sala salaNaoRestrita = sala(false);
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        Reserva reserva = reservaService.criar(autor, inicio, fim, "Aula regular",
                List.of(new ReservaRecurso(salaNaoRestrita, 1)));

        // assert
        assertEquals(EstadoReserva.APROVADA, reserva.getEstado());
    }

    @Test
    @DisplayName("RF-12-T2: criar reserva com recurso restrito deve nascer em estado SOLICITADA")
    void rf12T2_reservaComRecursoRestrito_deveNascerSolicitada() {
        // arrange
        Usuario autor = solicitante();
        Sala salaRestrita = sala(true);
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        Reserva reserva = reservaService.criar(autor, inicio, fim, "Aula especial",
                List.of(new ReservaRecurso(salaRestrita, 1)));

        // assert
        assertEquals(EstadoReserva.SOLICITADA, reserva.getEstado());
    }

    @Test
    @DisplayName("RF-12-T3: reserva com sala restrita + professor nao restrito deve nascer SOLICITADA (basta um recurso restrito)")
    void rf12T3_reservaMista_deveNascerSolicitadaPorUmRecursoRestrito() {
        // arrange
        Usuario autor = solicitante();
        Sala salaRestrita = sala(true);
        com.edualoca.domain.recurso.Professor professorNaoRestrito = new com.edualoca.domain.recurso.Professor(
                UUID.randomUUID(), "Prof. Z", "MAT-200", List.of());
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        Reserva reserva = reservaService.criar(autor, inicio, fim, "Aula mista",
                List.of(new ReservaRecurso(salaRestrita, 1), new ReservaRecurso(professorNaoRestrito, 1)));

        // assert
        assertEquals(EstadoReserva.SOLICITADA, reserva.getEstado());
    }

    @Test
    @DisplayName("RF-12-T4: criar reserva sem finalidade deve ser rejeitada com HTTP 400")
    void rf12T4_reservaSemFinalidade_deveSerRejeitada() {
        // arrange
        Usuario autor = solicitante();
        Sala salaNaoRestrita = sala(false);
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.criar(autor, inicio, fim, null, List.of(new ReservaRecurso(salaNaoRestrita, 1))));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-12-T5 [gap Q13, assumindo exigencia de ao menos um recurso]: reserva sem nenhum recurso vinculado deve ser rejeitada com HTTP 400")
    void rf12T5_reservaSemRecursoVinculado_deveSerRejeitada() {
        // arrange
        Usuario autor = solicitante();
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.criar(autor, inicio, fim, "Aula sem recurso", Collections.emptyList()));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-13-T1: alterar reserva propria em SOLICITADA revalida sobreposicao antes de aplicar mudanca")
    void rf13T1_alterarReservaSolicitadaComHorarioLivre_devePermitir() {
        // arrange
        Usuario autor = solicitante();
        Sala salaRestrita = sala(true);
        Reserva reservaSolicitada = reservaService.criar(autor, LocalDateTime.of(2026, 3, 9, 10, 0),
                LocalDateTime.of(2026, 3, 9, 12, 0), "Aula especial", List.of(new ReservaRecurso(salaRestrita, 1)));
        UUID reservaId = reservaSolicitada.getId();
        LocalDateTime novoInicio = LocalDateTime.of(2026, 3, 11, 10, 0);
        LocalDateTime novoFim = LocalDateTime.of(2026, 3, 11, 12, 0);

        // act
        Reserva reservaAlterada = reservaService.alterar(autor, reservaId, novoInicio, novoFim);

        // assert
        assertEquals(novoInicio, reservaAlterada.getInicio());
    }

    @Test
    @DisplayName("RF-13-T2: alterar reserva propria em APROVADA deve ser permitido")
    void rf13T2_alterarReservaAprovada_devePermitir() {
        // arrange
        Usuario autor = solicitante();
        Sala salaNaoRestrita = sala(false);
        Reserva reservaAprovada = reservaService.criar(autor, LocalDateTime.of(2026, 3, 9, 10, 0),
                LocalDateTime.of(2026, 3, 9, 12, 0), "Aula regular", List.of(new ReservaRecurso(salaNaoRestrita, 1)));
        UUID reservaId = reservaAprovada.getId();
        LocalDateTime novoInicio = LocalDateTime.of(2026, 3, 12, 10, 0);
        LocalDateTime novoFim = LocalDateTime.of(2026, 3, 12, 12, 0);

        // act
        Reserva reservaAlterada = reservaService.alterar(autor, reservaId, novoInicio, novoFim);

        // assert
        assertEquals(novoFim, reservaAlterada.getFim());
    }

    @Test
    @DisplayName("RF-13-T3 [gap Q14, assumindo HTTP 409 por estado incompativel]: alterar reserva em EM_USO deve ser rejeitado")
    void rf13T3_alterarReservaEmUso_deveSerRejeitada() {
        // arrange
        Usuario autor = solicitante();
        Sala salaNaoRestrita = sala(false);
        Reserva reservaEmUso = reservaService.criar(autor, LocalDateTime.of(2026, 3, 9, 10, 0),
                LocalDateTime.of(2026, 3, 9, 12, 0), "Aula em uso", List.of(new ReservaRecurso(salaNaoRestrita, 1)));
        reservaEmUso.setEstado(EstadoReserva.EM_USO);
        UUID reservaEmUsoId = reservaEmUso.getId();
        LocalDateTime novoInicio = LocalDateTime.of(2026, 3, 12, 10, 0);
        LocalDateTime novoFim = LocalDateTime.of(2026, 3, 12, 12, 0);

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.alterar(autor, reservaEmUsoId, novoInicio, novoFim));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-13-T4: alteracao de horario que colide com outra reserva ativa deve ser rejeitada com HTTP 409")
    void rf13T4_alteracaoComColisaoDeHorario_deveSerRejeitada() {
        // arrange
        Usuario autor = solicitante();
        Sala salaX = sala(false);
        Reserva reservaOcupandoHorarioColidente = reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 11, 0),
                LocalDateTime.of(2026, 3, 10, 13, 0), "Aula ocupando o horario alvo", List.of(new ReservaRecurso(salaX, 1)));
        Reserva reservaASerAlterada = reservaService.criar(autor, LocalDateTime.of(2026, 3, 20, 8, 0),
                LocalDateTime.of(2026, 3, 20, 9, 0), "Aula a ser remarcada", List.of(new ReservaRecurso(salaX, 1)));
        UUID reservaId = reservaASerAlterada.getId();
        LocalDateTime novoInicioColidente = reservaOcupandoHorarioColidente.getInicio().plusMinutes(30);
        LocalDateTime novoFimColidente = reservaOcupandoHorarioColidente.getFim();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.alterar(autor, reservaId, novoInicioColidente, novoFimColidente));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-14-T1: cancelar reserva propria em SOLICITADA com motivo deve transicionar para CANCELADA")
    void rf14T1_cancelamentoComMotivo_deveTransicionarParaCancelada() {
        // arrange
        Usuario autor = solicitante();
        Sala salaRestrita = sala(true);
        Reserva reservaSolicitada = reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula especial", List.of(new ReservaRecurso(salaRestrita, 1)));
        UUID reservaId = reservaSolicitada.getId();

        // act
        Reserva reservaCancelada = reservaService.cancelar(autor, reservaId, "Mudanca de planos");

        // assert
        assertEquals(EstadoReserva.CANCELADA, reservaCancelada.getEstado());
    }

    @Test
    @DisplayName("RF-14-T2: cancelamento em APROVADA ou EM_USO deve ser permitido")
    void rf14T2_cancelamentoEmAprovadaOuEmUso_devePermitir() {
        // arrange
        Usuario autor = solicitante();
        Sala salaNaoRestrita = sala(false);
        Reserva reservaAprovada = reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula regular", List.of(new ReservaRecurso(salaNaoRestrita, 1)));
        UUID reservaAprovadaId = reservaAprovada.getId();

        // act
        Reserva reservaCancelada = reservaService.cancelar(autor, reservaAprovadaId, "Imprevisto");

        // assert
        assertEquals(EstadoReserva.CANCELADA, reservaCancelada.getEstado());
    }

    @Test
    @DisplayName("RF-14-T3: tentativa de cancelar reserva ja CONCLUIDA deve ser rejeitada")
    void rf14T3_cancelamentoDeReservaConcluida_deveSerRejeitado() {
        // arrange
        Usuario autor = solicitante();
        Sala salaNaoRestrita = sala(false);
        Reserva reservaConcluida = reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula ja concluida", List.of(new ReservaRecurso(salaNaoRestrita, 1)));
        reservaConcluida.setEstado(EstadoReserva.CONCLUIDA);
        UUID reservaConcluidaId = reservaConcluida.getId();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.cancelar(autor, reservaConcluidaId, "Tentativa invalida"));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-14-T4: cancelamento sem motivo (vazio/nulo) deve ser rejeitado com HTTP 400")
    void rf14T4_cancelamentoSemMotivo_deveSerRejeitado() {
        // arrange
        Usuario autor = solicitante();
        UUID reservaId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.cancelar(autor, reservaId, ""));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-14-T5 [gap Q15, assumindo erro em vez de no-op idempotente]: cancelar reserva ja CANCELADA deve ser rejeitado")
    void rf14T5_cancelamentoDeReservaJaCancelada_deveSerRejeitado() {
        // arrange
        Usuario autor = solicitante();
        Sala salaNaoRestrita = sala(false);
        Reserva reservaJaCancelada = reservaService.criar(autor, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula cancelada anteriormente", List.of(new ReservaRecurso(salaNaoRestrita, 1)));
        reservaJaCancelada.setEstado(EstadoReserva.CANCELADA);
        UUID reservaJaCanceladaId = reservaJaCancelada.getId();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.cancelar(autor, reservaJaCanceladaId, "Segunda tentativa de cancelamento"));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }
}
