package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.reserva.EstadoReserva;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regra critica "reservas do mesmo recurso nao podem se sobrepor", para <b>material x material</b> (antes so havia teste
 * de sala e de professor). Fixa a regra vigente na criacao, igual a da constraint {@code ex_reserva_recurso_sem_sobreposicao}
 * do PostgreSQL: o material e exclusivo no intervalo, qualquer que seja a quantidade pedida.
 *
 * <p>A busca (RF-10) ainda trata o material por quantidade livre; a divergencia esta registrada em DEF-016, aberto.
 */
class SobreposicaoMaterialTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime DIA = LocalDateTime.of(2027, 7, 1, 0, 0);

    private final RelogioAjustavel relogio = new RelogioAjustavel(DIA.minusDays(1).atZone(ZONA).toInstant(), ZONA);
    private final ReservaService reservas = new ReservaService(relogio);
    private final Usuario ana = new Usuario(UUID.randomUUID(), "ana@example.com", "hash", Perfil.SOLICITANTE);
    private final Usuario bia = new Usuario(UUID.randomUUID(), "bia@example.com", "hash", Perfil.SOLICITANTE);

    @BeforeEach
    void semear() {
        Banco.seedSeNecessario();
    }

    private Reserva reservar(Usuario quem, Material material, int quantidade, int horaInicio, int horaFim) {
        return reservas.criar(quem, DIA.withHour(horaInicio), DIA.withHour(horaFim), "Aula",
                List.of(new ReservaRecurso(material, quantidade)));
    }

    private static Material projetores() {
        return new Material(UUID.randomUUID(), "Projetor " + UUID.randomUUID(), "projetor", 5, false);
    }

    @ParameterizedTest(name = "existente 09-11, nova {0}-{1} -> recusada={2}")
    @CsvSource({
            // Sem zero a esquerda: o JUnit converte com Integer.decode, que le "08" e "09" como octal invalido.
            "10, 12, true",   // sobreposicao parcial no fim
            "8, 10, true",    // sobreposicao parcial no inicio
            "9, 11, true",    // intervalos identicos
            "8, 12, true",    // nova engloba a existente
            "11, 12, false",  // encosta no fim: semiaberto [inicio, fim)
            "7, 9, false",    // encosta no inicio
    })
    @DisplayName("Mesmo material: sobreposicao e recusada com 409; intervalos que so encostam sao aceitos")
    void mesmoMaterial(int horaInicio, int horaFim, boolean recusada) {
        Material material = projetores();
        reservar(ana, material, 1, 9, 11);

        if (recusada) {
            RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                    () -> reservar(bia, material, 1, horaInicio, horaFim));
            assertAll(
                    () -> assertEquals(409, ex.getStatusHttp()),
                    () -> assertThat(ex.getMessage()).contains("sobreposição"),
                    () -> assertThat(Banco.auditoria).as("conflito evitado conta no relatorio (RF-35)")
                            .anyMatch(e -> "REJEICAO_SOBREPOSICAO".equals(e.getAcao()) && bia.getId().equals(e.getAutorId())));
        } else {
            assertEquals(EstadoReserva.APROVADA, reservar(bia, material, 1, horaInicio, horaFim).getEstado());
        }
    }

    @Test
    @DisplayName("Regra vigente: o material e exclusivo no intervalo, mesmo sobrando unidades (ver DEF-016)")
    void exclusivoMesmoComUnidadesSobrando() {
        Material cincoProjetores = projetores();
        reservar(ana, cincoProjetores, 1, 9, 11);

        assertEquals(409, assertThrows(RegraNegocioException.class,
                () -> reservar(bia, cincoProjetores, 1, 9, 11)).getStatusHttp());
    }

    @Test
    @DisplayName("Materiais diferentes no mesmo horario nao conflitam")
    void materiaisDiferentes() {
        reservar(ana, projetores(), 1, 9, 11);

        assertEquals(EstadoReserva.APROVADA, reservar(bia, projetores(), 1, 9, 11).getEstado());
    }

    @Test
    @DisplayName("Reserva cancelada libera o material para o mesmo horario")
    void canceladaLibera() {
        Material material = projetores();
        Reserva primeira = reservar(ana, material, 1, 9, 11);
        reservas.cancelar(ana, primeira.getId(), "aula remarcada");

        assertEquals(EstadoReserva.APROVADA, reservar(bia, material, 1, 9, 11).getEstado());
    }

    @Test
    @DisplayName("Reserva com dois materiais e recusada inteira se um deles conflita (nada e gravado)")
    void conflitoEmUmDosMateriaisRecusaTudo() {
        Material ocupado = projetores();
        Material livre = projetores();
        reservar(ana, ocupado, 1, 9, 11);
        int antes = Banco.reservas.size();

        assertEquals(409, assertThrows(RegraNegocioException.class, () -> reservas.criar(bia, DIA.withHour(9),
                DIA.withHour(11), "Aula", List.of(new ReservaRecurso(livre, 1), new ReservaRecurso(ocupado, 1)))).getStatusHttp());
        assertAll(
                () -> assertEquals(antes, Banco.reservas.size()),
                () -> assertEquals(EstadoReserva.APROVADA, reservar(bia, livre, 1, 9, 11).getEstado()));
    }
}
