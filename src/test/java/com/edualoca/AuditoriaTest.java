package com.edualoca;

import com.edualoca.domain.auditoria.AuditoriaEvento;
import com.edualoca.domain.auditoria.AuditoriaService;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.CadastroRecursoService;
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
    @DisplayName("DEF-011: entidade sem nenhum evento devolve lista vazia, sem evento inventado")
    void def011_entidadeSemEventos_devolveListaVazia() {
        // arrange
        UUID reservaQueNuncaExistiu = UUID.randomUUID();
        UUID salaQueNuncaExistiu = UUID.randomUUID();

        // act
        List<AuditoriaEvento> eventosReserva = auditoriaService.consultarPorEntidade("Reserva", reservaQueNuncaExistiu);
        List<AuditoriaEvento> eventosSala = auditoriaService.consultarPorEntidade("Sala", salaQueNuncaExistiu);

        // assert
        assertTrue(eventosReserva.isEmpty(), "eventos inventados para reserva: " + eventosReserva);
        assertTrue(eventosSala.isEmpty(), "eventos inventados para sala: " + eventosSala);
    }

    /** Reescrito (DEF-011): antes consultava um id aleatorio e so passava pelo evento inventado na consulta. */
    @Test
    @DisplayName("RF-27-T1: aprovacao real de reserva gera evento APROVACAO com o Responsavel como autor")
    void rf27T1_transicaoDeEstadoDeReserva_deveGerarAuditoria() {
        // arrange
        ReservaService reservaService = new ReservaService();
        Usuario solicitante = new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE);
        Usuario responsavel = new Usuario(Banco.RESPONSAVEL_BLOCO_A_ID, "resp@example.com", "hash", Perfil.RESPONSAVEL);
        Sala salaRestrita = new Sala(UUID.randomUUID(), "Sala restrita", "aula", 20, "Bloco A", true);
        Reserva reserva = reservaService.criar(solicitante, LocalDateTime.of(2026, 3, 12, 10, 0),
                LocalDateTime.of(2026, 3, 12, 12, 0), "Aula especial", List.of(new ReservaRecurso(salaRestrita, 1)));

        // act
        reservaService.aprovar(responsavel, reserva.getId());
        List<AuditoriaEvento> eventos = auditoriaService.consultarPorEntidade("Reserva", reserva.getId());

        // assert
        assertEquals(1, eventos.stream().filter(e -> "APROVACAO".equals(e.getAcao())
                && responsavel.getId().equals(e.getAutorId())).count());
    }

    /** Reescrito (DEF-011): antes consultava um id aleatorio e so passava pelo evento inventado na consulta. */
    @Test
    @DisplayName("RF-28-T1: cadastro real de sala gera entrada de auditoria CRIACAO para a sala")
    void rf28T1_alteracaoDeCadastro_deveGerarAuditoria() {
        // arrange
        CadastroRecursoService cadastro = new CadastroRecursoService();

        // act
        Sala sala = cadastro.cadastrarSala("Lab auditado", "laboratorio", 25, "Bloco C", false);
        List<AuditoriaEvento> eventos = auditoriaService.consultarPorEntidade("Sala", sala.getId());

        // assert
        assertEquals(List.of("CRIACAO"), eventos.stream().map(AuditoriaEvento::getAcao).toList());
    }

    /** Reescrito (DEF-011): antes consultava um id aleatorio e so passava pelo evento inventado na consulta. */
    @Test
    @DisplayName("RF-28-T2: rejeicao real por sobreposicao grava REJEICAO_SOBREPOSICAO com quem tentou como autor")
    void rf28T2_rejeicaoAutomaticaPorSobreposicao_deveGravarAcaoEspecifica() {
        // arrange
        ReservaService reservaService = new ReservaService();
        Usuario primeiro = new Usuario(UUID.randomUUID(), "primeiro@example.com", "hash", Perfil.SOLICITANTE);
        Usuario segundo = new Usuario(UUID.randomUUID(), "segundo@example.com", "hash", Perfil.SOLICITANTE);
        Sala sala = new Sala(UUID.randomUUID(), "Sala disputada", "aula", 30, "Bloco B", false);
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 13, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 13, 12, 0);
        reservaService.criar(primeiro, inicio, fim, "Primeira", List.of(new ReservaRecurso(sala, 1)));

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.criar(segundo, inicio, fim, "Segunda", List.of(new ReservaRecurso(sala, 1))));

        // assert
        assertEquals(409, ex.getStatusHttp());
        AuditoriaEvento rejeicao = Banco.auditoria.stream()
                .filter(e -> "REJEICAO_SOBREPOSICAO".equals(e.getAcao()) && segundo.getId().equals(e.getAutorId()))
                .findFirst().orElseThrow(() -> new AssertionError("rejeicao por sobreposicao nao auditada"));
        List<AuditoriaEvento> eventos = auditoriaService.consultarPorEntidade("Reserva", rejeicao.getEntidadeId());
        assertEquals(List.of("REJEICAO_SOBREPOSICAO"), eventos.stream().map(AuditoriaEvento::getAcao).toList());
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
    @DisplayName("RF-30-T2 [Q22 resolvida pela matriz de autorizacao, P-3]: DELETE de reserva em SOLICITADA e recusado com 403, como nos demais estados (DEF-006)")
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

        // assert: era 405 enquanto a Q22 estava aberta; a rota DELETE existe, entao 405 nao se aplica (DEF-006)
        assertEquals(403, ex.getStatusHttp());
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
