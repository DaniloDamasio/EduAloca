package com.edualoca.domain.recurso;

import java.util.UUID;

public class Sala {

    private final UUID id;
    private String nome;
    private String tipo;
    private int capacidade;
    private String localizacao;
    private boolean restrito;
    private boolean ativo = true;

    public Sala(UUID id, String nome, String tipo, int capacidade, String localizacao, boolean restrito) {
        this.id = id;
        this.nome = nome;
        this.tipo = tipo;
        this.capacidade = capacidade;
        this.localizacao = localizacao;
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

    public int getCapacidade() {
        return capacidade;
    }

    public String getLocalizacao() {
        return localizacao;
    }

    public boolean isRestrito() {
        return restrito;
    }

    public boolean isAtivo() {
        return ativo;
    }

    /** Edicao no proprio objeto (RF-04): reservas e manutencoes apontam para esta instancia. Validar antes. */
    public void atualizar(String nome, String tipo, int capacidade, String localizacao, boolean restrito) {
        this.nome = nome;
        this.tipo = tipo;
        this.capacidade = capacidade;
        this.localizacao = localizacao;
        this.restrito = restrito;
    }

    public void inativar() {
        this.ativo = false;
    }
}
