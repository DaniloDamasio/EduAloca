package com.edualoca.domain.recurso;

import java.util.List;
import java.util.UUID;

public class Professor {

    private final UUID id;
    private String nome;
    private final String identificadorInstitucional;
    private List<Competencia> competencias;
    private boolean ativo = true;

    public Professor(UUID id, String nome, String identificadorInstitucional, List<Competencia> competencias) {
        this.id = id;
        this.nome = nome;
        this.identificadorInstitucional = identificadorInstitucional;
        this.competencias = competencias;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getIdentificadorInstitucional() {
        return identificadorInstitucional;
    }

    public List<Competencia> getCompetencias() {
        return competencias;
    }

    public boolean isAtivo() {
        return ativo;
    }

    /** Edicao no proprio objeto (RF-05). O identificador institucional nao muda: e a chave de unicidade. */
    public void atualizar(String nome, List<Competencia> competencias) {
        this.nome = nome;
        this.competencias = List.copyOf(competencias);
    }

    public void inativar() {
        this.ativo = false;
    }
}
