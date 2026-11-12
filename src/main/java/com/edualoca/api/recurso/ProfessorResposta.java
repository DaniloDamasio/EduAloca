package com.edualoca.api.recurso;

import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Professor;

import java.util.List;
import java.util.UUID;

public record ProfessorResposta(UUID id, String nome, String identificadorInstitucional, List<String> competencias) {

    public static ProfessorResposta de(Professor professor) {
        return new ProfessorResposta(professor.getId(), professor.getNome(), professor.getIdentificadorInstitucional(),
                professor.getCompetencias().stream().map(Competencia::getNome).toList());
    }
}
