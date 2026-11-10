package com.edualoca;

import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
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

    private final ReservaService reservaService = new ReservaService();

    private final Usuario ana = new Usuario(UUID.randomUUID(), "ana@example.com", "hash", Perfil.SOLICITANTE);
    private final Usuario rui = new Usuario(UUID.randomUUID(), "rui@example.com", "hash", Perfil.RESPONSAVEL);
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
        CadastroRecursoService cadastro = new CadastroRecursoService();
        LocalDateTime inicioManutencao = LocalDateTime.of(2026, 3, 10, 8, 0, 0);
        LocalDateTime fimManutencao = LocalDateTime.of(2026, 3, 10, 18, 0, 0);
        Object recurso = switch (tipo) {
            case "SALA" -> {
                Sala sala = cadastro.cadastrarSala(nome, "laboratorio", 30, "Bloco C", false);
                cadastro.cadastrarManutencao(sala, inicioManutencao, fimManutencao, "Bloqueio");
                yield sala;
            }
            case "MATERIAL" -> {
                var material = cadastro.cadastrarMaterial(nome, "projetor", 1, false);
                cadastro.cadastrarManutencao(material, inicioManutencao, fimManutencao, "Bloqueio");
                yield material;
            }
            case "PROFESSOR" -> {
                var professor = cadastro.cadastrarProfessor(nome, "LIMA-001", List.of(new Competencia("Quimica")));
                cadastro.cadastrarManutencao(professor, inicioManutencao, fimManutencao, "Bloqueio");
                yield professor;
            }
            default -> throw new IllegalArgumentException(tipo);
        };

        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.criar(ana, DEZ_MARCO_10H, DEZ_MARCO_12H, "Aula",
                        List.of(new ReservaRecurso(recurso, 1))));

        assertEquals(409, ex.getStatusHttp());
    }
}
