package com.edualoca.domain.recurso;

import com.edualoca.domain.auditoria.AuditoriaService;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.usuario.Usuario;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class CadastroRecursoService {

    public Sala cadastrarSala(String nome, String tipo, int capacidade, String localizacao, boolean restrito) {
        Banco.seedSeNecessario();
        if (nome == null || nome.isBlank()) {
            throw new RegraNegocioException(400, "Nome da sala e obrigatorio");
        }
        if (capacidade <= 0) {
            throw new RegraNegocioException(400, "Capacidade da sala deve ser positiva");
        }
        Sala sala = new Sala(UUID.randomUUID(), nome, tipo, capacidade, localizacao, restrito);
        Banco.salas.put(sala.getId(), sala);
        AuditoriaService.registrar("Sala", sala.getId(), "CRIACAO", null);
        return sala;
    }

    public Professor cadastrarProfessor(String nome, String identificadorInstitucional, List<Competencia> competencias) {
        Banco.seedSeNecessario();
        if (Banco.professorIdPorIdentificador.containsKey(identificadorInstitucional)) {
            throw new RegraNegocioException(409, "Identificador institucional ja cadastrado");
        }
        Professor professor = new Professor(UUID.randomUUID(), nome, identificadorInstitucional, competencias);
        Banco.professores.put(professor.getId(), professor);
        Banco.professorIdPorIdentificador.put(identificadorInstitucional, professor.getId());
        AuditoriaService.registrar("Professor", professor.getId(), "CRIACAO", null);
        return professor;
    }

    public Material cadastrarMaterial(String nome, String tipo, int quantidadeTotal, boolean restrito) {
        Banco.seedSeNecessario();
        if (quantidadeTotal <= 0) {
            throw new RegraNegocioException(400, "Quantidade total do material deve ser positiva");
        }
        Material material = new Material(UUID.randomUUID(), nome, tipo, quantidadeTotal, restrito);
        Banco.materiais.put(material.getId(), material);
        AuditoriaService.registrar("Material", material.getId(), "CRIACAO", null);
        return material;
    }

    public Manutencao cadastrarManutencao(Sala sala, LocalDateTime inicio, LocalDateTime fim, String motivo) {
        Banco.seedSeNecessario();
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            throw new RegraNegocioException(400, "Periodo de manutencao invalido: fim deve ser posterior ao inicio");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new RegraNegocioException(400, "Motivo da manutencao e obrigatorio");
        }
        boolean colideComReservaAtivaNaoConfirmada = Banco.reservas.values().stream()
                .anyMatch(r -> ehAtiva(r.getEstado())
                        && (r.getEstado() == EstadoReserva.APROVADA || r.getEstado() == EstadoReserva.EM_USO)
                        && envolveRecurso(r, sala)
                        && sobrepoe(inicio, fim, r.getInicio(), r.getFim()));
        if (colideComReservaAtivaNaoConfirmada) {
            throw new RegraNegocioException(409, "Manutencao colide com reserva ativa; confirmacao explicita necessaria");
        }
        Manutencao manutencao = new Manutencao(UUID.randomUUID(), sala, inicio, fim, motivo);
        Banco.manutencoes.put(manutencao.getId(), manutencao);
        AuditoriaService.registrar("Manutencao", manutencao.getId(), "CRIACAO", null);
        return manutencao;
    }

    public void inativarSala(Sala sala) {
        Banco.seedSeNecessario();
        sala.inativar();
        AuditoriaService.registrar("Sala", sala.getId(), "INATIVACAO", null);
    }

    public List<Sala> listarSalasAtivas() {
        Banco.seedSeNecessario();
        return Banco.salas.values().stream().filter(Sala::isAtivo).toList();
    }

    public List<Sala> listarSalasAtivas(Usuario solicitante) {
        if (solicitante == null) {
            throw new RegraNegocioException(401, "Autenticacao necessaria para consultar salas");
        }
        return listarSalasAtivas();
    }

    private static boolean ehAtiva(EstadoReserva estado) {
        return estado == EstadoReserva.SOLICITADA || estado == EstadoReserva.APROVADA || estado == EstadoReserva.EM_USO;
    }

    private static boolean envolveRecurso(Reserva reserva, Object recurso) {
        return reserva.getRecursos().stream().anyMatch(rr -> rr.getRecurso() == recurso);
    }

    private static boolean sobrepoe(LocalDateTime inicioA, LocalDateTime fimA, LocalDateTime inicioB, LocalDateTime fimB) {
        return inicioA.isBefore(fimB) && inicioB.isBefore(fimA);
    }
}
