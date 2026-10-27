package com.edualoca.domain.reserva;

import com.edualoca.domain.auditoria.AuditoriaService;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Manutencao;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class ReservaService {

    private static final ConcurrentHashMap<UUID, ReentrantLock> LOCKS_POR_RECURSO = new ConcurrentHashMap<>();

    public Reserva criar(Usuario solicitante, LocalDateTime inicio, LocalDateTime fim,
                          String finalidade, List<ReservaRecurso> recursos) {
        Banco.seedSeNecessario();
        validarIntervalo(inicio, fim);
        if (finalidade == null || finalidade.isBlank()) {
            throw new RegraNegocioException(400, "Finalidade da reserva e obrigatoria");
        }
        if (recursos == null || recursos.isEmpty()) {
            throw new RegraNegocioException(400, "Reserva deve ter ao menos um recurso vinculado");
        }

        List<ReentrantLock> locksAdquiridos = adquirirLocksOrdenados(recursos);
        try {
            for (ReservaRecurso rr : recursos) {
                validarRecursoDisponivel(rr.getRecurso(), inicio, fim, null);
            }

            boolean algumRestrito = recursos.stream().anyMatch(this::ehRestrito);
            EstadoReserva estadoInicial = algumRestrito ? EstadoReserva.SOLICITADA : EstadoReserva.APROVADA;

            Reserva reserva = new Reserva(UUID.randomUUID(), solicitante.getId(), inicio, fim,
                    finalidade, recursos, estadoInicial);
            Banco.reservas.put(reserva.getId(), reserva);
            AuditoriaService.registrar("Reserva", reserva.getId(), "CRIACAO", solicitante.getId());
            return reserva;
        } finally {
            liberarLocks(locksAdquiridos);
        }
    }

    public Reserva alterar(Usuario autor, UUID reservaId, LocalDateTime novoInicio, LocalDateTime novoFim) {
        Banco.seedSeNecessario();
        validarIntervalo(novoInicio, novoFim);
        Reserva reserva = Banco.reservas.get(reservaId);
        if (reserva == null) {
            throw new RegraNegocioException(409, "Reserva inexistente");
        }
        if (!reserva.getSolicitanteId().equals(autor.getId())) {
            throw new RegraNegocioException(403, "Usuario nao autorizado a alterar reserva de outro solicitante");
        }
        if (reserva.getEstado() != EstadoReserva.SOLICITADA && reserva.getEstado() != EstadoReserva.APROVADA) {
            throw new RegraNegocioException(409, "Reserva em estado incompativel com alteracao");
        }
        List<ReentrantLock> locksAdquiridos = adquirirLocksOrdenados(reserva.getRecursos());
        try {
            for (ReservaRecurso rr : reserva.getRecursos()) {
                validarRecursoDisponivel(rr.getRecurso(), novoInicio, novoFim, reservaId);
            }
            reserva.setInicio(novoInicio);
            reserva.setFim(novoFim);
            AuditoriaService.registrar("Reserva", reserva.getId(), "ATUALIZACAO", autor.getId());
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
        Reserva reserva = Banco.reservas.get(reservaId);
        if (reserva == null) {
            throw new RegraNegocioException(409, "Reserva inexistente");
        }
        if (!reserva.getSolicitanteId().equals(autor.getId())) {
            throw new RegraNegocioException(403, "Usuario nao autorizado a cancelar reserva de outro solicitante");
        }
        if (reserva.getEstado() == EstadoReserva.CONCLUIDA || reserva.getEstado() == EstadoReserva.CANCELADA
                || reserva.getEstado() == EstadoReserva.REJEITADA || reserva.getEstado() == EstadoReserva.NAO_COMPARECEU) {
            throw new RegraNegocioException(409, "Reserva em estado terminal nao pode ser cancelada");
        }
        reserva.setEstado(EstadoReserva.CANCELADA);
        AuditoriaService.registrar("Reserva", reserva.getId(), "CANCELAMENTO", autor.getId());
        return reserva;
    }

    public Reserva aprovar(Usuario responsavel, UUID reservaId) {
        Banco.seedSeNecessario();
        if (responsavel.getPerfil() != Perfil.RESPONSAVEL && responsavel.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new RegraNegocioException(403, "Apenas Responsavel ou Administrador podem aprovar reservas");
        }
        Reserva reserva = Banco.reservas.get(reservaId);
        if (reserva == null || reserva.getEstado() != EstadoReserva.SOLICITADA) {
            throw new RegraNegocioException(409, "Reserva nao esta em estado SOLICITADA para aprovacao");
        }
        reserva.setEstado(EstadoReserva.APROVADA);
        AuditoriaService.registrar("Reserva", reserva.getId(), "APROVACAO", responsavel.getId());
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
        Reserva reserva = Banco.reservas.get(reservaId);
        if (reserva == null || reserva.getEstado() != EstadoReserva.SOLICITADA) {
            throw new RegraNegocioException(409, "Reserva nao esta em estado SOLICITADA para rejeicao");
        }
        reserva.setEstado(EstadoReserva.REJEITADA);
        AuditoriaService.registrar("Reserva", reserva.getId(), "REJEICAO", responsavel.getId());
        return reserva;
    }

    public Reserva consultar(Usuario autor, UUID reservaId) {
        Banco.seedSeNecessario();
        Reserva reserva = Banco.reservas.get(reservaId);
        if (autor.getPerfil() == Perfil.SOLICITANTE
                && (reserva == null || !reserva.getSolicitanteId().equals(autor.getId()))) {
            throw new RegraNegocioException(403, "Solicitante nao pode consultar reserva de outro solicitante");
        }
        if (reserva == null) {
            throw new RegraNegocioException(409, "Reserva inexistente");
        }
        return reserva;
    }

    public void excluir(Usuario autor, UUID reservaId) {
        Banco.seedSeNecessario();
        Reserva reserva = Banco.reservas.get(reservaId);
        if (reserva != null && reserva.getEstado() == EstadoReserva.SOLICITADA) {
            throw new RegraNegocioException(405, "Exclusao fisica de reserva nao e exposta pela API");
        }
        throw new RegraNegocioException(403, "Reserva iniciada nao pode ser excluida");
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

    private void validarRecursoDisponivel(Object recurso, LocalDateTime inicio, LocalDateTime fim, UUID reservaIgnorada) {
        boolean emManutencao = Banco.manutencoes.values().stream()
                .anyMatch(m -> m.getSala() == recurso && sobrepoe(inicio, fim, m.getInicio(), m.getFim()));
        if (emManutencao) {
            throw new RegraNegocioException(409, "Recurso em manutencao no intervalo solicitado");
        }
        boolean conflito = Banco.reservas.values().stream()
                .filter(r -> !r.getId().equals(reservaIgnorada))
                .filter(r -> ehAtiva(r.getEstado()))
                .filter(r -> r.getRecursos().stream().anyMatch(rr -> rr.getRecurso() == recurso))
                .anyMatch(r -> sobrepoe(inicio, fim, r.getInicio(), r.getFim()));
        if (conflito) {
            throw new RegraNegocioException(409, "Recurso indisponivel: sobreposicao com reserva ativa");
        }
    }

    private static boolean ehAtiva(EstadoReserva estado) {
        return estado == EstadoReserva.SOLICITADA || estado == EstadoReserva.APROVADA || estado == EstadoReserva.EM_USO;
    }

    private static boolean sobrepoe(LocalDateTime inicioA, LocalDateTime fimA, LocalDateTime inicioB, LocalDateTime fimB) {
        return inicioA.isBefore(fimB) && inicioB.isBefore(fimA);
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
