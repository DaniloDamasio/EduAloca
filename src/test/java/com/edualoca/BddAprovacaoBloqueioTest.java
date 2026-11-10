package com.edualoca;

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
}
