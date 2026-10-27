package com.edualoca.domain.recurso;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;

import java.time.LocalDateTime;
import java.util.List;

public class PesquisaRecursoService {

    public List<Sala> buscarSalas(String tipo, int capacidadeMinima, String localizacao,
                                   LocalDateTime inicio, LocalDateTime fim) {
        Banco.seedSeNecessario();
        validarIntervalo(inicio, fim);
        return Banco.salas.values().stream()
                .filter(Sala::isAtivo)
                .filter(s -> tipo == null || tipo.equalsIgnoreCase(s.getTipo()))
                .filter(s -> s.getCapacidade() >= capacidadeMinima)
                .filter(s -> localizacao == null || localizacao.equalsIgnoreCase(s.getLocalizacao()))
                .filter(s -> disponivel(s, inicio, fim))
                .toList();
    }

    public List<Professor> buscarProfessores(String competencia, LocalDateTime inicio, LocalDateTime fim) {
        Banco.seedSeNecessario();
        validarIntervalo(inicio, fim);
        return Banco.professores.values().stream()
                .filter(Professor::isAtivo)
                .filter(p -> p.getCompetencias().stream().anyMatch(c -> c.getNome().equalsIgnoreCase(competencia)))
                .filter(p -> disponivel(p, inicio, fim))
                .toList();
    }

    public List<Material> buscarMateriais(String tipo, int quantidadeDesejada, LocalDateTime inicio, LocalDateTime fim) {
        Banco.seedSeNecessario();
        validarIntervalo(inicio, fim);
        return Banco.materiais.values().stream()
                .filter(Material::isAtivo)
                .filter(m -> tipo == null || tipo.equalsIgnoreCase(m.getTipo()))
                .filter(m -> quantidadeLivre(m, inicio, fim) >= quantidadeDesejada)
                .toList();
    }

    private void validarIntervalo(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            throw new RegraNegocioException(400, "Intervalo de busca invalido: fim deve ser posterior ao inicio");
        }
    }

    private boolean disponivel(Object recurso, LocalDateTime inicio, LocalDateTime fim) {
        if (recurso instanceof Sala sala) {
            boolean emManutencao = Banco.manutencoes.values().stream()
                    .anyMatch(m -> m.getSala() == sala && sobrepoe(inicio, fim, m.getInicio(), m.getFim()));
            if (emManutencao) {
                return false;
            }
        }
        return Banco.reservas.values().stream()
                .filter(r -> ehAtiva(r.getEstado()))
                .filter(r -> envolveRecurso(r, recurso))
                .noneMatch(r -> sobrepoe(inicio, fim, r.getInicio(), r.getFim()));
    }

    private int quantidadeLivre(Material material, LocalDateTime inicio, LocalDateTime fim) {
        int reservado = Banco.reservas.values().stream()
                .filter(r -> ehAtiva(r.getEstado()))
                .filter(r -> sobrepoe(inicio, fim, r.getInicio(), r.getFim()))
                .flatMap(r -> r.getRecursos().stream())
                .filter(rr -> rr.getRecurso() == material)
                .mapToInt(ReservaRecurso::getQuantidade)
                .sum();
        return material.getQuantidadeTotal() - reservado;
    }

    private static boolean envolveRecurso(Reserva reserva, Object recurso) {
        return reserva.getRecursos().stream().anyMatch(rr -> rr.getRecurso() == recurso);
    }

    private static boolean ehAtiva(EstadoReserva estado) {
        return estado == EstadoReserva.SOLICITADA || estado == EstadoReserva.APROVADA || estado == EstadoReserva.EM_USO;
    }

    private static boolean sobrepoe(LocalDateTime inicioA, LocalDateTime fimA, LocalDateTime inicioB, LocalDateTime fimB) {
        return inicioA.isBefore(fimB) && inicioB.isBefore(fimA);
    }
}
