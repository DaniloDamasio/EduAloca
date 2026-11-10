package com.edualoca.api.recurso;

import com.edualoca.domain.recurso.Manutencao;

import java.time.LocalDateTime;
import java.util.UUID;

public record ManutencaoResposta(UUID id, LocalDateTime inicio, LocalDateTime fim, String motivo) {

    public static ManutencaoResposta de(Manutencao manutencao) {
        return new ManutencaoResposta(manutencao.getId(), manutencao.getInicio(), manutencao.getFim(), manutencao.getMotivo());
    }
}
