package com.edualoca.domain.reserva;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma entrada do historico auditavel de uma reserva (RF-27): quem mudou o estado, quando, de qual para qual
 * e por que. {@code estadoAnterior} e {@code null} na criacao.
 */
public record RegistroHistoricoReserva(UUID id, UUID reservaId, UUID usuarioId, Instant instante,
                                       EstadoReserva estadoAnterior, EstadoReserva estadoNovo, String motivo) {
}
