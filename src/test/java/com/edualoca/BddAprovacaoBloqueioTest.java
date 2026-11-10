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

    private final ReservaService reservaService = new ReservaService();

    @Test
    @DisplayName("BDD-R1-06: rejeicao com justificativa deixa a justificativa visivel para o solicitante")
    void rejeicaoComJustificativa_tornaJustificativaVisivelAoSolicitante() {
        Usuario ana = new Usuario(UUID.randomUUID(), "ana@example.com", "hash", Perfil.SOLICITANTE);
        Usuario rui = new Usuario(UUID.randomUUID(), "rui@example.com", "hash", Perfil.RESPONSAVEL);
        Sala labQuimica = new Sala(UUID.randomUUID(), "Lab Quimica", "laboratorio", 20, "Bloco B", true);
        Reserva reserva = reservaService.criar(ana, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula pratica", List.of(new ReservaRecurso(labQuimica, 1)));

        reservaService.rejeitar(rui, reserva.getId(), "Laboratorio reservado para banca");

        Reserva vistaPelaAna = reservaService.consultar(ana, reserva.getId());
        assertEquals("Laboratorio reservado para banca", vistaPelaAna.getJustificativaRejeicao());
    }
}
