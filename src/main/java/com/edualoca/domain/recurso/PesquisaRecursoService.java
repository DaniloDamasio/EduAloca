package com.edualoca.domain.recurso;

import java.time.LocalDateTime;
import java.util.List;

public class PesquisaRecursoService {

    public List<Sala> buscarSalas(String tipo, int capacidadeMinima, String localizacao,
                                   LocalDateTime inicio, LocalDateTime fim) {
        throw new UnsupportedOperationException("busca de salas nao implementada");
    }

    public List<Professor> buscarProfessores(String competencia, LocalDateTime inicio, LocalDateTime fim) {
        throw new UnsupportedOperationException("busca de professores nao implementada");
    }

    public List<Material> buscarMateriais(String tipo, int quantidadeDesejada, LocalDateTime inicio, LocalDateTime fim) {
        throw new UnsupportedOperationException("busca de materiais nao implementada");
    }
}
