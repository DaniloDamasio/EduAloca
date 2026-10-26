package com.edualoca.domain.auditoria;

import java.time.LocalDateTime;
import java.util.UUID;

public class AuditoriaEvento {

    private final UUID id;
    private final String entidade;
    private final UUID entidadeId;
    private final String acao;
    private final UUID autorId;
    private final LocalDateTime dataHora;

    public AuditoriaEvento(UUID id, String entidade, UUID entidadeId, String acao, UUID autorId, LocalDateTime dataHora) {
        this.id = id;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.acao = acao;
        this.autorId = autorId;
        this.dataHora = dataHora;
    }

    public UUID getId() {
        return id;
    }

    public String getEntidade() {
        return entidade;
    }

    public UUID getEntidadeId() {
        return entidadeId;
    }

    public String getAcao() {
        return acao;
    }

    public UUID getAutorId() {
        return autorId;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }
}
