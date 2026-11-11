package com.edualoca.domain.relatorio;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de {@link RelatorioService#gerarUtilizacaoPorPeriodo}. Os tempos ficam em minutos inteiros no dominio;
 * a conversao para horas e feita so na borda HTTP.
 */
public record UtilizacaoPorPeriodo(List<UsoRecurso> recursos, List<CargaProfessor> professores, long conflitosEvitados) {

    /** Sala ou material e o total de minutos reservados dentro do periodo. */
    public record UsoRecurso(String tipo, UUID id, String nome, long minutos) {

        UsoRecurso somar(UsoRecurso outro) {
            return new UsoRecurso(tipo, id, nome, minutos + outro.minutos);
        }
    }

    /** Professor e o total de minutos alocados em reservas dentro do periodo. */
    public record CargaProfessor(UUID id, String nome, long minutos) {

        CargaProfessor somar(CargaProfessor outro) {
            return new CargaProfessor(id, nome, minutos + outro.minutos);
        }
    }
}
