package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.RegistroHistoricoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import com.edualoca.suporte.RelogioAjustavel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P-08, complemento automatico (decisao de 12/11/2026): a reserva que chega ao fim do horario ainda {@code APROVADA}
 * vira {@code NAO_COMPARECEU} sem acao humana, com historico e auditoria em nome do sistema. O registro manual
 * (inicio + 15 min) continua coberto por {@link ConclusaoNaoComparecimentoTest}.
 */
class NaoComparecimentoAutomaticoTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 10, 5, 8, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2026, 10, 5, 10, 0);

    private final RelogioAjustavel relogio = new RelogioAjustavel(INICIO.minusDays(1).atZone(ZONA).toInstant(), ZONA);
    private final ReservaService reservas = new ReservaService(relogio);
    private final Usuario solicitante = new Usuario(UUID.randomUUID(), "sol@example.com", "hash", Perfil.SOLICITANTE);
    private final Usuario administrador = new Usuario(Banco.ADMINISTRADOR_ID, "admin@example.com", "hash", Perfil.ADMINISTRADOR);

    @BeforeEach
    void semear() {
        Banco.seedSeNecessario();
    }

    private Reserva aprovada(LocalDateTime inicio, LocalDateTime fim) {
        Sala sala = new Sala(UUID.randomUUID(), "Sala " + UUID.randomUUID(), "aula", 30, "Bloco A", false);
        Reserva reserva = reservas.criar(solicitante, inicio, fim, "Aula", List.of(new ReservaRecurso(sala, 1)));
        assertEquals(EstadoReserva.APROVADA, reserva.getEstado(), "pre-condicao: sala comum nasce APROVADA");
        return reserva;
    }

    private void agora(LocalDateTime instante) {
        relogio.definir(instante.atZone(ZONA).toInstant());
    }

    private static long eventos(UUID reservaId, String acao) {
        return Banco.auditoria.stream().filter(e -> reservaId.equals(e.getEntidadeId()) && acao.equals(e.getAcao())).count();
    }

    @ParameterizedTest(name = "{0} min em relacao ao fim -> encerrada={1}")
    @CsvSource({"-1, false", "0, true", "1, true"})
    @DisplayName("APROVADA vira NAO_COMPARECEU a partir do fim do horario (borda inclusiva)")
    void bordaDoFim(long minutosEmRelacaoAoFim, boolean encerrada) {
        Reserva reserva = aprovada(INICIO, FIM);
        agora(FIM.plusMinutes(minutosEmRelacaoAoFim));

        List<Reserva> resultado = reservas.registrarNaoComparecimentosVencidos();

        if (encerrada) {
            assertAll(
                    () -> assertEquals(EstadoReserva.NAO_COMPARECEU, reserva.getEstado()),
                    () -> assertThat(resultado).extracting(Reserva::getId).containsExactly(reserva.getId()),
                    () -> assertThat(reserva.getEstado().isAtiva()).as("libera o recurso").isFalse());
        } else {
            assertAll(
                    () -> assertEquals(EstadoReserva.APROVADA, reserva.getEstado()),
                    () -> assertThat(resultado).isEmpty());
        }
    }

    @Test
    @DisplayName("Grava historico APROVADA -> NAO_COMPARECEU e um evento de auditoria, ambos em nome do sistema")
    void historicoEAuditoriaDoSistema() {
        Reserva reserva = aprovada(INICIO, FIM);
        agora(FIM);

        reservas.registrarNaoComparecimentosVencidos();

        RegistroHistoricoReserva ultimo = reservas.consultarHistorico(administrador, reserva.getId()).getLast();
        assertAll(
                () -> assertEquals(EstadoReserva.APROVADA, ultimo.estadoAnterior()),
                () -> assertEquals(EstadoReserva.NAO_COMPARECEU, ultimo.estadoNovo()),
                () -> assertEquals(Banco.SISTEMA_ID, ultimo.usuarioId()),
                () -> assertThat(ultimo.motivo()).contains("automático"),
                () -> assertEquals(1, eventos(reserva.getId(), "NAO_COMPARECIMENTO")),
                () -> assertThat(Banco.auditoria).filteredOn(e -> reserva.getId().equals(e.getEntidadeId())
                        && "NAO_COMPARECIMENTO".equals(e.getAcao())).allMatch(e -> Banco.SISTEMA_ID.equals(e.getAutorId())));
    }

    @Test
    @DisplayName("So mexe em APROVADA vencida: EM_USO, SOLICITADA, cancelada e futura ficam como estao")
    void soAprovadaVencida() {
        Reserva emUso = aprovada(INICIO, FIM);
        reservas.iniciarUso(administrador, emUso.getId());
        Reserva cancelada = aprovada(INICIO.plusHours(2), FIM.plusHours(2));
        reservas.cancelar(solicitante, cancelada.getId(), "aula remarcada");
        Sala restrita = new Sala(UUID.randomUUID(), "Lab " + UUID.randomUUID(), "laboratorio", 20, "Bloco A", true);
        Reserva solicitada = reservas.criar(solicitante, INICIO.plusHours(4), FIM.plusHours(4), "Aula",
                List.of(new ReservaRecurso(restrita, 1)));
        Reserva futura = aprovada(INICIO.plusDays(1), FIM.plusDays(1));
        agora(FIM.plusHours(6));

        List<Reserva> resultado = reservas.registrarNaoComparecimentosVencidos();

        assertAll(
                () -> assertThat(resultado).isEmpty(),
                () -> assertEquals(EstadoReserva.EM_USO, emUso.getEstado()),
                () -> assertEquals(EstadoReserva.CANCELADA, cancelada.getEstado()),
                () -> assertEquals(EstadoReserva.SOLICITADA, solicitada.getEstado()),
                () -> assertEquals(EstadoReserva.APROVADA, futura.getEstado()));
    }

    @Test
    @DisplayName("Chamar de novo nao duplica historico nem auditoria (idempotente)")
    void idempotente() {
        Reserva reserva = aprovada(INICIO, FIM);
        agora(FIM.plusMinutes(5));

        reservas.registrarNaoComparecimentosVencidos();
        int registros = reservas.consultarHistorico(administrador, reserva.getId()).size();
        List<Reserva> segunda = reservas.registrarNaoComparecimentosVencidos();

        assertAll(
                () -> assertThat(segunda).isEmpty(),
                () -> assertEquals(registros, reservas.consultarHistorico(administrador, reserva.getId()).size()),
                () -> assertEquals(1, eventos(reserva.getId(), "NAO_COMPARECIMENTO")));
    }

    @Test
    @DisplayName("O registro manual continua possivel antes do fim (inicio + 15 min) e o automatico nao repete")
    void manualAntesDoAutomatico() {
        Reserva reserva = aprovada(INICIO, FIM);
        agora(INICIO.plusMinutes(20));
        reservas.registrarNaoComparecimento(administrador, reserva.getId());
        agora(FIM.plusMinutes(1));

        assertAll(
                () -> assertThat(reservas.registrarNaoComparecimentosVencidos()).isEmpty(),
                () -> assertEquals(1, eventos(reserva.getId(), "NAO_COMPARECIMENTO")),
                () -> assertEquals(administrador.getId(),
                        reservas.consultarHistorico(administrador, reserva.getId()).getLast().usuarioId()));
    }
}
