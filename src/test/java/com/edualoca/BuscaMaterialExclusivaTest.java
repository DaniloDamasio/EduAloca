package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.PesquisaRecursoService;
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
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DEF-016, opcao A (decisao de 12/11/2026): a busca de material segue a mesma regra da criacao e da constraint
 * {@code ex_reserva_recurso_sem_sobreposicao}. Material com reserva ativa sobreposta nao aparece, qualquer que seja a
 * quantidade; {@code quantidade} so limita o pedido a {@code quantidadeTotal}. O que a busca oferece, a criacao aceita.
 */
class BuscaMaterialExclusivaTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime DIA = LocalDateTime.of(2027, 8, 2, 0, 0);

    private final RelogioAjustavel relogio = new RelogioAjustavel(DIA.minusDays(1).atZone(ZONA).toInstant(), ZONA);
    private final ReservaService reservas = new ReservaService(relogio);
    private final PesquisaRecursoService busca = new PesquisaRecursoService();
    private final Usuario ana = new Usuario(UUID.randomUUID(), "ana@example.com", "hash", Perfil.SOLICITANTE);
    private final Usuario bia = new Usuario(UUID.randomUUID(), "bia@example.com", "hash", Perfil.SOLICITANTE);
    private Material cincoProjetores;

    @BeforeEach
    void cadastrar() {
        Banco.seedSeNecessario();
        cincoProjetores = new Material(UUID.randomUUID(), "Projetor DEF-016", "projetor-def016", 5, false);
        Banco.materiais.put(cincoProjetores.getId(), cincoProjetores);
    }

    private Reserva reservar(Usuario quem, int quantidade, int horaInicio, int horaFim) {
        return reservas.criar(quem, DIA.withHour(horaInicio), DIA.withHour(horaFim), "Aula",
                List.of(new ReservaRecurso(cincoProjetores, quantidade)));
    }

    private boolean aparece(int quantidade, int horaInicio, int horaFim) {
        return busca.buscarMateriais("projetor-def016", quantidade, DIA.withHour(horaInicio), DIA.withHour(horaFim)).stream()
                .anyMatch(m -> m.getId().equals(cincoProjetores.getId()));
    }

    @Test
    @DisplayName("Com 1 de 5 unidades reservada no intervalo, o material nao aparece (antes aparecia e a criacao recusava)")
    void reservaParcialTiraOMaterialDaBusca() {
        reservar(ana, 1, 9, 11);

        assertThat(aparece(1, 9, 11)).isFalse();
        assertThat(aparece(1, 10, 12)).as("sobreposicao parcial").isFalse();
    }

    @ParameterizedTest(name = "busca {0}-{1} -> aparece={2}")
    @CsvSource({"11, 12, true", "7, 9, true", "10, 11, false", "8, 12, false"})
    @DisplayName("Intervalos que so encostam (semiaberto) nao bloqueiam; os que se sobrepoem, sim")
    void bordasDoIntervalo(int horaInicio, int horaFim, boolean esperado) {
        reservar(ana, 1, 9, 11);

        assertThat(aparece(1, horaInicio, horaFim)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("Reserva cancelada devolve o material a busca")
    void canceladaLibera() {
        Reserva reserva = reservar(ana, 2, 9, 11);
        reservas.cancelar(ana, reserva.getId(), "aula remarcada");

        assertThat(aparece(1, 9, 11)).isTrue();
    }

    @ParameterizedTest(name = "quantidade {0} -> aparece={1}")
    @CsvSource({"1, true", "5, true", "6, false"})
    @DisplayName("Sem reserva no intervalo, a quantidade so precisa caber na quantidade total")
    void quantidadeLimitadaAoTotal(int quantidade, boolean esperado) {
        assertThat(aparece(quantidade, 9, 11)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("O que a busca oferece, a criacao aceita; o que a busca esconde, a criacao recusaria")
    void buscaECriacaoConcordam() {
        reservar(ana, 1, 9, 11);

        assertThat(aparece(1, 11, 12)).isTrue();
        assertEquals(EstadoReserva.APROVADA, reservar(bia, 1, 11, 12).getEstado());
        assertThat(aparece(1, 9, 11)).isFalse();
    }
}
