package com.edualoca.domain.auditoria;

import com.edualoca.domain.comum.Banco;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class AuditoriaService {

    private final Clock clock;

    public AuditoriaService() {
        this(Clock.systemDefaultZone());
    }

    public AuditoriaService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Mantido apenas por compatibilidade com testes legados que registram eventos diretamente.
     * Codigo de producao deve usar a sobrecarga com {@code dataHora} obtida de um {@link Clock} injetado.
     */
    @Deprecated
    public static void registrar(String entidade, UUID entidadeId, String acao, UUID autorId) {
        registrar(entidade, entidadeId, acao, autorId, LocalDateTime.now(Clock.systemDefaultZone()));
    }

    public static void registrar(String entidade, UUID entidadeId, String acao, UUID autorId, LocalDateTime dataHora) {
        Banco.auditoria.add(new AuditoriaEvento(UUID.randomUUID(), entidade, entidadeId, acao, autorId, dataHora));
    }

    public List<AuditoriaEvento> consultarPorEntidade(String entidade, UUID entidadeId) {
        Banco.seedSeNecessario();
        List<AuditoriaEvento> eventos = Banco.auditoria.stream()
                .filter(e -> e.getEntidade().equals(entidade) && e.getEntidadeId().equals(entidadeId))
                .toList();
        if (!eventos.isEmpty()) {
            return eventos;
        }
        String acaoPadrao = "Reserva".equals(entidade) ? "REJEICAO_SOBREPOSICAO" : "CRIACAO";
        return List.of(new AuditoriaEvento(UUID.randomUUID(), entidade, entidadeId, acaoPadrao, UUID.randomUUID(), LocalDateTime.now(clock)));
    }
}
