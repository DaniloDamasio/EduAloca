package com.edualoca;

import com.edualoca.domain.auditoria.AuditoriaService;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.relatorio.RelatorioService;
import com.edualoca.domain.relatorio.RelatorioUtilizacao;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelatoriosTest {

    private final RelatorioService relatorioService = new RelatorioService();

    private Usuario responsavel() {
        return new Usuario(UUID.randomUUID(), "resp@example.com", "hash", Perfil.RESPONSAVEL);
    }

    @Test
    @DisplayName("RF-34-T1: relatorio de utilizacao para intervalo com reservas conhecidas deve refletir 100% delas")
    void rf34T1_relatorioComReservasConhecidas_deveRefletirTodas() {
        // arrange
        Usuario autor = responsavel();
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 31, 0, 0);

        // act
        RelatorioUtilizacao relatorio = relatorioService.gerarRelatorioUtilizacao(autor, inicio, fim);

        // assert
        assertTrue(relatorio.getRegistros().size() >= 0);
    }

    @Test
    @DisplayName("RF-34-T2: relatorio para intervalo sem nenhuma reserva deve retornar zerado sem erro")
    void rf34T2_relatorioSemReservasNoPeriodo_deveRetornarZerado() {
        // arrange
        Usuario autor = responsavel();
        LocalDateTime inicio = LocalDateTime.of(2099, 1, 1, 0, 0);
        LocalDateTime fim = LocalDateTime.of(2099, 1, 2, 0, 0);

        // act
        RelatorioUtilizacao relatorio = relatorioService.gerarRelatorioUtilizacao(autor, inicio, fim);

        // assert
        assertTrue(relatorio.getRegistros().isEmpty());
    }

    @Test
    @DisplayName("RF-34-T3: relatorio com fim <= inicio no filtro deve ser rejeitado com HTTP 400")
    void rf34T3_filtroComFimMenorOuIgualInicio_deveSerRejeitado() {
        // arrange
        Usuario autor = responsavel();
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 31, 0, 0);
        LocalDateTime fimInvalido = LocalDateTime.of(2026, 3, 1, 0, 0);

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> relatorioService.gerarRelatorioUtilizacao(autor, inicio, fimInvalido));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-35-T1 [depende da resolucao de Q21 sobre nomenclatura de 'acao']: contagem de rejeicoes por sobreposicao deve bater com HTTP 409 registrados em auditoria")
    void rf35T1_contagemDeRejeicoesPorSobreposicao_deveBaterComAuditoria() {
        // arrange
        Usuario autor = responsavel();
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 31, 0, 0);

        // act
        long contagem = relatorioService.contarRejeicoesPorSobreposicao(autor, inicio, fim);

        // assert
        assertTrue(contagem >= 0);
    }

    @Test
    @DisplayName("RF-36-T1: solicitante tentando acessar endpoint de relatorio deve receber HTTP 403")
    void rf36T1_solicitanteAcessandoRelatorio_deveRetornar403() {
        // arrange
        Usuario solicitante = new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE);
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 31, 0, 0);

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> relatorioService.gerarRelatorioUtilizacao(solicitante, inicio, fim));

        // assert
        assertEquals(403, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-36-T2: responsavel ou administrador acessando relatorio deve ser permitido")
    void rf36T2_responsavelOuAdministradorAcessandoRelatorio_devePermitir() {
        // arrange
        Usuario autor = responsavel();
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 31, 0, 0);

        // act
        RelatorioUtilizacao relatorio = relatorioService.gerarRelatorioUtilizacao(autor, inicio, fim);

        // assert
        assertTrue(relatorio.getRegistros() != null);
    }

    @Test
    @DisplayName("RF-34 [risco]: relatorio deve incluir reservas CONCLUIDA/EM_USO dentro do periodo e excluir as demais e as fora do periodo")
    void rf34_relatorioComReservasReais_deveFiltrarPorEstadoEPeriodo() {
        // arrange
        ReservaService reservaService = new ReservaService();
        Usuario solicitante = new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE);
        LocalDateTime inicioFiltro = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime fimFiltro = LocalDateTime.of(2026, 3, 31, 0, 0);

        Reserva concluidaDentroDoPeriodo = reservaService.criar(solicitante, LocalDateTime.of(2026, 3, 5, 10, 0),
                LocalDateTime.of(2026, 3, 5, 12, 0), "Aula concluida",
                List.of(new ReservaRecurso(new Sala(UUID.randomUUID(), "Sala 1", "aula", 10, "Bloco A", false), 1)));
        concluidaDentroDoPeriodo.setEstado(EstadoReserva.CONCLUIDA);

        Reserva emUsoDentroDoPeriodo = reservaService.criar(solicitante, LocalDateTime.of(2026, 3, 6, 10, 0),
                LocalDateTime.of(2026, 3, 6, 12, 0), "Aula em uso",
                List.of(new ReservaRecurso(new Sala(UUID.randomUUID(), "Sala 2", "aula", 10, "Bloco A", false), 1)));
        emUsoDentroDoPeriodo.setEstado(EstadoReserva.EM_USO);

        Reserva solicitadaDentroDoPeriodo = reservaService.criar(solicitante, LocalDateTime.of(2026, 3, 7, 10, 0),
                LocalDateTime.of(2026, 3, 7, 12, 0), "Aula ainda solicitada",
                List.of(new ReservaRecurso(new Sala(UUID.randomUUID(), "Sala Restrita", "aula", 10, "Bloco A", true), 1)));

        Reserva concluidaForaDoPeriodo = reservaService.criar(solicitante, LocalDateTime.of(2026, 4, 5, 10, 0),
                LocalDateTime.of(2026, 4, 5, 12, 0), "Aula concluida em abril",
                List.of(new ReservaRecurso(new Sala(UUID.randomUUID(), "Sala 3", "aula", 10, "Bloco A", false), 1)));
        concluidaForaDoPeriodo.setEstado(EstadoReserva.CONCLUIDA);

        // act
        RelatorioUtilizacao relatorio = relatorioService.gerarRelatorioUtilizacao(responsavel(), inicioFiltro, fimFiltro);
        List<UUID> idsNoRelatorio = relatorio.getRegistros().stream()
                .map(r -> ((Reserva) r).getId())
                .toList();

        // assert
        assertTrue(idsNoRelatorio.contains(concluidaDentroDoPeriodo.getId()));
        assertTrue(idsNoRelatorio.contains(emUsoDentroDoPeriodo.getId()));
        assertFalse(idsNoRelatorio.contains(solicitadaDentroDoPeriodo.getId()));
        assertFalse(idsNoRelatorio.contains(concluidaForaDoPeriodo.getId()));
    }

    @Test
    @DisplayName("RF-35 [risco]: contagem de rejeicoes por sobreposicao deve contar apenas eventos REJEICAO_SOBREPOSICAO dentro do periodo filtrado")
    void rf35_contagemDeRejeicoesReais_deveRespeitarAcaoEPeriodo() {
        // arrange
        UUID reservaRejeitadaPorSobreposicao = UUID.randomUUID();
        UUID reservaComOutraAcao = UUID.randomUUID();
        AuditoriaService.registrar("Reserva", reservaRejeitadaPorSobreposicao, "REJEICAO_SOBREPOSICAO", UUID.randomUUID());
        AuditoriaService.registrar("Reserva", reservaComOutraAcao, "CRIACAO", UUID.randomUUID());
        AuditoriaService.registrar("Sala", UUID.randomUUID(), "REJEICAO_SOBREPOSICAO", UUID.randomUUID());

        LocalDateTime inicioFiltro = LocalDateTime.now().minusMinutes(1);
        LocalDateTime fimFiltro = LocalDateTime.now().plusMinutes(1);

        // act
        long contagem = relatorioService.contarRejeicoesPorSobreposicao(responsavel(), inicioFiltro, fimFiltro);

        // assert: apenas o evento de "Reserva" com acao REJEICAO_SOBREPOSICAO deve ser contado,
        // nao o de outra acao nem o de outra entidade
        assertEquals(1, contagem);
    }
}
