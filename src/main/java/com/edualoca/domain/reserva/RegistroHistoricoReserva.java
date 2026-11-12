package com.edualoca.domain.reserva;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma entrada do historico auditavel de uma reserva (RF-27): quem mudou o estado, quando, de qual para qual
 * e por que. {@code estadoAnterior} e {@code null} na criacao. Imutavel: nao ha como alterar um registro depois
 * de criado, e a porta {@link HistoricoReservaRepositorio} nao oferece alteracao nem exclusao (RF-29).
 */
public record RegistroHistoricoReserva(UUID id, UUID reservaId, UUID usuarioId, Instant instante,
                                       EstadoReserva estadoAnterior, EstadoReserva estadoNovo, String motivo) {

    public RegistroHistoricoReserva {
        exigir(id != null, "id");
        exigir(reservaId != null, "reserva");
        exigir(usuarioId != null, "usuario");
        exigir(instante != null, "instante");
        exigir(estadoNovo != null, "estado novo");
        exigir(motivo != null && !motivo.isBlank(), "motivo");
        if (estadoAnterior == estadoNovo) {
            throw new IllegalArgumentException("Registro de histórico exige mudança de estado");
        }
    }

    private static void exigir(boolean condicao, String campo) {
        if (!condicao) {
            throw new IllegalArgumentException("Registro de histórico sem " + campo);
        }
    }
}
