package com.edualoca.domain.auditoria;

import com.edualoca.domain.comum.Banco;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class AuditoriaService {

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

    /**
     * Eventos registrados para a entidade, na ordem de registro. Entidade sem eventos devolve lista vazia: a ausencia de
     * registro e informacao de auditoria e nao pode ser mascarada por um evento fabricado (DEF-011).
     */
    public List<AuditoriaEvento> consultarPorEntidade(String entidade, UUID entidadeId) {
        Banco.seedSeNecessario();
        return Banco.auditoria.stream()
                .filter(e -> e.getEntidade().equals(entidade) && e.getEntidadeId().equals(entidadeId))
                .toList();
    }
}
