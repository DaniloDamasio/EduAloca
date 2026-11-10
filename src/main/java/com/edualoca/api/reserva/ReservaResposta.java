package com.edualoca.api.reserva;

import com.edualoca.domain.reserva.Reserva;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReservaResposta(UUID id, UUID solicitanteId, String estado, LocalDateTime inicio, LocalDateTime fim,
                              String finalidade, String justificativaRejeicao) {

    public static ReservaResposta de(Reserva reserva) {
        return new ReservaResposta(reserva.getId(), reserva.getSolicitanteId(), reserva.getEstado().name(),
                reserva.getInicio(), reserva.getFim(), reserva.getFinalidade(), reserva.getJustificativaRejeicao());
    }
}
