package com.edualoca.api.recurso;

import java.util.List;

/** Corpos de cadastro do Administrador (RF-04, RF-05, RF-06). Numeros e booleanos ausentes viram 0 e false. */
public final class CadastroRequisicoes {

    private CadastroRequisicoes() {
    }

    public record SalaRequisicao(String nome, String tipo, Integer capacidade, String localizacao, Boolean restrito) {
    }

    public record ProfessorRequisicao(String nome, String identificadorInstitucional, List<String> competencias) {
    }

    public record MaterialRequisicao(String nome, String tipo, Integer quantidadeTotal, Boolean restrito) {
    }
}
