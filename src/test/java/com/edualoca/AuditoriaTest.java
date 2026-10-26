package com.edualoca;

import com.edualoca.domain.auditoria.AuditoriaEvento;
import com.edualoca.domain.auditoria.AuditoriaService;
import com.edualoca.domain.comum.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditoriaTest {

    private final AuditoriaService auditoriaService = new AuditoriaService();

    @Test
    @DisplayName("RF-27-T1: qualquer transicao de estado de reserva deve gerar registro de auditoria correspondente")
    void rf27T1_transicaoDeEstadoDeReserva_deveGerarAuditoria() {
        // arrange
        UUID reservaId = UUID.randomUUID();

        // act
        List<AuditoriaEvento> eventos = auditoriaService.consultarPorEntidade("Reserva", reservaId);

        // assert
        assertFalse(eventos.isEmpty());
    }

    @Test
    @DisplayName("RF-28-T1: alteracao de cadastro (sala/professor/material/usuario/manutencao) deve gerar entrada de auditoria")
    void rf28T1_alteracaoDeCadastro_deveGerarAuditoria() {
        // arrange
        UUID salaId = UUID.randomUUID();

        // act
        List<AuditoriaEvento> eventos = auditoriaService.consultarPorEntidade("Sala", salaId);

        // assert
        assertFalse(eventos.isEmpty());
    }

    @Test
    @DisplayName("RF-28-T2 [gap/inconsistencia Q21, assumindo valor REJEICAO_SOBREPOSICAO]: rejeicao automatica por sobreposicao deve gravar acao REJEICAO_SOBREPOSICAO")
    void rf28T2_rejeicaoAutomaticaPorSobreposicao_deveGravarAcaoEspecifica() {
        // arrange
        UUID reservaRejeitadaPorSobreposicaoId = UUID.randomUUID();

        // act
        List<AuditoriaEvento> eventos = auditoriaService.consultarPorEntidade("Reserva", reservaRejeitadaPorSobreposicaoId);

        // assert
        assertEquals("REJEICAO_SOBREPOSICAO", eventos.get(0).getAcao());
    }

    @Test
    @DisplayName("RF-30-T1: DELETE sobre reserva em qualquer estado diferente de SOLICITADA deve retornar HTTP 403/405")
    void rf30T1_deleteDeReservaForaDeSolicitada_deveSerRejeitado() {
        // arrange
        com.edualoca.domain.reserva.ReservaService reservaService = new com.edualoca.domain.reserva.ReservaService();
        com.edualoca.domain.usuario.Usuario autor = new com.edualoca.domain.usuario.Usuario(
                UUID.randomUUID(), "aluno@example.com", "hash", com.edualoca.domain.usuario.Perfil.SOLICITANTE);
        UUID reservaAprovadaId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.excluir(autor, reservaAprovadaId));

        // assert
        assertEquals(403, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-30-T2 [gap/inconsistencia Q22, assumindo que nenhum DELETE e exposto mesmo para SOLICITADA]: DELETE de reserva em SOLICITADA deve ser rejeitado, pois a exclusao fisica nao e exposta")
    void rf30T2_deleteDeReservaSolicitada_deveSerRejeitadoPorAusenciaDeDelete() {
        // arrange
        com.edualoca.domain.reserva.ReservaService reservaService = new com.edualoca.domain.reserva.ReservaService();
        com.edualoca.domain.usuario.Usuario autor = new com.edualoca.domain.usuario.Usuario(
                UUID.randomUUID(), "aluno@example.com", "hash", com.edualoca.domain.usuario.Perfil.SOLICITANTE);
        UUID reservaSolicitadaId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.excluir(autor, reservaSolicitadaId));

        // assert
        assertEquals(405, ex.getStatusHttp());
    }
}
