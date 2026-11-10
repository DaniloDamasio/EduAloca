package com.edualoca.api.recurso;

import com.edualoca.domain.recurso.Sala;

import java.util.UUID;

public record SalaResposta(UUID id, String nome, String tipo, int capacidade, String localizacao, boolean restrito) {

    public static SalaResposta de(Sala sala) {
        return new SalaResposta(sala.getId(), sala.getNome(), sala.getTipo(), sala.getCapacidade(),
                sala.getLocalizacao(), sala.isRestrito());
    }
}
