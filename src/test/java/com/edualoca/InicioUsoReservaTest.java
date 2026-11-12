package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.RegistroHistoricoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.retirada.RegistroRetirada;
import com.edualoca.domain.retirada.RetiradaMaterialService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RF-18 / P-07 (decisao de 12/11/2026: inicio de uso manual, por Responsavel competente ou Administrador): a reserva so
 * chega a {@code EM_USO} a partir de {@code APROVADA}; reserva restrita {@code SOLICITADA} nunca pula a aprovacao.
 * Toda transicao gera historico e um evento de auditoria (RF-27).
 */
class InicioUsoReservaTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 5, 20, 14, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2026, 5, 20, 16, 0);

    private final ReservaService reservas = new ReservaService();
    private final Usuario solicitante = new Usuario(UUID.randomUUID(), "sol@example.com", "hash", Perfil.SOLICITANTE);
    private final Usuario responsavelBlocoA = new Usuario(Banco.RESPONSAVEL_BLOCO_A_ID, "responsavel@example.com", "hash", Perfil.RESPONSAVEL);
    private final Usuario responsavelBlocoB = new Usuario(Banco.RESPONSAVEL_BLOCO_B_ID, "responsavel.b@example.com", "hash", Perfil.RESPONSAVEL);
    private final Usuario administrador = new Usuario(Banco.ADMINISTRADOR_ID, "admin@example.com", "hash", Perfil.ADMINISTRADOR);

    private final Material projetor = new Material(UUID.randomUUID(), "Projetor Lab", "projetor", 1, false);

    private Reserva restritaSolicitada() {
        Banco.seedSeNecessario();
        Sala labRestrito = new Sala(UUID.randomUUID(), "Lab restrito", "laboratorio", 20, "Bloco A", true);
        Reserva reserva = reservas.criar(solicitante, INICIO, FIM, "Pratica de laboratorio",
                List.of(new ReservaRecurso(labRestrito, 1), new ReservaRecurso(projetor, 1)));
        assertEquals(EstadoReserva.SOLICITADA, reserva.getEstado(), "pre-condicao: restrita nasce SOLICITADA");
        return reserva;
    }

    private static long eventos(UUID reservaId, String acao) {
        return Banco.auditoria.stream()
                .filter(e -> reservaId.equals(e.getEntidadeId()) && acao.equals(e.getAcao())).count();
    }

    @Test
    @DisplayName("RF-18: reserva restrita SOLICITADA nao vai para EM_USO sem aprovacao (409, estado e historico intactos)")
    void restritaSolicitada_naoPulaAprovacao() {
        Reserva reserva = restritaSolicitada();
        int historicoAntes = reservas.consultarHistorico(administrador, reserva.getId()).size();

        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservas.iniciarUso(administrador, reserva.getId()));

        assertAll(
                () -> assertEquals(409, ex.getStatusHttp()),
                () -> assertEquals(EstadoReserva.SOLICITADA, reserva.getEstado()),
                () -> assertEquals(historicoAntes, reservas.consultarHistorico(administrador, reserva.getId()).size()),
                () -> assertEquals(0, eventos(reserva.getId(), "INICIO_USO")));
    }

    @Test
    @DisplayName("RF-18: depois de APROVADA, o Responsavel competente inicia o uso; historico APROVADA->EM_USO e um evento")
    void aprovada_iniciaUsoComHistoricoEAuditoria() {
        Reserva reserva = restritaSolicitada();
        reservas.aprovar(responsavelBlocoA, reserva.getId());

        reservas.iniciarUso(responsavelBlocoA, reserva.getId());

        List<RegistroHistoricoReserva> historico = reservas.consultarHistorico(responsavelBlocoA, reserva.getId());
        RegistroHistoricoReserva ultimo = historico.getLast();
        assertAll(
                () -> assertEquals(EstadoReserva.EM_USO, reserva.getEstado()),
                () -> assertEquals(EstadoReserva.APROVADA, ultimo.estadoAnterior()),
                () -> assertEquals(EstadoReserva.EM_USO, ultimo.estadoNovo()),
                () -> assertEquals(responsavelBlocoA.getId(), ultimo.usuarioId()),
                () -> assertEquals(1, eventos(reserva.getId(), "INICIO_USO")));
    }

    @Test
    @DisplayName("RF-18 + RF-24: fluxo real sem setEstado: SOLICITADA -> APROVADA -> EM_USO e entao a retirada e aceita")
    void fluxoReal_permiteRetiradaSoDepoisDoInicioDoUso() {
        RetiradaMaterialService retiradas = new RetiradaMaterialService();
        Reserva reserva = restritaSolicitada();

        assertEquals(409, assertThrows(RegraNegocioException.class,
                () -> retiradas.registrarRetirada(responsavelBlocoA, reserva.getId(), projetor.getId(), INICIO)).getStatusHttp());
        reservas.aprovar(administrador, reserva.getId());
        reservas.iniciarUso(administrador, reserva.getId());
        RegistroRetirada retirada = retiradas.registrarRetirada(responsavelBlocoA, reserva.getId(), projetor.getId(), INICIO);

        assertEquals(projetor.getId(), retirada.getItemId());
    }

    @Test
    @DisplayName("RF-18 / P-07: Solicitante e Responsavel de outro bloco nao iniciam o uso (403, estado intacto)")
    void soResponsavelCompetenteOuAdministrador() {
        Reserva reserva = restritaSolicitada();
        reservas.aprovar(responsavelBlocoA, reserva.getId());

        RegraNegocioException doSolicitante = assertThrows(RegraNegocioException.class,
                () -> reservas.iniciarUso(solicitante, reserva.getId()));
        RegraNegocioException doOutroBloco = assertThrows(RegraNegocioException.class,
                () -> reservas.iniciarUso(responsavelBlocoB, reserva.getId()));

        assertAll(
                () -> assertEquals(403, doSolicitante.getStatusHttp()),
                () -> assertEquals(403, doOutroBloco.getStatusHttp()),
                () -> assertEquals(EstadoReserva.APROVADA, reserva.getEstado()));
    }

    @Test
    @DisplayName("RF-18: REJEITADA, CANCELADA, EM_USO e inexistente nao iniciam uso (409)")
    void foraDeAprovada_409() {
        Reserva rejeitada = restritaSolicitada();
        reservas.rejeitar(responsavelBlocoA, rejeitada.getId(), "laboratorio reservado para banca");
        Reserva cancelada = restritaSolicitada();
        reservas.cancelar(solicitante, cancelada.getId(), "aula remarcada");
        Reserva emUso = restritaSolicitada();
        reservas.aprovar(administrador, emUso.getId());
        reservas.iniciarUso(administrador, emUso.getId());

        for (UUID id : List.of(rejeitada.getId(), cancelada.getId(), emUso.getId(), UUID.randomUUID())) {
            RegraNegocioException ex = assertThrows(RegraNegocioException.class, () -> reservas.iniciarUso(administrador, id));
            assertThat(ex.getStatusHttp()).as("reserva %s", id).isEqualTo(409);
        }
    }
}
