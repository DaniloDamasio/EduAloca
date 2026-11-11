package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.relatorio.RelatorioService;
import com.edualoca.domain.relatorio.UtilizacaoPorPeriodo;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * As horas do relatorio sao tempo decorrido de verdade no fuso do servidor, e nao diferenca de relogio de parede.
 * Usa America/New_York porque o Brasil nao tem mais horario de verao.
 */
class RelatorioFusoHorarioTest {

    private static final ZoneId NOVA_YORK = ZoneId.of("America/New_York");
    private final Usuario administrador = new Usuario(UUID.randomUUID(), "adm@edualoca.test", "h", Perfil.ADMINISTRADOR);

    @Test
    @DisplayName("Reserva que atravessa o inicio do horario de verao conta as horas realmente decorridas")
    void reservaNaMudancaParaHorarioDeVerao() {
        // Em 14/03/2027, as 02:00, o relogio de Nova York pula para 03:00.
        // Reserva 01:00-04:00 no relogio de parede = 2 h decorridas (e nao 3 h).
        Sala sala = new Sala(UUID.randomUUID(), "Sala NY", "aula", 10, "Bloco A", false);
        Reserva reserva = new Reserva(UUID.randomUUID(), administrador.getId(), LocalDateTime.of(2027, 3, 14, 1, 0),
                LocalDateTime.of(2027, 3, 14, 4, 0), "Aula", List.of(new ReservaRecurso(sala, 1)), EstadoReserva.APROVADA);
        Banco.reservas.put(reserva.getId(), reserva);

        UtilizacaoPorPeriodo relatorio = new RelatorioService(NOVA_YORK).gerarUtilizacaoPorPeriodo(administrador,
                LocalDateTime.of(2027, 3, 14, 0, 0), LocalDateTime.of(2027, 3, 15, 0, 0));

        assertEquals(120, relatorio.recursos().get(0).minutos());
    }
}
