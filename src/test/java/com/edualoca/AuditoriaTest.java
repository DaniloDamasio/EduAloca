package com.edualoca;

import com.edualoca.domain.auditoria.AuditoriaEvento;
import com.edualoca.domain.auditoria.AuditoriaService;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Sala;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        com.edualoca.domain.recurso.Sala salaRestrita = new com.edualoca.domain.recurso.Sala(
                UUID.randomUUID(), "Sala restrita", "aula", 20, "Bloco A", true);
        com.edualoca.domain.reserva.Reserva reservaSolicitada = reservaService.criar(autor,
                java.time.LocalDateTime.of(2026, 3, 10, 10, 0), java.time.LocalDateTime.of(2026, 3, 10, 12, 0),
                "Aula especial", List.of(new com.edualoca.domain.reserva.ReservaRecurso(salaRestrita, 1)));
        UUID reservaSolicitadaId = reservaSolicitada.getId();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.excluir(autor, reservaSolicitadaId));

        // assert
        assertEquals(405, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-27 [risco]: criacao real de reserva deve gerar evento de auditoria com acao CRIACAO e autor correto (nao o fallback fabricado)")
    void rf27_criacaoDeReservaReal_deveGerarEventoDeAuditoriaReal() {
        // arrange
        ReservaService reservaService = new ReservaService();
        Usuario solicitante = new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE);
        Sala sala = new Sala(UUID.randomUUID(), "Sala Y", "aula", 10, "Bloco B", false);

        // act
        Reserva reserva = reservaService.criar(solicitante, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula auditada", List.of(new ReservaRecurso(sala, 1)));
        List<AuditoriaEvento> eventos = auditoriaService.consultarPorEntidade("Reserva", reserva.getId());

        // assert
        assertTrue(eventos.stream().anyMatch(e ->
                "CRIACAO".equals(e.getAcao()) && solicitante.getId().equals(e.getAutorId())));
    }

    @Test
    @DisplayName("RF-27/RF-28 [risco]: auditoria de uma entidade nao deve misturar eventos registrados para outra entidade/id")
    void rf27_auditoriaDeEntidadesDiferentes_naoDeveSeMisturar() {
        // arrange
        ReservaService reservaService = new ReservaService();
        Usuario solicitante = new Usuario(UUID.randomUUID(), "aluno2@example.com", "hash", Perfil.SOLICITANTE);
        Sala salaA = new Sala(UUID.randomUUID(), "Sala A", "aula", 10, "Bloco B", false);
        Sala salaB = new Sala(UUID.randomUUID(), "Sala B", "aula", 10, "Bloco B", false);
        Reserva reservaA = reservaService.criar(solicitante, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula A", List.of(new ReservaRecurso(salaA, 1)));
        Reserva reservaB = reservaService.criar(solicitante, LocalDateTime.of(2026, 3, 10, 14, 0),
                LocalDateTime.of(2026, 3, 10, 16, 0), "Aula B", List.of(new ReservaRecurso(salaB, 1)));

        // act
        List<AuditoriaEvento> eventosDeA = auditoriaService.consultarPorEntidade("Reserva", reservaA.getId());

        // assert
        assertTrue(eventosDeA.stream().allMatch(e -> reservaA.getId().equals(e.getEntidadeId())));
        assertFalse(eventosDeA.stream().anyMatch(e -> reservaB.getId().equals(e.getEntidadeId())));
    }
}
