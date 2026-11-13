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
    /** Autor gravado nos eventos de auditoria (RF-28); {@code null} quando o servico e usado sem ator (testes antigos). */
    private final UUID autorId;

    public CadastroRecursoService() {
        this(Clock.systemDefaultZone());
    }

    public CadastroRecursoService(Clock clock) {
        this(clock, null);
    }

    private CadastroRecursoService(Clock clock, UUID autorId) {
        this.clock = clock;
        this.autorId = autorId;
    }

    /**
     * RF-28: mesmo servico, mas gravando {@code autor} como autor de cada evento de auditoria (criacao, edicao,
     * inativacao e manutencao). Usado pela camada HTTP com o usuario do token; nunca com um campo do corpo.
     */
    public CadastroRecursoService por(Usuario autor) {
        return new CadastroRecursoService(clock, autor == null ? null : autor.getId());
    }

    public Sala cadastrarSala(String nome, String tipo, int capacidade, String localizacao, boolean restrito) {
        Banco.seedSeNecessario();
        validarSala(nome, tipo, capacidade, localizacao);
        Sala sala = new Sala(UUID.randomUUID(), nome, tipo, capacidade, localizacao, restrito);
        Banco.salas.put(sala.getId(), sala);
        AuditoriaService.registrar("Sala", sala.getId(), "CRIACAO", autorId, LocalDateTime.now(clock));
        return sala;
    }

    public Professor cadastrarProfessor(String nome, String identificadorInstitucional, List<Competencia> competencias) {
        Banco.seedSeNecessario();
        validarProfessor(nome, competencias);
        obrigatorio(identificadorInstitucional, "Identificador institucional do professor é obrigatório");
        if (Banco.professorIdPorIdentificador.containsKey(identificadorInstitucional)) {
            throw new RegraNegocioException(409, "Identificador institucional já cadastrado");
        }
        Professor professor = new Professor(UUID.randomUUID(), nome, identificadorInstitucional, competencias);
        Banco.professores.put(professor.getId(), professor);
        Banco.professorIdPorIdentificador.put(identificadorInstitucional, professor.getId());
        AuditoriaService.registrar("Professor", professor.getId(), "CRIACAO", autorId, LocalDateTime.now(clock));
        return professor;
    }

    public Material cadastrarMaterial(String nome, String tipo, int quantidadeTotal, boolean restrito) {
        Banco.seedSeNecessario();
        validarMaterial(nome, tipo, quantidadeTotal);
        Material material = new Material(UUID.randomUUID(), nome, tipo, quantidadeTotal, restrito);
        Banco.materiais.put(material.getId(), material);
        AuditoriaService.registrar("Material", material.getId(), "CRIACAO", autorId, LocalDateTime.now(clock));
        return material;
    }

    public Manutencao cadastrarManutencao(Sala sala, LocalDateTime inicio, LocalDateTime fim, String motivo) {
        return registrarManutencao(sala, inicio, fim, motivo);
    }

    private Manutencao registrarManutencao(Object recurso, LocalDateTime inicio, LocalDateTime fim, String motivo) {
        Banco.seedSeNecessario();
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            throw new RegraNegocioException(400, "Período de manutenção inválido: fim deve ser posterior ao início (campos 'inicio' e 'fim')");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new RegraNegocioException(400, "Motivo da manutenção é obrigatório");
        }
        boolean colideComReservaAtivaNaoConfirmada = !reservasQueColidemComManutencao(recurso, inicio, fim).isEmpty();
        if (colideComReservaAtivaNaoConfirmada) {
            throw new RegraNegocioException(409, "Manutenção colide com reserva ativa; confirmação explícita necessária");
        }
        Manutencao manutencao = new Manutencao(UUID.randomUUID(), recurso, inicio, fim, motivo);
        Banco.manutencoes.put(manutencao.getId(), manutencao);
        AuditoriaService.registrar("Manutencao", manutencao.getId(), "CRIACAO", autorId, LocalDateTime.now(clock));
        return manutencao;
    }

    /**
     * RF-23: reservas {@code APROVADA} ou {@code EM_USO} do recurso que se sobrepoem ao periodo de manutencao, em ordem
     * de inicio. Sao elas que impedem o cadastro sem confirmacao explicita.
     */
    public List<Reserva> reservasQueColidemComManutencao(Object recurso, LocalDateTime inicio, LocalDateTime fim) {
        Banco.seedSeNecessario();
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            return List.of();
        }
        return Banco.reservas.values().stream()
                .filter(r -> r.getEstado() == EstadoReserva.APROVADA || r.getEstado() == EstadoReserva.EM_USO)
                .filter(r -> envolveRecurso(r, recurso))
                .filter(r -> Intervalos.sobrepoe(inicio, fim, r.getInicio(), r.getFim()))
                .sorted(java.util.Comparator.comparing(Reserva::getInicio))
                .toList();
    }

    /**
     * RF-21: encerra a manutencao antes do fim previsto (ou cancela uma futura). O bloqueio deixa de valer na hora e o
     * recurso volta a aparecer na busca; o evento {@code ENCERRAMENTO} fica na auditoria com o autor.
     */
    public Manutencao encerrarManutencao(UUID manutencaoId) {
        Banco.seedSeNecessario();
        Manutencao manutencao = manutencaoId == null ? null : Banco.manutencoes.remove(manutencaoId);
        if (manutencao == null) {
            throw new RegraNegocioException(404, "Manutenção inexistente ou já encerrada");
        }
        AuditoriaService.registrar("Manutencao", manutencao.getId(), "ENCERRAMENTO", autorId, LocalDateTime.now(clock));
        return manutencao;
    }

    /** Manutencoes cadastradas, da mais proxima para a mais distante. */
    public List<Manutencao> listarManutencoes() {
        Banco.seedSeNecessario();
        return Banco.manutencoes.values().stream()
                .sorted(java.util.Comparator.comparing(Manutencao::getInicio).thenComparing(m -> m.getId().toString()))
                .toList();
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
        AuditoriaService.registrar("Sala", sala.getId(), "INATIVACAO", autorId, LocalDateTime.now(clock));
    }

    /** RF-04: edita a sala no lugar; recusa sem alterar nada se algum campo for invalido. */
    public Sala atualizarSala(UUID id, String nome, String tipo, int capacidade, String localizacao, boolean restrito) {
        Banco.seedSeNecessario();
        Sala sala = existente(Banco.salas.get(id), "Sala");
        validarSala(nome, tipo, capacidade, localizacao);
        sala.atualizar(nome, tipo, capacidade, localizacao, restrito);
        AuditoriaService.registrar("Sala", sala.getId(), "ATUALIZACAO", autorId, LocalDateTime.now(clock));
        return sala;
    }

    /** RF-05: edita nome e competencias; o identificador institucional e a chave e nao muda. */
    public Professor atualizarProfessor(UUID id, String nome, List<Competencia> competencias) {
        Banco.seedSeNecessario();
        Professor professor = existente(Banco.professores.get(id), "Professor");
        validarProfessor(nome, competencias);
        professor.atualizar(nome, competencias);
        AuditoriaService.registrar("Professor", professor.getId(), "ATUALIZACAO", autorId, LocalDateTime.now(clock));
        return professor;
    }

    public void inativarProfessor(UUID id) {
        Banco.seedSeNecessario();
        Professor professor = existente(Banco.professores.get(id), "Professor");
        professor.inativar();
        AuditoriaService.registrar("Professor", professor.getId(), "INATIVACAO", autorId, LocalDateTime.now(clock));
    }

    /** RF-06: edita o material no lugar; recusa sem alterar nada se algum campo for invalido. */
    public Material atualizarMaterial(UUID id, String nome, String tipo, int quantidadeTotal, boolean restrito) {
        Banco.seedSeNecessario();
        Material material = existente(Banco.materiais.get(id), "Material");
        validarMaterial(nome, tipo, quantidadeTotal);
        material.atualizar(nome, tipo, quantidadeTotal, restrito);
        AuditoriaService.registrar("Material", material.getId(), "ATUALIZACAO", autorId, LocalDateTime.now(clock));
        return material;
    }

    public void inativarMaterial(UUID id) {
        Banco.seedSeNecessario();
        Material material = existente(Banco.materiais.get(id), "Material");
        material.inativar();
        AuditoriaService.registrar("Material", material.getId(), "INATIVACAO", autorId, LocalDateTime.now(clock));
    }

    public List<Sala> listarSalasAtivas() {
        Banco.seedSeNecessario();
        return Banco.salas.values().stream().filter(Sala::isAtivo).toList();
    }

    public List<Sala> listarSalasAtivas(Usuario solicitante) {
        if (solicitante == null) {
            throw new RegraNegocioException(401, "Autenticação necessária para consultar salas");
        }
        return listarSalasAtivas();
    }

    private static void validarSala(String nome, String tipo, int capacidade, String localizacao) {
        obrigatorio(nome, "Nome da sala é obrigatório");
        obrigatorio(tipo, "Tipo da sala é obrigatório");
        obrigatorio(localizacao, "Localização da sala é obrigatória (campo 'localizacao')");
        if (capacidade <= 0) {
            throw new RegraNegocioException(400, "Capacidade da sala deve ser positiva");
        }
    }

    private static void validarProfessor(String nome, List<Competencia> competencias) {
        obrigatorio(nome, "Nome do professor é obrigatório");
        if (competencias == null) {
            throw new RegraNegocioException(400, "Lista de competências do professor é obrigatória (campo 'competencias'; pode ser vazia)");
        }
    }

    private static void validarMaterial(String nome, String tipo, int quantidadeTotal) {
        obrigatorio(nome, "Nome do material é obrigatório");
        obrigatorio(tipo, "Tipo do material é obrigatório");
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
