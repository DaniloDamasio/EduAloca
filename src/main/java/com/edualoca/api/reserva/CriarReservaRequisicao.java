package com.edualoca.api.reserva;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CriarReservaRequisicao(LocalDateTime inicio, LocalDateTime fim, String finalidade,
                                     List<RecursoRequisicao> recursos) {

    public record RecursoRequisicao(String tipo, UUID id, Integer quantidade) {
    }
}
