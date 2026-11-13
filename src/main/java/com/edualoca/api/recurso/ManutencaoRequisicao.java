package com.edualoca.api.recurso;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code confirmarColisao} (RF-23): ausente ou {@code false} recusa a manutencao que colide com reserva ativa;
 * {@code true} cancela as reservas APROVADA afetadas e registra a manutencao (EM_USO continua impedindo).
 */
public record ManutencaoRequisicao(String tipoRecurso, UUID recursoId, LocalDateTime inicio, LocalDateTime fim,
                                   String motivo, Boolean confirmarColisao) {
}
