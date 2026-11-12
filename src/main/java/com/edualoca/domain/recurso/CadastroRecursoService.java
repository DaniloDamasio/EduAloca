package com.edualoca.domain.recurso;

import com.edualoca.domain.auditoria.AuditoriaService;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.Intervalos;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.usuario.Usuario;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class CadastroRecursoService {

    private final Clock clock;

    public CadastroRecursoService() {
        this(Clock.systemDefaultZone());
    }

    public CadastroRecursoService(Clock clock) {
        this.clock = clock;
    }

    public Sala cadastrarSala(String nome, String tipo, int capacidade, String localizacao, boolean restrito) {
        Banco.seedSeNecessario();
        validarSala(nome, tipo, capacidade, localizacao);
        Sala sala = new Sala(UUID.randomUUID(), nome, tipo, capacidade, localizacao, restrito);
        Banco.salas.put(sala.getId(), sala);
        AuditoriaService.registrar("Sala", sala.getId(), "CRIACAO", null, LocalDateTime.now(clock));
        return sala;
    }

    public Professor cadastrarProfessor(String nome, String identificadorInstitucional, List<Competencia> competencias) {
        Banco.seedSeNecessario();
        validarProfessor(nome, competencias);
        obrigatorio(identificadorInstitucional, "Identificador institucional do professor e obrigatorio");
        if (Banco.professorIdPorIdentificador.containsKey(identificadorInstitucional)) {
            throw new RegraNegocioException(409, "Identificador institucional ja cadastrado");
        }
        Professor professor = new Professor(UUID.randomUUID(), nome, identificadorInstitucional, competencias);
        Banco.professores.put(professor.getId(), professor);
        Banco.professorIdPorIdentificador.put(identificadorInstitucional, professor.getId());
        AuditoriaService.registrar("Professor", professor.getId(), "CRIACAO", null, LocalDateTime.now(clock));
        return professor;
    }

    public Material cadastrarMaterial(String nome, String tipo, int quantidadeTotal, boolean restrito) {
        Banco.seedSeNecessario();
        validarMaterial(nome, tipo, quantidadeTotal);
        Material material = new Material(UUID.randomUUID(), nome, tipo, quantidadeTotal, restrito);
        Banco.materiais.put(material.getId(), material);
        AuditoriaService.registrar("Material", material.getId(), "CRIACAO", null, LocalDateTime.now(clock));
        return material;
    }

    public Manutencao cadastrarManutencao(Sala sala, LocalDateTime inicio, LocalDateTime fim, String motivo) {
        return registrarManutencao(sala, inicio, fim, motivo);
    }

    private Manutencao registrarManutencao(Object recurso, LocalDateTime inicio, LocalDateTime fim, String motivo) {
        Banco.seedSeNecessario();
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            throw new RegraNegocioException(400, "Periodo de manutencao invalido: fim deve ser posterior ao inicio");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new RegraNegocioException(400, "Motivo da manutencao e obrigatorio");
        }
        boolean colideComReservaAtivaNaoConfirmada = Banco.reservas.values().stream()
                .anyMatch(r -> (r.getEstado() == EstadoReserva.APROVADA || r.getEstado() == EstadoReserva.EM_USO)
                        && envolveRecurso(r, recurso)
                        && Intervalos.sobrepoe(inicio, fim, r.getInicio(), r.getFim()));
        if (colideComReservaAtivaNaoConfirmada) {
            throw new RegraNegocioException(409, "Manutencao colide com reserva ativa; confirmacao explicita necessaria");
        }
        Manutencao manutencao = new Manutencao(UUID.randomUUID(), recurso, inicio, fim, motivo);
        Banco.manutencoes.put(manutencao.getId(), manutencao);
        AuditoriaService.registrar("Manutencao", manutencao.getId(), "CRIACAO", null, LocalDateTime.now(clock));
        return manutencao;
    }

    public Manutencao cadastrarManutencao(Material material, LocalDateTime inicio, LocalDateTime fim, String motivo) {
        return registrarManutencao(material, inicio, fim, motivo);
    }

    public Manutencao cadastrarManutencao(Professor professor, LocalDateTime inicio, LocalDateTime fim, String motivo) {
        return registrarManutencao(professor, inicio, fim, motivo);
    }

    public void inativarSala(Sala sala) {
        Banco.seedSeNecessario();
        sala.inativar();
        AuditoriaService.registrar("Sala", sala.getId(), "INATIVACAO", null, LocalDateTime.now(clock));
    }

    /** RF-04: edita a sala no lugar; recusa sem alterar nada se algum campo for invalido. */
    public Sala atualizarSala(UUID id, String nome, String tipo, int capacidade, String localizacao, boolean restrito) {
        Banco.seedSeNecessario();
        Sala sala = existente(Banco.salas.get(id), "Sala");
        validarSala(nome, tipo, capacidade, localizacao);
        sala.atualizar(nome, tipo, capacidade, localizacao, restrito);
        AuditoriaService.registrar("Sala", sala.getId(), "ATUALIZACAO", null, LocalDateTime.now(clock));
        return sala;
    }

    /** RF-05: edita nome e competencias; o identificador institucional e a chave e nao muda. */
    public Professor atualizarProfessor(UUID id, String nome, List<Competencia> competencias) {
        Banco.seedSeNecessario();
        Professor professor = existente(Banco.professores.get(id), "Professor");
        validarProfessor(nome, competencias);
        professor.atualizar(nome, competencias);
        AuditoriaService.registrar("Professor", professor.getId(), "ATUALIZACAO", null, LocalDateTime.now(clock));
        return professor;
    }

    public void inativarProfessor(UUID id) {
        Banco.seedSeNecessario();
        Professor professor = existente(Banco.professores.get(id), "Professor");
        professor.inativar();
        AuditoriaService.registrar("Professor", professor.getId(), "INATIVACAO", null, LocalDateTime.now(clock));
    }

    /** RF-06: edita o material no lugar; recusa sem alterar nada se algum campo for invalido. */
    public Material atualizarMaterial(UUID id, String nome, String tipo, int quantidadeTotal, boolean restrito) {
        Banco.seedSeNecessario();
        Material material = existente(Banco.materiais.get(id), "Material");
        validarMaterial(nome, tipo, quantidadeTotal);
        material.atualizar(nome, tipo, quantidadeTotal, restrito);
        AuditoriaService.registrar("Material", material.getId(), "ATUALIZACAO", null, LocalDateTime.now(clock));
        return material;
    }

    public void inativarMaterial(UUID id) {
        Banco.seedSeNecessario();
        Material material = existente(Banco.materiais.get(id), "Material");
        material.inativar();
        AuditoriaService.registrar("Material", material.getId(), "INATIVACAO", null, LocalDateTime.now(clock));
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

    private static void validarSala(String nome, String tipo, int capacidade, String localizacao) {
        obrigatorio(nome, "Nome da sala e obrigatorio");
        obrigatorio(tipo, "Tipo da sala e obrigatorio");
        obrigatorio(localizacao, "Localizacao da sala e obrigatoria");
        if (capacidade <= 0) {
            throw new RegraNegocioException(400, "Capacidade da sala deve ser positiva");
        }
    }

    private static void validarProfessor(String nome, List<Competencia> competencias) {
        obrigatorio(nome, "Nome do professor e obrigatorio");
        if (competencias == null) {
            throw new RegraNegocioException(400, "Lista de competencias do professor e obrigatoria (pode ser vazia)");
        }
    }

    private static void validarMaterial(String nome, String tipo, int quantidadeTotal) {
        obrigatorio(nome, "Nome do material e obrigatorio");
        obrigatorio(tipo, "Tipo do material e obrigatorio");
        if (quantidadeTotal <= 0) {
            throw new RegraNegocioException(400, "Quantidade total do material deve ser positiva");
        }
    }

    private static void obrigatorio(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new RegraNegocioException(400, mensagem);
        }
    }

    private static <T> T existente(T recurso, String entidade) {
        if (recurso == null) {
            throw new RegraNegocioException(404, "Cadastro inexistente: " + entidade);
        }
        return recurso;
    }

    private static boolean envolveRecurso(Reserva reserva, Object recurso) {
        return reserva.getRecursos().stream().anyMatch(rr -> rr.getRecurso() == recurso);
    }
}
