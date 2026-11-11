package com.edualoca;

import com.edualoca.domain.auditoria.AuditoriaEvento;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cenarios de DOCS/testes/bdd-aprovacao-bloqueio.md implementados por TDD estrito.
 */
class BddAprovacaoBloqueioTest {

    private static final LocalDateTime DEZ_MARCO_10H = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final LocalDateTime DEZ_MARCO_12H = LocalDateTime.of(2026, 3, 10, 12, 0);
    private static final LocalDateTime INICIO_MANUTENCAO = LocalDateTime.of(2026, 3, 10, 8, 0, 0);
    private static final LocalDateTime FIM_MANUTENCAO = LocalDateTime.of(2026, 3, 10, 18, 0, 0);

    /** 09/03/2026 11:30 em Sao Paulo; o teste nunca depende do relogio real. */
    private static final Clock RELOGIO_FIXO =
            Clock.fixed(Instant.parse("2026-03-09T14:30:00Z"), ZoneId.of("America/Sao_Paulo"));

    private final ReservaService reservaService = new ReservaService(RELOGIO_FIXO);

    private final Usuario ana = new Usuario(UUID.randomUUID(), "ana@example.com", "hash", Perfil.SOLICITANTE);
    /** Responsavel competente pelo Bloco B, onde ficam as salas restritas destes cenarios (P-2, DEF-010). */
    private final Usuario rui = new Usuario(Banco.RESPONSAVEL_BLOCO_B_ID, "rui@example.com", "hash", Perfil.RESPONSAVEL);
    private final Sala labQuimica = new Sala(UUID.randomUUID(), "Lab Quimica", "laboratorio", 20, "Bloco B", true);

    private Reserva reservaSolicitadaDaAnaNoLabQuimica() {
        return reservaService.criar(ana, DEZ_MARCO_10H, DEZ_MARCO_12H, "Aula pratica",
                List.of(new ReservaRecurso(labQuimica, 1)));
    }

    @Test
    @DisplayName("BDD-R1-06: rejeicao com justificativa deixa a justificativa visivel para o solicitante")
    void rejeicaoComJustificativa_tornaJustificativaVisivelAoSolicitante() {
        Reserva reserva = reservaSolicitadaDaAnaNoLabQuimica();

        reservaService.rejeitar(rui, reserva.getId(), "Laboratorio reservado para banca");

        Reserva vistaPelaAna = reservaService.consultar(ana, reserva.getId());
        assertEquals("Laboratorio reservado para banca", vistaPelaAna.getJustificativaRejeicao());
    }

    @ParameterizedTest(name = "{0} \"{1}\" em manutencao recusa reserva com HTTP 409")
    @CsvSource({
            "SALA,      Lab 3",
            "MATERIAL,  Projetor P-07",
            "PROFESSOR, Prof. Lima"
    })
    @DisplayName("BDD-R3-05: reserva de qualquer tipo de recurso durante manutencao e recusada com HTTP 409")
    void reservaDeRecursoDeQualquerTipoEmManutencao_eRecusadaCom409(String tipo, String nome) {
        CadastroRecursoService cadastro = new CadastroRecursoService(RELOGIO_FIXO);
        Object recurso = switch (tipo) {
            case "SALA" -> {
                Sala sala = cadastro.cadastrarSala(nome, "laboratorio", 30, "Bloco C", false);
                cadastro.cadastrarManutencao(sala, INICIO_MANUTENCAO, FIM_MANUTENCAO, "Bloqueio");
                yield sala;
            }
            case "MATERIAL" -> {
                var material = cadastro.cadastrarMaterial(nome, "projetor", 1, false);
                cadastro.cadastrarManutencao(material, INICIO_MANUTENCAO, FIM_MANUTENCAO, "Bloqueio");
                yield material;
            }
            case "PROFESSOR" -> {
                var professor = cadastro.cadastrarProfessor(nome, "LIMA-001", List.of(new Competencia("Quimica")));
                cadastro.cadastrarManutencao(professor, INICIO_MANUTENCAO, FIM_MANUTENCAO, "Bloqueio");
                yield professor;
            }
            default -> throw new IllegalArgumentException(tipo);
        };

        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.criar(ana, DEZ_MARCO_10H, DEZ_MARCO_12H, "Aula",
                        List.of(new ReservaRecurso(recurso, 1))));

        assertEquals(409, ex.getStatusHttp());
    }

    @ParameterizedTest(name = "{0} --{1}--> {2} gera um unico evento {3} com ator e instante do relogio")
    @CsvSource({
            "SOLICITADA, aprovar,  APROVADA,  APROVACAO",
            "SOLICITADA, rejeitar, REJEITADA, REJEICAO",
            "SOLICITADA, cancelar, CANCELADA, CANCELAMENTO",
            "APROVADA,   cancelar, CANCELADA, CANCELAMENTO"
    })
    @DisplayName("BDD-R4-01: transicao permitida muda o estado e gera exatamente um evento de auditoria com ator e instante")
    void transicaoPermitida_mudaEstadoEGeraUmEventoDeAuditoriaComAtorEInstante(
            EstadoReserva origem, String acao, EstadoReserva destino, String acaoAuditoria) {
        ReservaService servico = new ReservaService(RELOGIO_FIXO);
        Sala sala = new Sala(UUID.randomUUID(), "Sala", "aula", 20, "Bloco B", origem == EstadoReserva.SOLICITADA);
        Reserva reserva = servico.criar(ana, DEZ_MARCO_10H, DEZ_MARCO_12H, "Aula", List.of(new ReservaRecurso(sala, 1)));
        assertEquals(origem, reserva.getEstado());

        Usuario ator = acao.equals("cancelar") ? ana : rui;
        executar(servico, acao, ator, reserva);

        assertEquals(destino, reserva.getEstado());
        List<AuditoriaEvento> eventosDaTransicao = Banco.auditoria.stream()
                .filter(e -> e.getEntidadeId().equals(reserva.getId()))
                .filter(e -> !e.getAcao().equals("CRIACAO"))
                .toList();
        assertEquals(1, eventosDaTransicao.size());
        AuditoriaEvento evento = eventosDaTransicao.get(0);
        assertEquals(acaoAuditoria, evento.getAcao());
        assertEquals(ator.getId(), evento.getAutorId());
        assertEquals(LocalDateTime.now(RELOGIO_FIXO), evento.getDataHora());
    }

    private static void executar(ReservaService servico, String acao, Usuario ator, Reserva reserva) {
        switch (acao) {
            case "aprovar" -> servico.aprovar(ator, reserva.getId());
            case "rejeitar" -> servico.rejeitar(ator, reserva.getId(), "Laboratorio reservado para banca");
            case "cancelar" -> servico.cancelar(ator, reserva.getId(), "Aula remarcada");
            default -> throw new IllegalArgumentException(acao);
        }
    }
}
