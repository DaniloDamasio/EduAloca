package com.edualoca.domain.recurso;

import java.util.UUID;

public class Material {

    private final UUID id;
    private final String nome;
    private final String tipo;
    private final int quantidadeTotal;
    private final boolean restrito;
    private boolean ativo = true;

    public Material(UUID id, String nome, String tipo, int quantidadeTotal, boolean restrito) {
        this.id = id;
        this.nome = nome;
        this.tipo = tipo;
        this.quantidadeTotal = quantidadeTotal;
        this.restrito = restrito;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getTipo() {
        return tipo;
    }

    public int getQuantidadeTotal() {
        return quantidadeTotal;
    }

    public boolean isRestrito() {
        return restrito;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void inativar() {
        this.ativo = false;
    }
}
