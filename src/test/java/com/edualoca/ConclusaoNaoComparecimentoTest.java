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
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import com.edualoca.suporte.RelogioAjustavel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P-05 / P-07 / P-08 (decisao de 12/11/2026): {@code EM_USO -> CONCLUIDA} ("concluir") e
 * {@code APROVADA -> NAO_COMPARECEU} ("registrar nao comparecimento", so a partir de inicio + 15 min), ambos manuais,
 * por Responsavel competente ou Administrador, com historico e um evento de auditoria (RF-27).
 */
class ConclusaoNaoComparecimentoTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 10, 5, 8, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2026, 10, 5, 10, 0);

    private final RelogioAjustavel relogio = new RelogioAjustavel(INICIO.minusDays(1).atZone(ZONA).toInstant(), ZONA);
    private final ReservaService reservas = new ReservaService(relogio);
    private final Usuario solicitante = new Usuario(UUID.randomUUID(), "sol@example.com", "hash", Perfil.SOLICITANTE);
    private final Usuario responsavelA = new Usuario(Banco.RESPONSAVEL_BLOCO_A_ID, "responsavel@example.com", "hash", Perfil.RESPONSAVEL);
    private final Usuario responsavelB = new Usuario(Banco.RESPONSAVEL_BLOCO_B_ID, "responsavel.b@example.com", "hash", Perfil.RESPONSAVEL);
    private final Usuario administrador = new Usuario(Banco.ADMINISTRADOR_ID, "admin@example.com", "hash", Perfil.ADMINISTRADOR);

    @BeforeEach
    void semear() {
        Banco.seedSeNecessario();
    }

    private Reserva aprovada() {
        Sala sala = new Sala(UUID.randomUUID(), "Sala " + UUID.randomUUID(), "aula", 30, "Bloco A", false);
        Reserva reserva = reservas.criar(solicitante, INICIO, FIM, "Aula", List.of(new ReservaRecurso(sala, 1)));
        assertEquals(EstadoReserva.APROVADA, reserva.getEstado(), "pre-condicao: sala comum nasce APROVADA");
        return reserva;
    }

    private void agora(LocalDateTime instante) {
        relogio.definir(instante.atZone(ZONA).toInstant());
    }

    private static long eventos(UUID reservaId, String acao) {
        return Banco.auditoria.stream().filter(e -> reservaId.equals(e.getEntidadeId()) && acao.equals(e.getAcao())).count();
    }

    @Test
    @DisplayName("Concluir: EM_USO -> CONCLUIDA com historico e um evento CONCLUSAO")
    void concluir_emUso() {
        Reserva reserva = aprovada();
        reservas.iniciarUso(responsavelA, reserva.getId());

        reservas.concluir(responsavelA, reserva.getId());

        RegistroHistoricoReserva ultimo = reservas.consultarHistorico(administrador, reserva.getId()).getLast();
        assertAll(
                () -> assertEquals(EstadoReserva.CONCLUIDA, reserva.getEstado()),
                () -> assertEquals(EstadoReserva.EM_USO, ultimo.estadoAnterior()),
                () -> assertEquals(responsavelA.getId(), ultimo.usuarioId()),
                () -> assertEquals(1, eventos(reserva.getId(), "CONCLUSAO")),
                () -> assertThat(reserva.getEstado().isAtiva()).as("concluida libera o recurso").isFalse());
    }

    @Test
    @DisplayName("Concluir: so a partir de EM_USO (APROVADA, CONCLUIDA e inexistente -> 409)")
    void concluir_foraDeEmUso_409() {
        Reserva aprovada = aprovada();
        Reserva concluida = aprovada();
        reservas.iniciarUso(administrador, concluida.getId());
        reservas.concluir(administrador, concluida.getId());

        for (UUID id : List.of(aprovada.getId(), concluida.getId(), UUID.randomUUID())) {
            assertEquals(409, assertThrows(RegraNegocioException.class, () -> reservas.concluir(administrador, id)).getStatusHttp());
        }
        assertEquals(EstadoReserva.APROVADA, aprovada.getEstado());
    }

    @ParameterizedTest(name = "{0} min apos o inicio -> aceito={1}")
    @CsvSource({"14, false", "15, true", "16, true"})
    @DisplayName("Nao compareceu: APROVADA so depois de inicio + 15 min (borda inclusiva)")
    void naoCompareceu_toleranciaDe15Minutos(long minutos, boolean aceito) {
        Reserva reserva = aprovada();
        agora(INICIO.plusMinutes(minutos));

        if (aceito) {
            reservas.registrarNaoComparecimento(responsavelA, reserva.getId());
            assertAll(
                    () -> assertEquals(EstadoReserva.NAO_COMPARECEU, reserva.getEstado()),
                    () -> assertEquals(1, eventos(reserva.getId(), "NAO_COMPARECIMENTO")),
                    () -> assertEquals(EstadoReserva.APROVADA,
                            reservas.consultarHistorico(administrador, reserva.getId()).getLast().estadoAnterior()));
        } else {
            RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                    () -> reservas.registrarNaoComparecimento(responsavelA, reserva.getId()));
            assertAll(
                    () -> assertEquals(409, ex.getStatusHttp()),
                    () -> assertThat(ex.getMessage()).contains("15"),
                    () -> assertEquals(EstadoReserva.APROVADA, reserva.getEstado()));
        }
    }

    @Test
    @DisplayName("Nao compareceu: so a partir de APROVADA (EM_USO e inexistente -> 409)")
    void naoCompareceu_foraDeAprovada_409() {
        Reserva emUso = aprovada();
        reservas.iniciarUso(administrador, emUso.getId());
        agora(INICIO.plus(Duration.ofHours(1)));

        assertEquals(409, assertThrows(RegraNegocioException.class,
                () -> reservas.registrarNaoComparecimento(administrador, emUso.getId())).getStatusHttp());
        assertEquals(409, assertThrows(RegraNegocioException.class,
                () -> reservas.registrarNaoComparecimento(administrador, UUID.randomUUID())).getStatusHttp());
    }

    @Test
    @DisplayName("Concluir e nao compareceu: Solicitante e Responsavel de outro bloco recebem 403")
    void soResponsavelCompetenteOuAdministrador() {
        Reserva emUso = aprovada();
        reservas.iniciarUso(administrador, emUso.getId());
        Reserva faltou = aprovada();
        agora(INICIO.plusMinutes(30));

        assertAll(
                () -> assertEquals(403, assertThrows(RegraNegocioException.class, () -> reservas.concluir(solicitante, emUso.getId())).getStatusHttp()),
                () -> assertEquals(403, assertThrows(RegraNegocioException.class, () -> reservas.concluir(responsavelB, emUso.getId())).getStatusHttp()),
                () -> assertEquals(403, assertThrows(RegraNegocioException.class, () -> reservas.registrarNaoComparecimento(solicitante, faltou.getId())).getStatusHttp()),
                () -> assertEquals(403, assertThrows(RegraNegocioException.class, () -> reservas.registrarNaoComparecimento(responsavelB, faltou.getId())).getStatusHttp()),
                () -> assertEquals(EstadoReserva.EM_USO, emUso.getEstado()),
                () -> assertEquals(EstadoReserva.APROVADA, faltou.getEstado()));
    }

    @Test
    @DisplayName("Reserva sem sala (so material): o Solicitante continua sem poder concluir nem registrar falta (403)")
    void reservaSemSala_perfilContinuaExigido() {
        Material projetor = new Material(UUID.randomUUID(), "Projetor", "projetor", 1, false);
        Reserva emUso = reservas.criar(solicitante, INICIO, FIM, "Aula", List.of(new ReservaRecurso(projetor, 1)));
        reservas.iniciarUso(administrador, emUso.getId());
        Reserva faltou = reservas.criar(solicitante, INICIO.plusHours(3), FIM.plusHours(3), "Aula",
                List.of(new ReservaRecurso(new Material(UUID.randomUUID(), "Caixa", "audio", 1, false), 1)));
        agora(INICIO.plusHours(4));

        assertAll(
                () -> assertEquals(403, assertThrows(RegraNegocioException.class, () -> reservas.concluir(solicitante, emUso.getId())).getStatusHttp()),
                () -> assertEquals(403, assertThrows(RegraNegocioException.class, () -> reservas.registrarNaoComparecimento(solicitante, faltou.getId())).getStatusHttp()));
    }
}
