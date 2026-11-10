package com.edualoca.api.recurso;

import java.time.LocalDateTime;
import java.util.UUID;

public record ManutencaoRequisicao(String tipoRecurso, UUID recursoId, LocalDateTime inicio, LocalDateTime fim,
                                   String motivo) {
}
