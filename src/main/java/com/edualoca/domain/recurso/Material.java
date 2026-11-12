package com.edualoca.domain.recurso;

import java.util.UUID;

public class Material {

    private final UUID id;
    private String nome;
    private String tipo;
    private int quantidadeTotal;
    private boolean restrito;
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

    /** Edicao no proprio objeto (RF-06): reservas e manutencoes apontam para esta instancia. Validar antes. */
    public void atualizar(String nome, String tipo, int quantidadeTotal, boolean restrito) {
        this.nome = nome;
        this.tipo = tipo;
        this.quantidadeTotal = quantidadeTotal;
        this.restrito = restrito;
    }

    public void inativar() {
        this.ativo = false;
    }
}
