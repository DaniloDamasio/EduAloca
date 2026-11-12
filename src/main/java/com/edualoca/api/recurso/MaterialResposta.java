package com.edualoca.api.recurso;

import com.edualoca.domain.recurso.Material;

import java.util.UUID;

public record MaterialResposta(UUID id, String nome, String tipo, int quantidadeTotal, boolean restrito) {

    public static MaterialResposta de(Material material) {
        return new MaterialResposta(material.getId(), material.getNome(), material.getTipo(),
                material.getQuantidadeTotal(), material.isRestrito());
    }
}
