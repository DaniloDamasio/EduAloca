package com.edualoca.api.relatorio;

import com.edualoca.domain.relatorio.UtilizacaoPorPeriodo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/** Corpo de GET /relatorios/utilizacao. Horas em decimal com 2 casas (90 min = 1.50). */
public record UtilizacaoResposta(List<Recurso> recursos, List<ProfessorCarga> professores, long conflitosEvitados) {

    public record Recurso(String tipo, UUID id, String nome, BigDecimal horasReservadas) {
    }

    public record ProfessorCarga(UUID id, String nome, BigDecimal cargaHorariaAlocada) {
    }

    static UtilizacaoResposta de(UtilizacaoPorPeriodo relatorio) {
        return new UtilizacaoResposta(
                relatorio.recursos().stream().map(r -> new Recurso(r.tipo(), r.id(), r.nome(), horas(r.minutos()))).toList(),
                relatorio.professores().stream().map(p -> new ProfessorCarga(p.id(), p.nome(), horas(p.minutos()))).toList(),
                relatorio.conflitosEvitados());
    }

    private static BigDecimal horas(long minutos) {
        return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
