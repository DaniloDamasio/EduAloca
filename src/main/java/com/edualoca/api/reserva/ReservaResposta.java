package com.edualoca.api.reserva;

import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReservaResposta(UUID id, UUID solicitanteId, String estado, LocalDateTime inicio, LocalDateTime fim,
                              String finalidade, String justificativaRejeicao, List<RecursoReservado> recursos) {

    /** Recurso vinculado a reserva, com o nome para exibicao (a interface nao precisa cruzar ids com o catalogo). */
    public record RecursoReservado(String tipo, UUID id, String nome, int quantidade) {

        static RecursoReservado de(ReservaRecurso rr) {
            return switch (rr.getRecurso()) {
                case Sala s -> new RecursoReservado("SALA", s.getId(), s.getNome(), rr.getQuantidade());
                case Material m -> new RecursoReservado("MATERIAL", m.getId(), m.getNome(), rr.getQuantidade());
                case Professor p -> new RecursoReservado("PROFESSOR", p.getId(), p.getNome(), rr.getQuantidade());
                default -> new RecursoReservado("DESCONHECIDO", null, null, rr.getQuantidade());
            };
        }
    }

    public static ReservaResposta de(Reserva reserva) {
        return new ReservaResposta(reserva.getId(), reserva.getSolicitanteId(), reserva.getEstado().name(),
                reserva.getInicio(), reserva.getFim(), reserva.getFinalidade(), reserva.getJustificativaRejeicao(),
                reserva.getRecursos().stream().map(RecursoReservado::de).toList());
    }
}
