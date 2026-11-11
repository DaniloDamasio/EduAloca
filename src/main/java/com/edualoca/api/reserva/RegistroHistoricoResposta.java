package com.edualoca.api.reserva;

import com.edualoca.domain.reserva.RegistroHistoricoReserva;

import java.time.Instant;
import java.util.UUID;

public record RegistroHistoricoResposta(UUID id, UUID reservaId, UUID usuarioId, Instant instante,
                                        String estadoAnterior, String estadoNovo, String motivo) {

    public static RegistroHistoricoResposta de(RegistroHistoricoReserva registro) {
        return new RegistroHistoricoResposta(registro.id(), registro.reservaId(), registro.usuarioId(), registro.instante(),
                registro.estadoAnterior() == null ? null : registro.estadoAnterior().name(),
                registro.estadoNovo().name(), registro.motivo());
    }
}
