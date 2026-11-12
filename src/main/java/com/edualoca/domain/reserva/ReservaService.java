package com.edualoca.domain.reserva;

import com.edualoca.domain.auditoria.AuditoriaService;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.Intervalos;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Manutencao;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class ReservaService {

    private static final ConcurrentHashMap<UUID, ReentrantLock> LOCKS_POR_RECURSO = new ConcurrentHashMap<>();

    private final Clock clock;
    private final ReservaRepositorio repositorio;
    private final HistoricoReservaRepositorio historico;
    private final Transacao transacao;

    public ReservaService() {
        this(Clock.systemDefaultZone());
    }

    public ReservaService(Clock clock) {
        this(clock, new ReservaRepositorioEmMemoria());
    }

    public ReservaService(Clock clock, ReservaRepositorio repositorio) {
        this(clock, repositorio, new HistoricoReservaRepositorioEmMemoria(), Transacao.direta());
    }

    public ReservaService(Clock clock, ReservaRepositorio repositorio, HistoricoReservaRepositorio historico,
                          Transacao transacao) {
        this.clock = clock;
        this.repositorio = repositorio;
        this.historico = historico;
        this.transacao = transacao;
    }

    public Reserva criar(Usuario solicitante, LocalDateTime inicio, LocalDateTime fim,
                          String finalidade, List<ReservaRecurso> recursos) {
        Banco.seedSeNecessario();
        // RF-12: so o Solicitante cria reserva (DEF-009). Perfil antes da entrada: 403 nao depende do corpo.
        if (solicitante.getPerfil() != Perfil.SOLICITANTE) {
            throw new RegraNegocioException(403, "Apenas o Solicitante pode criar reservas");
        }
        validarIntervalo(inicio, fim);
        if (finalidade == null || finalidade.isBlank()) {
            throw new RegraNegocioException(400, "Finalidade da reserva e obrigatoria");
        }
        if (recursos == null || recursos.isEmpty()) {
            throw new RegraNegocioException(400, "Reserva deve ter ao menos um recurso vinculado");
        }

        UUID reservaId = UUID.randomUUID();
        List<ReentrantLock> locksAdquiridos = adquirirLocksOrdenados(recursos);
        try {
            for (ReservaRecurso rr : recursos) {
                validarRecursoDisponivel(rr.getRecurso(), inicio, fim, null, reservaId, solicitante);
            }

            boolean algumRestrito = recursos.stream().anyMatch(this::ehRestrito);
            EstadoReserva estadoInicial = algumRestrito ? EstadoReserva.SOLICITADA : EstadoReserva.APROVADA;

            Reserva reserva = new Reserva(reservaId, solicitante.getId(), inicio, fim,
                    finalidade, recursos, estadoInicial);
            String motivo = algumRestrito ? "Criacao: recurso restrito aguarda aprovacao" : "Criacao: aprovada automaticamente";
            auditandoRecusaDoBanco(reservaId, solicitante, () -> transacao.executar(() -> {
                repositorio.inserir(reserva);
                historico.registrar(registro(reserva, solicitante, null, motivo));
            }));
            AuditoriaService.registrar("Reserva", reserva.getId(), "CRIACAO", solicitante.getId(), LocalDateTime.now(clock));
            return reserva;
        } finally {
            liberarLocks(locksAdquiridos);
        }
    }

    public Reserva alterar(Usuario autor, UUID reservaId, LocalDateTime novoInicio, LocalDateTime novoFim) {
        Banco.seedSeNecessario();
        // Intervalo antes da posse: com intervalo invalido, id alheio e inexistente recebem o mesmo 400.
        validarIntervalo(novoInicio, novoFim);
        Reserva reserva = repositorio.buscar(reservaId);
        if (!ehDono(autor, reserva)) {
            throw new RegraNegocioException(403, "Usuario nao autorizado a alterar reserva de outro solicitante");
        }
        if (reserva.getEstado() != EstadoReserva.SOLICITADA && reserva.getEstado() != EstadoReserva.APROVADA) {
            throw new RegraNegocioException(409, "Reserva em estado incompativel com alteracao");
        }
        List<ReentrantLock> locksAdquiridos = adquirirLocksOrdenados(reserva.getRecursos());
        try {
            for (ReservaRecurso rr : reserva.getRecursos()) {
                validarRecursoDisponivel(rr.getRecurso(), novoInicio, novoFim, reservaId, reservaId, autor);
            }
            LocalDateTime inicioAnterior = reserva.getInicio();
            LocalDateTime fimAnterior = reserva.getFim();
            reserva.setInicio(novoInicio);
            reserva.setFim(novoFim);
            try {
                auditandoRecusaDoBanco(reservaId, autor, () -> repositorio.atualizar(reserva));
            } catch (RuntimeException e) {
                // A gravacao foi recusada (ex.: sobreposicao detectada pelo banco); desfaz a alteracao em memoria.
                reserva.setInicio(inicioAnterior);
                reserva.setFim(fimAnterior);
                throw e;
            }
            AuditoriaService.registrar("Reserva", reserva.getId(), "ATUALIZACAO", autor.getId(), LocalDateTime.now(clock));
            return reserva;
        } finally {
            liberarLocks(locksAdquiridos);
        }
    }

    public Reserva cancelar(Usuario autor, UUID reservaId, String motivo) {
        Banco.seedSeNecessario();
        if (motivo == null || motivo.isBlank()) {
            throw new RegraNegocioException(400, "Motivo do cancelamento e obrigatorio");
        }
        Reserva reserva = repositorio.buscar(reservaId);
        if (!ehDono(autor, reserva)) {
            throw new RegraNegocioException(403, "Usuario nao autorizado a cancelar reserva de outro solicitante");
        }
        if (reserva.getEstado() == EstadoReserva.CONCLUIDA || reserva.getEstado() == EstadoReserva.CANCELADA
                || reserva.getEstado() == EstadoReserva.REJEITADA || reserva.getEstado() == EstadoReserva.NAO_COMPARECEU) {
            throw new RegraNegocioException(409, "Reserva em estado terminal nao pode ser cancelada");
        }
        transicionar(reserva, autor, motivo, () -> reserva.setEstado(EstadoReserva.CANCELADA));
        AuditoriaService.registrar("Reserva", reserva.getId(), "CANCELAMENTO", autor.getId(), LocalDateTime.now(clock));
        return reserva;
    }

    public Reserva aprovar(Usuario responsavel, UUID reservaId) {
        Banco.seedSeNecessario();
        if (responsavel.getPerfil() != Perfil.RESPONSAVEL && responsavel.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new RegraNegocioException(403, "Apenas Responsavel ou Administrador podem aprovar reservas");
        }
        Reserva reserva = repositorio.buscar(reservaId);
        if (reserva == null) {
            throw new RegraNegocioException(409, "Reserva nao esta em estado SOLICITADA para aprovacao");
        }
        exigirDecisorCompetente(responsavel, reserva, "aprovar");
        if (reserva.getEstado() != EstadoReserva.SOLICITADA) {
            throw new RegraNegocioException(409, "Reserva nao esta em estado SOLICITADA para aprovacao");
        }
        transicionar(reserva, responsavel, "Aprovacao da solicitacao", () -> reserva.setEstado(EstadoReserva.APROVADA));
        AuditoriaService.registrar("Reserva", reserva.getId(), "APROVACAO", responsavel.getId(), LocalDateTime.now(clock));
        return reserva;
    }

    /**
     * RF-18 / P-07 (decisao de 12/11/2026: inicio manual). So {@code APROVADA} vai para {@code EM_USO}: reserva
     * restrita {@code SOLICITADA} nunca pula a aprovacao. Mesmo controle de acesso da aprovacao: Administrador, ou
     * Responsavel do local, checado antes do estado. Grava historico e um evento {@code INICIO_USO}.
     */
    public Reserva iniciarUso(Usuario ator, UUID reservaId) {
        Banco.seedSeNecessario();
        if (ator.getPerfil() != Perfil.RESPONSAVEL && ator.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new RegraNegocioException(403, "Apenas Responsavel ou Administrador podem iniciar o uso da reserva");
        }
        Reserva reserva = repositorio.buscar(reservaId);
        if (reserva == null) {
            throw new RegraNegocioException(409, "Reserva nao esta APROVADA para iniciar o uso");
        }
        exigirDecisorCompetente(ator, reserva, "iniciar o uso de");
        if (reserva.getEstado() != EstadoReserva.APROVADA) {
            throw new RegraNegocioException(409, "Reserva nao esta APROVADA para iniciar o uso");
        }
        transicionar(reserva, ator, "Inicio do uso", () -> reserva.setEstado(EstadoReserva.EM_USO));
        AuditoriaService.registrar("Reserva", reserva.getId(), "INICIO_USO", ator.getId(), LocalDateTime.now(clock));
        return reserva;
    }

    public Reserva rejeitar(Usuario responsavel, UUID reservaId, String justificativa) {
        Banco.seedSeNecessario();
        if (responsavel.getPerfil() != Perfil.RESPONSAVEL && responsavel.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new RegraNegocioException(403, "Apenas Responsavel ou Administrador podem rejeitar reservas");
        }
        if (justificativa == null || justificativa.isBlank()) {
            throw new RegraNegocioException(400, "Justificativa da rejeicao e obrigatoria");
        }
        Reserva reserva = repositorio.buscar(reservaId);
        if (reserva == null) {
            throw new RegraNegocioException(409, "Reserva nao esta em estado SOLICITADA para rejeicao");
        }
        exigirDecisorCompetente(responsavel, reserva, "rejeitar");
        if (reserva.getEstado() != EstadoReserva.SOLICITADA) {
            throw new RegraNegocioException(409, "Reserva nao esta em estado SOLICITADA para rejeicao");
        }
        transicionar(reserva, responsavel, justificativa, () -> reserva.rejeitar(justificativa));
        AuditoriaService.registrar("Reserva", reserva.getId(), "REJEICAO", responsavel.getId(), LocalDateTime.now(clock));
        return reserva;
    }

    public Reserva consultar(Usuario autor, UUID reservaId) {
        Banco.seedSeNecessario();
        Reserva reserva = repositorio.buscar(reservaId);
        if (autor.getPerfil() == Perfil.SOLICITANTE
                && (reserva == null || !reserva.getSolicitanteId().equals(autor.getId()))) {
            throw new RegraNegocioException(403, "Solicitante nao pode consultar reserva de outro solicitante");
        }
        if (reserva == null) {
            throw new RegraNegocioException(409, "Reserva inexistente");
        }
        return reserva;
    }

    /**
     * Historico auditavel da reserva, so para Responsavel e Administrador. O perfil e checado antes da existencia
     * para que o Solicitante nao descubra quais ids de reserva existem.
     */
    public List<RegistroHistoricoReserva> consultarHistorico(Usuario autor, UUID reservaId) {
        Banco.seedSeNecessario();
        if (autor.getPerfil() != Perfil.RESPONSAVEL && autor.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new RegraNegocioException(403, "Apenas Responsavel ou Administrador podem consultar o historico da reserva");
        }
        if (repositorio.buscar(reservaId) == null) {
            throw new RegraNegocioException(409, "Reserva inexistente");
        }
        return historico.listarPorReserva(reservaId);
    }

    /**
     * RF-30 / P-3: nenhuma reserva e apagada fisicamente, por nenhum perfil e em nenhum estado; o fim de uma reserva e
     * o cancelamento. Sempre 403 (a rota existe, entao 405 contradiria o significado do codigo; DEF-006), sem consultar
     * a reserva, para que a resposta nao revele estado nem existencia.
     */
    public void excluir(Usuario autor, UUID reservaId) {
        throw new RegraNegocioException(403, "Reservas nao podem ser excluidas; para encerrar uma reserva, cancele-a");
    }

    /**
     * Aplica a mudanca de estado e grava reserva e historico na mesma transacao. Se a gravacao falhar, o banco
     * desfaz as duas escritas e aqui a reserva em memoria volta ao estado anterior.
     */
    private void transicionar(Reserva reserva, Usuario autor, String motivo, Runnable mudanca) {
        EstadoReserva estadoAnterior = reserva.getEstado();
        String justificativaAnterior = reserva.getJustificativaRejeicao();
        mudanca.run();
        try {
            transacao.executar(() -> {
                repositorio.atualizar(reserva);
                historico.registrar(registro(reserva, autor, estadoAnterior, motivo));
            });
        } catch (RuntimeException e) {
            reserva.desfazerTransicao(estadoAnterior, justificativaAnterior);
            throw e;
        }
    }

    /**
     * P-2 / RF-19 (DEF-010): o Administrador decide qualquer reserva; o Responsavel so decide reserva com sala de uma
     * localizacao sob sua responsabilidade ({@link Banco#responsavelPorLocalizacao}). Checado antes do estado, para que
     * o Responsavel nao competente nao descubra em que estado a reserva esta.
     *
     * <p>Lacuna conhecida: material e professor nao tem localizacao. Reserva sem nenhuma sala fica com qualquer
     * Responsavel, como antes, ate o vinculo responsavel-recurso ser modelado (registrado em DEF-010).
     */
    private static void exigirDecisorCompetente(Usuario autor, Reserva reserva, String acao) {
        if (autor.getPerfil() == Perfil.ADMINISTRADOR) {
            return;
        }
        List<Sala> salas = reserva.getRecursos().stream().map(ReservaRecurso::getRecurso)
                .filter(Sala.class::isInstance).map(Sala.class::cast).toList();
        boolean competente = salas.isEmpty() || salas.stream()
                .anyMatch(sala -> autor.getId().equals(Banco.responsavelPorLocalizacao.get(sala.getLocalizacao())));
        if (!competente) {
            throw new RegraNegocioException(403,
                    "Apenas o Responsavel pelo local do recurso (ou o Administrador) pode " + acao + " esta reserva");
        }
    }

    /**
     * Posse (RF-03, P-1). Reserva inexistente conta como "nao e sua": o 403 e o mesmo do id alheio, para que ninguem
     * descubra quais ids existem comparando respostas (DEF-005).
     */
    private static boolean ehDono(Usuario autor, Reserva reserva) {
        return reserva != null && reserva.getSolicitanteId().equals(autor.getId());
    }

    private RegistroHistoricoReserva registro(Reserva reserva, Usuario autor, EstadoReserva estadoAnterior, String motivo) {
        return new RegistroHistoricoReserva(UUID.randomUUID(), reserva.getId(), autor.getId(), clock.instant(),
                estadoAnterior, reserva.getEstado(), motivo);
    }

    private void validarIntervalo(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            throw new RegraNegocioException(400, "Intervalo invalido: fim deve ser posterior ao inicio");
        }
    }

    private boolean ehRestrito(ReservaRecurso rr) {
        Object recurso = rr.getRecurso();
        if (recurso instanceof Sala sala) {
            return sala.isRestrito();
        }
        if (recurso instanceof com.edualoca.domain.recurso.Material material) {
            return material.isRestrito();
        }
        return false;
    }

    private void validarRecursoDisponivel(Object recurso, LocalDateTime inicio, LocalDateTime fim, UUID reservaIgnorada,
                                          UUID reservaTentada, Usuario autor) {
        boolean emManutencao = Banco.manutencoes.values().stream()
                .anyMatch(m -> m.getRecurso() == recurso && Intervalos.sobrepoe(inicio, fim, m.getInicio(), m.getFim()));
        if (emManutencao) {
            throw new RegraNegocioException(409, "Recurso em manutencao no intervalo solicitado");
        }
        if (repositorio.existeConflito(recurso, inicio, fim, reservaIgnorada)) {
            registrarRejeicaoPorSobreposicao(reservaTentada, autor);
            throw new RegraNegocioException(409, "Recurso indisponivel: sobreposicao com reserva ativa");
        }
    }

    /**
     * Na gravacao, o unico 409 possivel vem da constraint de exclusao do banco (ADR-009): outra instancia gravou
     * reserva sobreposta depois da checagem. Tambem e um conflito evitado (RF-35).
     */
    private void auditandoRecusaDoBanco(UUID reservaTentada, Usuario autor, Runnable gravacao) {
        try {
            gravacao.run();
        } catch (RegraNegocioException e) {
            if (e.getStatusHttp() == 409) {
                registrarRejeicaoPorSobreposicao(reservaTentada, autor);
            }
            throw e;
        }
    }

    /** RF-28/RF-35: base da contagem de conflitos evitados no relatorio de utilizacao. */
    private void registrarRejeicaoPorSobreposicao(UUID reservaTentada, Usuario autor) {
        AuditoriaService.registrar("Reserva", reservaTentada, "REJEICAO_SOBREPOSICAO", autor.getId(), LocalDateTime.now(clock));
    }

    private List<ReentrantLock> adquirirLocksOrdenados(List<ReservaRecurso> recursos) {
        List<ReentrantLock> locks = recursos.stream()
                .map(rr -> LOCKS_POR_RECURSO.computeIfAbsent(identidadeDoRecurso(rr.getRecurso()), k -> new ReentrantLock()))
                .distinct()
                .sorted((a, b) -> Integer.compare(System.identityHashCode(a), System.identityHashCode(b)))
                .toList();
        locks.forEach(ReentrantLock::lock);
        return locks;
    }

    private void liberarLocks(List<ReentrantLock> locks) {
        for (int i = locks.size() - 1; i >= 0; i--) {
            locks.get(i).unlock();
        }
    }

    private UUID identidadeDoRecurso(Object recurso) {
        if (recurso instanceof Sala sala) {
            return sala.getId();
        }
        if (recurso instanceof com.edualoca.domain.recurso.Professor professor) {
            return professor.getId();
        }
        if (recurso instanceof com.edualoca.domain.recurso.Material material) {
            return material.getId();
        }
        return UUID.nameUUIDFromBytes(String.valueOf(System.identityHashCode(recurso)).getBytes());
    }
}
