package com.edualoca;

import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.retirada.CondicaoItem;
import com.edualoca.domain.retirada.RegistroRetirada;
import com.edualoca.domain.retirada.RetiradaMaterialService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DEF-014 / RF-24 / RF-25: a retirada vincula reserva, <b>item</b>, autor e instante; o ciclo aberto e por item; a
 * devolucao so encerra ciclo aberto. Tambem fecha as lacunas R1, R3 e R4 da mutacao manual.
 */
class RetiradaVinculoItemTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 4, 10, 10, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2026, 4, 10, 12, 0);

    private final RetiradaMaterialService retiradas = new RetiradaMaterialService();
    private final ReservaService reservas = new ReservaService();
    private final Usuario responsavel = new Usuario(UUID.randomUUID(), "resp@example.com", "hash", Perfil.RESPONSAVEL);

    private final Material projetor = new Material(UUID.randomUUID(), "Projetor", "projetor", 1, false);
    private final Material caixaDeSom = new Material(UUID.randomUUID(), "Caixa de som", "audio", 1, false);

    private Reserva reservaComMateriais() {
        Usuario solicitante = new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE);
        Sala sala = new Sala(UUID.randomUUID(), "Sala Y", "aula", 30, "Bloco A", false);
        return reservas.criar(solicitante, INICIO, FIM, "Aula com projetor e som", List.of(
                new ReservaRecurso(sala, 1), new ReservaRecurso(projetor, 1), new ReservaRecurso(caixaDeSom, 1)));
    }

    private Reserva reservaEmUso() {
        Reserva reserva = reservaComMateriais();
        reserva.setEstado(EstadoReserva.EM_USO);
        return reserva;
    }

    @Test
    @DisplayName("RF-24: retirada grava reserva, item, autor e instante informados")
    void retirada_vinculaReservaItemAutorEInstante() {
        Reserva reserva = reservaEmUso();
        LocalDateTime instante = INICIO.plusMinutes(5);

        RegistroRetirada registro = retiradas.registrarRetirada(responsavel, reserva.getId(), projetor.getId(), instante);

        assertAll(
                () -> assertEquals(reserva.getId(), registro.getReservaId()),
                () -> assertEquals(projetor.getId(), registro.getItemId()),
                () -> assertEquals(responsavel.getId(), registro.getAutorId()),
                () -> assertEquals(instante, registro.getDataRetirada()));
    }

    @Test
    @DisplayName("RF-24 (R1): reserva real APROVADA, ainda nao EM_USO, recusa retirada com 409")
    void retiradaDeReservaAprovada_eRecusada() {
        Reserva aprovada = reservaComMateriais();
        assertEquals(EstadoReserva.APROVADA, aprovada.getEstado(), "pre-condicao: sala e materiais livres nascem APROVADA");

        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> retiradas.registrarRetirada(responsavel, aprovada.getId(), projetor.getId(), INICIO));

        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("DEF-014: item que nao faz parte da reserva recusa retirada com 409")
    void itemForaDaReserva_eRecusado() {
        Reserva reserva = reservaEmUso();

        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> retiradas.registrarRetirada(responsavel, reserva.getId(), UUID.randomUUID(), INICIO));

        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("DEF-014: dois itens da mesma reserva sao retirados, cada um no seu ciclo")
    void doisItensDaMesmaReserva_saoRetirados() {
        Reserva reserva = reservaEmUso();

        RegistroRetirada doProjetor = retiradas.registrarRetirada(responsavel, reserva.getId(), projetor.getId(), INICIO);
        RegistroRetirada daCaixa = retiradas.registrarRetirada(responsavel, reserva.getId(), caixaDeSom.getId(), INICIO);

        assertEquals(projetor.getId(), doProjetor.getItemId());
        assertEquals(caixaDeSom.getId(), daCaixa.getItemId());
    }

    @Test
    @DisplayName("RF-24-T3: o mesmo item com ciclo aberto nao e retirado de novo; depois da devolucao, pode")
    void mesmoItem_soVoltaASerRetiradoDepoisDaDevolucao() {
        Reserva reserva = reservaEmUso();
        RegistroRetirada primeira = retiradas.registrarRetirada(responsavel, reserva.getId(), projetor.getId(), INICIO);

        assertEquals(409, assertThrows(RegraNegocioException.class,
                () -> retiradas.registrarRetirada(responsavel, reserva.getId(), projetor.getId(), INICIO)).getStatusHttp());

        retiradas.registrarDevolucao(responsavel, primeira.getId(), INICIO.plusMinutes(30), CondicaoItem.INTEGRO);
        assertNotNull(retiradas.registrarRetirada(responsavel, reserva.getId(), projetor.getId(), INICIO.plusMinutes(40)));
    }

    @Test
    @DisplayName("DEF-014 / RF-25: devolver um ciclo ja encerrado e recusado com 409 e nao reescreve a devolucao")
    void devolucaoDeCicloEncerrado_eRecusadaSemSobrescrever() {
        Reserva reserva = reservaEmUso();
        RegistroRetirada registro = retiradas.registrarRetirada(responsavel, reserva.getId(), projetor.getId(), INICIO);
        LocalDateTime devolucao = INICIO.plusHours(1);
        retiradas.registrarDevolucao(responsavel, registro.getId(), devolucao, CondicaoItem.DANIFICADO);

        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> retiradas.registrarDevolucao(responsavel, registro.getId(), devolucao.plusHours(1), CondicaoItem.INTEGRO));

        assertAll(
                () -> assertEquals(409, ex.getStatusHttp()),
                () -> assertEquals(devolucao, registro.getDataDevolucao()),
                () -> assertEquals(CondicaoItem.DANIFICADO, registro.getCondicaoDevolucao()));
    }
}
