package com.edualoca.api.reserva;

import java.time.LocalDateTime;

/** Novo intervalo da reserva (RF-13). */
public record AlterarReservaRequisicao(LocalDateTime inicio, LocalDateTime fim) {
}
