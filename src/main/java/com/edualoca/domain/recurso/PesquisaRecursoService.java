package com.edualoca.domain.recurso;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.Intervalos;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.reserva.Reserva;

import java.time.LocalDateTime;
import java.util.List;

public class PesquisaRecursoService {

    public List<Sala> buscarSalas(String tipo, int capacidadeMinima, String localizacao,
                                   LocalDateTime inicio, LocalDateTime fim) {
        Banco.seedSeNecessario();
        validarIntervalo(inicio, fim);
        if (capacidadeMinima < 0) {
            throw new RegraNegocioException(400, "Capacidade mínima inválida: informe zero ou um número positivo de lugares");
        }
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

    /**
     * RF-10 / DEF-016 (opcao A, 12/11/2026): o material e exclusivo no intervalo, como na criacao e na constraint
     * {@code ex_reserva_recurso_sem_sobreposicao}. Material com reserva ativa sobreposta ou em manutencao nao aparece,
     * qualquer que seja a quantidade; {@code quantidadeDesejada} so precisa caber na quantidade total.
     */
    public List<Material> buscarMateriais(String tipo, int quantidadeDesejada, LocalDateTime inicio, LocalDateTime fim) {
        Banco.seedSeNecessario();
        validarIntervalo(inicio, fim);
        return Banco.materiais.values().stream()
                .filter(Material::isAtivo)
                .filter(m -> tipo == null || tipo.equalsIgnoreCase(m.getTipo()))
                .filter(m -> m.getQuantidadeTotal() >= quantidadeDesejada)
                .filter(m -> disponivel(m, inicio, fim))
                .toList();
    }

    private void validarIntervalo(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            throw new RegraNegocioException(400, "Intervalo de busca inválido: fim deve ser posterior ao início (campos 'inicio' e 'fim')");
        }
    }

    /** RF-11: recurso com reserva ativa sobreposta ou em manutencao no intervalo nao esta disponivel (DEF-012). */
    private boolean disponivel(Object recurso, LocalDateTime inicio, LocalDateTime fim) {
        if (emManutencao(recurso, inicio, fim)) {
            return false;
        }
        return Banco.reservas.values().stream()
                .filter(r -> r.getEstado().isAtiva())
                .filter(r -> envolveRecurso(r, recurso))
                .noneMatch(r -> Intervalos.sobrepoe(inicio, fim, r.getInicio(), r.getFim()));
    }

    /** Vale para sala, professor e material: a manutencao guarda o proprio recurso. */
    private static boolean emManutencao(Object recurso, LocalDateTime inicio, LocalDateTime fim) {
        return Banco.manutencoes.values().stream()
                .anyMatch(m -> m.getRecurso() == recurso && Intervalos.sobrepoe(inicio, fim, m.getInicio(), m.getFim()));
    }

    private static boolean envolveRecurso(Reserva reserva, Object recurso) {
        return reserva.getRecursos().stream().anyMatch(rr -> rr.getRecurso() == recurso);
    }
}
