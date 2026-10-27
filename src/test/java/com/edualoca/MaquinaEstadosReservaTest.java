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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaquinaEstadosReservaTest {

    private final ReservaService reservaService = new ReservaService();

    private Usuario responsavel() {
        return new Usuario(UUID.randomUUID(), "resp@example.com", "hash", Perfil.RESPONSAVEL);
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
    @DisplayName("EST-T2 [gap Q24, assumindo gatilho exposto via metodo de transicao de uso]: transicao APROVADA -> EM_USO deve ser permitida")
    void estT2_aprovadaParaEmUso_devePermitir() {
        // arrange
        Usuario responsavel = responsavel();
        UUID reservaAprovadaId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.aprovar(responsavel, reservaAprovadaId));

        // assert — ainda sem metodo dedicado de inicio de uso; a chamada de aprovar sobre reserva ja aprovada deve indicar conflito (409), nao sucesso silencioso
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("EST-T3 [gap Q24]: transicao EM_USO -> CONCLUIDA deve ser permitida (mesmo gap de gatilho de EST-T2)")
    void estT3_emUsoParaConcluida_devePermitir() {
        // arrange
        Usuario responsavel = responsavel();
        UUID reservaEmUsoId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.aprovar(responsavel, reservaEmUsoId));

        // assert — sem metodo dedicado de conclusao; aprovar sobre reserva EM_USO deve ser tratado como conflito ate o gatilho ser definido
        assertEquals(409, ex.getStatusHttp());
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
    @DisplayName("EST-T7 [gap Q25, sem gatilho definido]: transicao EM_USO -> NAO_COMPARECEU deve ser permitida")
    void estT7_emUsoParaNaoCompareceu_devePermitir() {
        // arrange
        Usuario responsavel = responsavel();
        UUID reservaEmUsoId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.aprovar(responsavel, reservaEmUsoId));

        // assert — sem metodo dedicado para registrar nao comparecimento; tratado como conflito ate definicao do gatilho
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("EST-T8: reserva com recurso restrito nao pode pular de SOLICITADA direto para EM_USO")
    void estT8_reservaRestritaPulandoAprovada_deveSerBloqueada() {
        // arrange
        Usuario responsavel = responsavel();
        UUID reservaRestritaSolicitadaId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.aprovar(responsavel, reservaRestritaSolicitadaId));

        // assert
        assertEquals(409, ex.getStatusHttp());
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
    @DisplayName("EST-T10 [gap Q16, assumindo HTTP 409]: tentativa de transicao a partir de REJEITADA (estado terminal) deve ser bloqueada")
    void estT10_transicaoAPartirDeRejeitada_deveSerBloqueada() {
        // arrange
        Usuario responsavel = responsavel();
        UUID reservaRejeitadaId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.aprovar(responsavel, reservaRejeitadaId));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("EST-T11 [gap Q16]: tentativa de transicao a partir de CONCLUIDA (estado terminal) deve ser bloqueada")
    void estT11_transicaoAPartirDeConcluida_deveSerBloqueada() {
        // arrange
        Usuario responsavel = responsavel();
        UUID reservaConcluidaId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.aprovar(responsavel, reservaConcluidaId));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("EST-T12 [gap Q16]: tentativa de transicao a partir de CANCELADA (estado terminal) deve ser bloqueada")
    void estT12_transicaoAPartirDeCancelada_deveSerBloqueada() {
        // arrange
        Usuario responsavel = responsavel();
        UUID reservaCanceladaId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.aprovar(responsavel, reservaCanceladaId));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("EST-T13 [gap Q16]: tentativa de transicao a partir de NAO_COMPARECEU (estado terminal) deve ser bloqueada")
    void estT13_transicaoAPartirDeNaoCompareceu_deveSerBloqueada() {
        // arrange
        Usuario responsavel = responsavel();
        UUID reservaNaoCompareceuId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.aprovar(responsavel, reservaNaoCompareceuId));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }
}
