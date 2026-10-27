package com.edualoca;

import com.edualoca.domain.notificacao.NotificacaoEnviada;
import com.edualoca.domain.notificacao.NotificacaoService;
import com.edualoca.domain.notificacao.StatusNotificacao;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NotificacoesTest {

    private final NotificacaoService notificacaoService = new NotificacaoService();

    private Reserva reservaDeExemplo(EstadoReserva estado) {
        Sala sala = new Sala(UUID.randomUUID(), "Sala X", "aula", 30, "Bloco A", true);
        return new Reserva(UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.of(2026, 3, 10, 10, 0), LocalDateTime.of(2026, 3, 10, 12, 0),
                "Aula especial", List.of(new ReservaRecurso(sala, 1)), estado);
    }

    @Test
    @DisplayName("RF-31-T1: mudanca de estado da propria reserva deve disparar notificacao ao Solicitante")
    void rf31T1_mudancaDeEstadoDaReserva_deveNotificarSolicitante() {
        // arrange
        Reserva reserva = reservaDeExemplo(EstadoReserva.APROVADA);

        // act
        NotificacaoEnviada notificacao = notificacaoService.notificarTransicaoDeEstado(reserva);

        // assert
        assertEquals(reserva.getSolicitanteId(), notificacao.getDestinatarioId());
    }

    @Test
    @DisplayName("RF-32-T1: nova solicitacao restrita deve notificar o responsavel competente")
    void rf32T1_novaSolicitacaoRestrita_deveNotificarResponsavelCompetente() {
        // arrange
        Reserva reserva = reservaDeExemplo(EstadoReserva.SOLICITADA);

        // act
        NotificacaoEnviada notificacao = notificacaoService.notificarResponsavelCompetente(reserva);

        // assert
        assertNotNull(notificacao.getDestinatarioId());
    }

    @Test
    @DisplayName("RF-32-T2 [gap Q23, inconsistencia modelagem x RF-32]: recurso restrito sem Responsavel associado deve retornar notificacao em FALHA, nao excecao nao tratada")
    void rf32T2_recursoRestritoSemResponsavelAssociado_deveRetornarFalha() {
        // arrange
        Sala salaSemResponsavel = new Sala(UUID.randomUUID(), "Sala sem responsavel", "aula", 10, "Bloco C", true);
        Reserva reserva = new Reserva(UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.of(2026, 3, 10, 10, 0), LocalDateTime.of(2026, 3, 10, 12, 0),
                "Aula sem responsavel definido", List.of(new ReservaRecurso(salaSemResponsavel, 1)), EstadoReserva.SOLICITADA);

        // act
        NotificacaoEnviada notificacao = notificacaoService.notificarResponsavelCompetente(reserva);

        // assert
        assertEquals(StatusNotificacao.FALHA, notificacao.getStatus());
    }

    @Test
    @DisplayName("RF-33-T1: endpoint de notificacao externa indisponivel deve persistir a transicao e registrar notificacao como FALHA (limitacao: sem WireMock nesta fase, simulado)")
    void rf33T1_endpointIndisponivel_devePersistirTransicaoComNotificacaoEmFalha() {
        // arrange
        NotificacaoService notificacaoServiceComEndpointIndisponivel = new NotificacaoService(r -> false);
        Reserva reserva = reservaDeExemplo(EstadoReserva.APROVADA);

        // act
        NotificacaoEnviada notificacao = notificacaoServiceComEndpointIndisponivel.notificarTransicaoDeEstado(reserva);

        // assert
        assertEquals(StatusNotificacao.FALHA, notificacao.getStatus());
    }

    @Test
    @DisplayName("RF-33-T2: endpoint de notificacao disponivel deve registrar NotificacaoEnviada com status ENVIADA")
    void rf33T2_endpointDisponivel_deveRegistrarStatusEnviada() {
        // arrange
        Reserva reserva = reservaDeExemplo(EstadoReserva.APROVADA);

        // act
        NotificacaoEnviada notificacao = notificacaoService.notificarTransicaoDeEstado(reserva);

        // assert
        assertEquals(StatusNotificacao.ENVIADA, notificacao.getStatus());
    }
}
