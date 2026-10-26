package com.edualoca.domain.recurso;

import com.edualoca.domain.usuario.Usuario;

import java.time.LocalDateTime;
import java.util.List;

public class CadastroRecursoService {

    public Sala cadastrarSala(String nome, String tipo, int capacidade, String localizacao, boolean restrito) {
        throw new UnsupportedOperationException("cadastro de sala nao implementado");
    }

    public Professor cadastrarProfessor(String nome, String identificadorInstitucional, List<Competencia> competencias) {
        throw new UnsupportedOperationException("cadastro de professor nao implementado");
    }

    public Material cadastrarMaterial(String nome, String tipo, int quantidadeTotal, boolean restrito) {
        throw new UnsupportedOperationException("cadastro de material nao implementado");
    }

    public Manutencao cadastrarManutencao(Sala sala, LocalDateTime inicio, LocalDateTime fim, String motivo) {
        throw new UnsupportedOperationException("cadastro de manutencao nao implementado");
    }

    public void inativarSala(Sala sala) {
        throw new UnsupportedOperationException("inativacao de sala nao implementada");
    }

    public List<Sala> listarSalasAtivas() {
        throw new UnsupportedOperationException("listagem de salas nao implementada");
    }

    public List<Sala> listarSalasAtivas(Usuario solicitante) {
        throw new UnsupportedOperationException("listagem de salas com autenticacao nao implementada");
    }
}
