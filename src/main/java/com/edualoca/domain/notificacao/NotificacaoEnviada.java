package com.edualoca.domain.notificacao;

import java.util.UUID;

public class NotificacaoEnviada {

    private final UUID id;
    private final UUID destinatarioId;
    private final String mensagem;
    private final StatusNotificacao status;

    public NotificacaoEnviada(UUID id, UUID destinatarioId, String mensagem, StatusNotificacao status) {
        this.id = id;
        this.destinatarioId = destinatarioId;
        this.mensagem = mensagem;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDestinatarioId() {
        return destinatarioId;
    }

    public String getMensagem() {
        return mensagem;
    }

    public StatusNotificacao getStatus() {
        return status;
    }
}
