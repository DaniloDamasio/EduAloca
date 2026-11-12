package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.notificacao.NotificacaoEnviada;
import com.edualoca.domain.notificacao.NotificacaoService;
import com.edualoca.domain.notificacao.StatusNotificacao;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.HistoricoReservaRepositorioEmMemoria;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaRepositorioEmMemoria;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.reserva.Transacao;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RF-31 / RF-32 / RF-33 nos fluxos reais: a notificacao deixa de ser so um servico testado isoladamente e passa a ser
 * disparada pelo {@link ReservaService}. O notificador externo e um duble que registra o estado de cada envio.
 */
class NotificacaoNosFluxosTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 9, 14, 8, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2026, 9, 14, 10, 0);

    private final List<EstadoReserva> enviados = new CopyOnWriteArrayList<>();
    private volatile boolean provedorFora = false;

    private final Usuario solicitante = new Usuario(UUID.randomUUID(), "sol@example.com", "hash", Perfil.SOLICITANTE);
    private final Usuario responsavelA = new Usuario(Banco.RESPONSAVEL_BLOCO_A_ID, "responsavel@example.com", "hash", Perfil.RESPONSAVEL);

    private ReservaService reservas;

    @BeforeEach
    void preparar() {
        Banco.seedSeNecessario();
        NotificacaoService notificacao = new NotificacaoService(reserva -> {
            if (provedorFora) {
                throw new IllegalStateException("provedor externo fora do ar");
            }
            enviados.add(reserva.getEstado());
            return true;
        });
        reservas = new ReservaService(Clock.systemDefaultZone(), new ReservaRepositorioEmMemoria(),
                new HistoricoReservaRepositorioEmMemoria(), Transacao.direta(), notificacao);
    }

    private Reserva restrita() {
        Sala lab = new Sala(UUID.randomUUID(), "Lab restrito", "laboratorio", 20, "Bloco A", true);
        return reservas.criar(solicitante, INICIO, FIM, "Pratica", List.of(new ReservaRecurso(lab, 1)));
    }

    private static List<NotificacaoEnviada> para(UUID destinatario) {
        return Banco.notificacoes.values().stream().filter(n -> destinatario.equals(n.getDestinatarioId())).toList();
    }

    @Test
    @DisplayName("RF-32: reserva restrita criada notifica o Responsavel competente; o Solicitante nao recebe nada ainda")
    void criacaoRestrita_notificaResponsavelCompetente() {
        long antes = para(Banco.RESPONSAVEL_BLOCO_A_ID).size();

        restrita();

        assertAll(
                () -> assertEquals(antes + 1, para(Banco.RESPONSAVEL_BLOCO_A_ID).size()),
                () -> assertThat(para(solicitante.getId())).isEmpty(),
                () -> assertThat(enviados).isEmpty());
    }

    @Test
    @DisplayName("RF-31: aprovar, iniciar uso, rejeitar e cancelar notificam o Solicitante com o estado novo")
    void cadaTransicao_notificaOSolicitante() {
        Reserva aprovada = restrita();
        reservas.aprovar(responsavelA, aprovada.getId());
        reservas.iniciarUso(responsavelA, aprovada.getId());
        Reserva rejeitada = restrita();
        reservas.rejeitar(responsavelA, rejeitada.getId(), "laboratorio em banca");
        Reserva cancelada = restrita();
        reservas.cancelar(solicitante, cancelada.getId(), "aula remarcada");

        assertAll(
                () -> assertThat(enviados).containsExactly(EstadoReserva.APROVADA, EstadoReserva.EM_USO,
                        EstadoReserva.REJEITADA, EstadoReserva.CANCELADA),
                () -> assertThat(para(solicitante.getId())).hasSize(4)
                        .allMatch(n -> n.getStatus() == StatusNotificacao.ENVIADA));
    }

    @Test
    @DisplayName("RF-33: provedor fora do ar nao desfaz a aprovacao; a notificacao fica registrada como FALHA")
    void falhaDoProvedor_naoReverteTransicao() {
        Reserva reserva = restrita();
        provedorFora = true;

        Reserva aprovada = reservas.aprovar(responsavelA, reserva.getId());

        assertAll(
                () -> assertEquals(EstadoReserva.APROVADA, aprovada.getEstado()),
                () -> assertEquals(EstadoReserva.APROVADA, reservas.consultarHistorico(responsavelA, reserva.getId()).getLast().estadoNovo()),
                () -> assertThat(para(solicitante.getId())).singleElement()
                        .extracting(NotificacaoEnviada::getStatus).isEqualTo(StatusNotificacao.FALHA));
    }

    @Test
    @DisplayName("RF-31: transicao recusada (403) nao notifica ninguem")
    void transicaoRecusada_naoNotifica() {
        Reserva reserva = restrita();

        assertThrows(RegraNegocioException.class, () -> reservas.aprovar(solicitante, reserva.getId()));

        assertAll(
                () -> assertThat(enviados).isEmpty(),
                () -> assertThat(para(solicitante.getId())).isEmpty());
    }
}
