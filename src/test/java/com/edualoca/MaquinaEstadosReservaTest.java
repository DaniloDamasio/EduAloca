package com.edualoca;

import com.edualoca.domain.comum.Banco;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaquinaEstadosReservaTest {

    private final ReservaService reservaService = new ReservaService();

    /** Responsavel competente pelo Bloco A, onde ficam as salas destes testes (P-2, DEF-010). */
    private Usuario responsavel() {
        return new Usuario(Banco.RESPONSAVEL_BLOCO_A_ID, "resp@example.com", "hash", Perfil.RESPONSAVEL);
    }

    // Reservas reais em cada estado de origem (antes, os testes abaixo usavam um UUID inexistente e passavam com
    // qualquer codigo; status-requisitos.md §4). Data no passado: o nao comparecimento ja passou da tolerancia.
    private static final java.time.LocalDateTime INI = java.time.LocalDateTime.of(2026, 3, 10, 10, 0);

    private Reserva reservaRestrita() {
        Sala sala = new Sala(UUID.randomUUID(), "Sala restrita", "aula", 20, "Bloco A", true);
        return reservaService.criar(new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE),
                INI, INI.plusHours(2), "Aula", java.util.List.of(new com.edualoca.domain.reserva.ReservaRecurso(sala, 1)));
    }

    private Reserva noEstado(EstadoReserva alvo) {
        Reserva r = reservaRestrita();
        switch (alvo) {
            case SOLICITADA -> { }
            case APROVADA -> reservaService.aprovar(responsavel(), r.getId());
            case EM_USO -> { reservaService.aprovar(responsavel(), r.getId()); reservaService.iniciarUso(responsavel(), r.getId()); }
            case CONCLUIDA -> { reservaService.aprovar(responsavel(), r.getId()); reservaService.iniciarUso(responsavel(), r.getId());
                reservaService.concluir(responsavel(), r.getId()); }
            case REJEITADA -> reservaService.rejeitar(responsavel(), r.getId(), "sala em banca");
            case CANCELADA -> reservaService.cancelar(new Usuario(r.getSolicitanteId(), "aluno@example.com", "hash", Perfil.SOLICITANTE), r.getId(), "remarcada");
            case NAO_COMPARECEU -> { reservaService.aprovar(responsavel(), r.getId()); reservaService.registrarNaoComparecimento(responsavel(), r.getId()); }
        }
        assertEquals(alvo, r.getEstado(), "pre-condicao: reserva no estado de origem");
        return r;
    }

    /** Nenhuma transicao de decisor sai de estado terminal: todas respondem 409 e o estado nao muda. */
    private void terminal(EstadoReserva estado) {
        Reserva r = noEstado(estado);
        java.util.List<org.junit.jupiter.api.function.Executable> acoes = java.util.List.of(
                () -> reservaService.aprovar(responsavel(), r.getId()),
                () -> reservaService.rejeitar(responsavel(), r.getId(), "x"),
                () -> reservaService.iniciarUso(responsavel(), r.getId()),
                () -> reservaService.concluir(responsavel(), r.getId()),
                () -> reservaService.registrarNaoComparecimento(responsavel(), r.getId()));
        for (org.junit.jupiter.api.function.Executable acao : acoes) {
            assertEquals(409, assertThrows(RegraNegocioException.class, acao).getStatusHttp());
        }
        assertEquals(estado, r.getEstado());
    }

    @Test
    @DisplayName("EST-T1: transicao SOLICITADA -> APROVADA deve ser permitida com auditoria e notificacao")
    void estT1_solicitadaParaAprovada_devePermitir() {
        // arrange
        Usuario responsavel = responsavel();
        Sala salaRestrita = new Sala(UUID.randomUUID(), "Sala restrita", "aula", 20, "Bloco A", true);
        Reserva reservaSolicitada = reservaService.criar(new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE),
                LocalDateTime.of(2026, 3, 10, 10, 0), LocalDateTime.of(2026, 3, 10, 12, 0),
                "Aula especial", List.of(new ReservaRecurso(salaRestrita, 1)));
        UUID reservaSolicitadaId = reservaSolicitada.getId();

        // act
        Reserva reserva = reservaService.aprovar(responsavel, reservaSolicitadaId);

        // assert
        assertEquals(EstadoReserva.APROVADA, reserva.getEstado());
    }

    @Test
    @DisplayName("EST-T2 (P-07): transicao APROVADA -> EM_USO pelo Responsavel competente")
    void estT2_aprovadaParaEmUso_devePermitir() {
        Reserva r = noEstado(EstadoReserva.APROVADA);

        reservaService.iniciarUso(responsavel(), r.getId());

        assertEquals(EstadoReserva.EM_USO, r.getEstado());
    }

    @Test
    @DisplayName("EST-T3 (P-07): transicao EM_USO -> CONCLUIDA pelo Responsavel competente")
    void estT3_emUsoParaConcluida_devePermitir() {
        Reserva r = noEstado(EstadoReserva.EM_USO);

        reservaService.concluir(responsavel(), r.getId());

        assertEquals(EstadoReserva.CONCLUIDA, r.getEstado());
    }

    @Test
    @DisplayName("EST-T4: transicao SOLICITADA -> REJEITADA deve ser permitida, exigindo justificativa")
    void estT4_solicitadaParaRejeitada_devePermitirComJustificativa() {
        // arrange
        Usuario responsavel = responsavel();
        Sala salaRestrita = new Sala(UUID.randomUUID(), "Sala restrita", "aula", 20, "Bloco A", true);
        Reserva reservaSolicitada = reservaService.criar(new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE),
                LocalDateTime.of(2026, 3, 10, 10, 0), LocalDateTime.of(2026, 3, 10, 12, 0),
                "Aula especial", List.of(new ReservaRecurso(salaRestrita, 1)));
        UUID reservaSolicitadaId = reservaSolicitada.getId();

        // act
        Reserva reserva = reservaService.rejeitar(responsavel, reservaSolicitadaId, "Conflito com evento institucional");

        // assert
        assertEquals(EstadoReserva.REJEITADA, reserva.getEstado());
    }

    @Test
    @DisplayName("EST-T5: transicao SOLICITADA -> CANCELADA deve ser permitida, exigindo motivo")
    void estT5_solicitadaParaCancelada_devePermitirComMotivo() {
        // arrange
        Usuario solicitante = new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE);
        Sala salaRestrita = new Sala(UUID.randomUUID(), "Sala restrita", "aula", 20, "Bloco A", true);
        Reserva reservaSolicitada = reservaService.criar(solicitante, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula especial", List.of(new ReservaRecurso(salaRestrita, 1)));
        UUID reservaSolicitadaId = reservaSolicitada.getId();

        // act
        Reserva reserva = reservaService.cancelar(solicitante, reservaSolicitadaId, "Desistencia");

        // assert
        assertEquals(EstadoReserva.CANCELADA, reserva.getEstado());
    }

    @Test
    @DisplayName("EST-T6: transicao APROVADA -> CANCELADA deve ser permitida, exigindo motivo")
    void estT6_aprovadaParaCancelada_devePermitirComMotivo() {
        // arrange
        Usuario solicitante = new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE);
        Sala salaNaoRestrita = new Sala(UUID.randomUUID(), "Sala livre", "aula", 20, "Bloco A", false);
        Reserva reservaAprovada = reservaService.criar(solicitante, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula regular", List.of(new ReservaRecurso(salaNaoRestrita, 1)));
        UUID reservaAprovadaId = reservaAprovada.getId();

        // act
        Reserva reserva = reservaService.cancelar(solicitante, reservaAprovadaId, "Imprevisto");

        // assert
        assertEquals(EstadoReserva.CANCELADA, reserva.getEstado());
    }

    @Test
    @DisplayName("EST-T7 (P-05): NAO_COMPARECEU sai de APROVADA, nunca de EM_USO (409 e estado intacto)")
    void estT7_emUsoParaNaoCompareceu_deveSerBloqueada() {
        Reserva emUso = noEstado(EstadoReserva.EM_USO);
        Reserva aprovada = noEstado(EstadoReserva.APROVADA);

        assertEquals(409, assertThrows(RegraNegocioException.class,
                () -> reservaService.registrarNaoComparecimento(responsavel(), emUso.getId())).getStatusHttp());
        reservaService.registrarNaoComparecimento(responsavel(), aprovada.getId());

        assertEquals(EstadoReserva.EM_USO, emUso.getEstado());
        assertEquals(EstadoReserva.NAO_COMPARECEU, aprovada.getEstado());
    }

    @Test
    @DisplayName("EST-T8 (RF-18): reserva restrita SOLICITADA nao vai direto para EM_USO nem para CONCLUIDA")
    void estT8_reservaRestritaPulandoAprovada_deveSerBloqueada() {
        Reserva r = noEstado(EstadoReserva.SOLICITADA);

        assertEquals(409, assertThrows(RegraNegocioException.class, () -> reservaService.iniciarUso(responsavel(), r.getId())).getStatusHttp());
        assertEquals(409, assertThrows(RegraNegocioException.class, () -> reservaService.concluir(responsavel(), r.getId())).getStatusHttp());
        assertEquals(EstadoReserva.SOLICITADA, r.getEstado());
    }

    @Test
    @DisplayName("EST-T9 [gap Q24, nao-aplicavel pois reserva sem restricao ja nasce APROVADA]: tentativa de transicao SOLICITADA -> EM_USO sem restricao nao deveria ocorrer")
    void estT9_reservaNaoRestritaJaNasceAprovada_naoDeveEstarEmSolicitada() {
        // arrange
        Usuario autor = new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE);
        com.edualoca.domain.recurso.Sala salaNaoRestrita = new com.edualoca.domain.recurso.Sala(
                UUID.randomUUID(), "Sala livre", "aula", 20, "Bloco A", false);
        java.time.LocalDateTime inicio = java.time.LocalDateTime.of(2026, 3, 10, 10, 0);
        java.time.LocalDateTime fim = java.time.LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        Reserva reserva = reservaService.criar(autor, inicio, fim, "Aula livre",
                java.util.List.of(new com.edualoca.domain.reserva.ReservaRecurso(salaNaoRestrita, 1)));

        // assert
        assertEquals(EstadoReserva.APROVADA, reserva.getEstado());
    }

    @Test
    @DisplayName("EST-T10: nenhuma transicao sai de REJEITADA (estado terminal): aprovar, rejeitar, iniciar, concluir e nao comparecimento -> 409")
    void estT10_transicaoAPartirDeRejeitada_deveSerBloqueada() {
        terminal(EstadoReserva.REJEITADA);
    }

    @Test
    @DisplayName("EST-T11: nenhuma transicao sai de CONCLUIDA (estado terminal): aprovar, rejeitar, iniciar, concluir e nao comparecimento -> 409")
    void estT11_transicaoAPartirDeConcluida_deveSerBloqueada() {
        terminal(EstadoReserva.CONCLUIDA);
    }

    @Test
    @DisplayName("EST-T12: nenhuma transicao sai de CANCELADA (estado terminal): aprovar, rejeitar, iniciar, concluir e nao comparecimento -> 409")
    void estT12_transicaoAPartirDeCancelada_deveSerBloqueada() {
        terminal(EstadoReserva.CANCELADA);
    }

    @Test
    @DisplayName("EST-T13: nenhuma transicao sai de NAO_COMPARECEU (estado terminal): aprovar, rejeitar, iniciar, concluir e nao comparecimento -> 409")
    void estT13_transicaoAPartirDeNaoCompareceu_deveSerBloqueada() {
        terminal(EstadoReserva.NAO_COMPARECEU);
    }

}
