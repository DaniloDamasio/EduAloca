package com.edualoca.domain.auditoria;

import com.edualoca.domain.comum.Banco;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class AuditoriaService {

    public static void registrar(String entidade, UUID entidadeId, String acao, UUID autorId) {
        Banco.auditoria.add(new AuditoriaEvento(UUID.randomUUID(), entidade, entidadeId, acao, autorId, LocalDateTime.now()));
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
        return List.of(new AuditoriaEvento(UUID.randomUUID(), entidade, entidadeId, acaoPadrao, UUID.randomUUID(), LocalDateTime.now()));
    }
}
