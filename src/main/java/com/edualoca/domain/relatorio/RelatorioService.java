package com.edualoca.domain.relatorio;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.Intervalos;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.relatorio.UtilizacaoPorPeriodo.CargaProfessor;
import com.edualoca.domain.relatorio.UtilizacaoPorPeriodo.UsoRecurso;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RelatorioService {

    /** Maior periodo aceito pelo relatorio por periodo (um ano, contando bissexto). */
    static final Duration PERIODO_MAXIMO = Duration.ofDays(366);

    /** Reservas que contam como uso: confirmadas. Pendente, rejeitada, cancelada e nao comparecimento nao contam. */
    private static final Set<EstadoReserva> ESTADOS_DE_USO =
            EnumSet.of(EstadoReserva.APROVADA, EstadoReserva.EM_USO, EstadoReserva.CONCLUIDA);

    /**
     * RF-34/RF-35: horas reservadas por recurso (sala e material), carga horaria por professor e conflitos evitados
     * no periodo semiaberto [inicio, fim). Reserva que cruza a borda conta so a parte de dentro do periodo.
     */
    public UtilizacaoPorPeriodo gerarUtilizacaoPorPeriodo(Usuario autor, LocalDateTime inicio, LocalDateTime fim) {
        Banco.seedSeNecessario();
        validarAcesso(autor);
        validarIntervalo(inicio, fim);
        if (Duration.between(inicio, fim).compareTo(PERIODO_MAXIMO) > 0) {
            throw new RegraNegocioException(400, "Periodo do relatorio nao pode passar de 366 dias");
        }
        Map<UUID, UsoRecurso> recursos = new LinkedHashMap<>();
        Map<UUID, CargaProfessor> professores = new LinkedHashMap<>();
        for (Reserva reserva : Banco.reservas.values()) {
            if (!ESTADOS_DE_USO.contains(reserva.getEstado())
                    || !Intervalos.sobrepoe(reserva.getInicio(), reserva.getFim(), inicio, fim)) {
                continue;
            }
            LocalDateTime inicioDentro = reserva.getInicio().isBefore(inicio) ? inicio : reserva.getInicio();
            LocalDateTime fimDentro = reserva.getFim().isAfter(fim) ? fim : reserva.getFim();
            long minutos = Duration.between(inicioDentro, fimDentro).toMinutes();
            reserva.getRecursos().stream().map(ReservaRecurso::getRecurso).distinct().forEach(recurso -> {
                switch (recurso) {
                    case Sala s -> recursos.merge(s.getId(), new UsoRecurso("SALA", s.getId(), s.getNome(), minutos), UsoRecurso::somar);
                    case Material m -> recursos.merge(m.getId(), new UsoRecurso("MATERIAL", m.getId(), m.getNome(), minutos), UsoRecurso::somar);
                    case Professor p -> professores.merge(p.getId(), new CargaProfessor(p.getId(), p.getNome(), minutos), CargaProfessor::somar);
                    default -> { }
                }
            });
        }
        return new UtilizacaoPorPeriodo(
                recursos.values().stream()
                        .sorted(Comparator.comparingLong(UsoRecurso::minutos).reversed().thenComparing(UsoRecurso::nome))
                        .toList(),
                professores.values().stream()
                        .sorted(Comparator.comparingLong(CargaProfessor::minutos).reversed().thenComparing(CargaProfessor::nome))
                        .toList(),
                contarRejeicoes(inicio, fim));
    }

    public RelatorioUtilizacao gerarRelatorioUtilizacao(Usuario autor, LocalDateTime inicio, LocalDateTime fim) {
        Banco.seedSeNecessario();
        validarAcesso(autor);
        validarIntervalo(inicio, fim);
        var registros = Banco.reservas.values().stream()
                .filter(r -> r.getEstado() == EstadoReserva.CONCLUIDA || r.getEstado() == EstadoReserva.EM_USO)
                .filter(r -> !r.getInicio().isBefore(inicio) && !r.getFim().isAfter(fim))
                .toList();
        return new RelatorioUtilizacao(registros);
    }

    public long contarRejeicoesPorSobreposicao(Usuario autor, LocalDateTime inicio, LocalDateTime fim) {
        Banco.seedSeNecessario();
        validarAcesso(autor);
        validarIntervalo(inicio, fim);
        return contarRejeicoes(inicio, fim);
    }

    /** Rejeicoes por sobreposicao registradas em auditoria em [inicio, fim) (criterio de aceite de RF-35). */
    private long contarRejeicoes(LocalDateTime inicio, LocalDateTime fim) {
        return Banco.auditoria.stream()
                .filter(e -> "Reserva".equals(e.getEntidade()))
                .filter(e -> "REJEICAO_SOBREPOSICAO".equals(e.getAcao()))
                .filter(e -> !e.getDataHora().isBefore(inicio) && e.getDataHora().isBefore(fim))
                .count();
    }

    /** RF-36: so Responsavel e Administrador. Publico para que a borda HTTP cheque o perfil antes da entrada. */
    public void validarAcesso(Usuario autor) {
        if (autor.getPerfil() != Perfil.RESPONSAVEL && autor.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new RegraNegocioException(403, "Apenas Responsavel ou Administrador podem acessar relatorios");
        }
    }

    private void validarIntervalo(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            throw new RegraNegocioException(400, "Intervalo de relatorio invalido: fim deve ser posterior ao inicio");
        }
    }
}
