package com.edualoca.domain.notificacao;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.Reserva;

import java.util.UUID;

public class NotificacaoService {

    public interface NotificadorExterno {
        boolean enviar(Reserva reserva);
    }

    private final NotificadorExterno notificadorExterno;

    public NotificacaoService() {
        this(reserva -> true);
    }

    public NotificacaoService(NotificadorExterno notificadorExterno) {
        this.notificadorExterno = notificadorExterno;
    }

    public NotificacaoEnviada notificarTransicaoDeEstado(Reserva reserva) {
        StatusNotificacao status = notificadorExterno.enviar(reserva) ? StatusNotificacao.ENVIADA : StatusNotificacao.FALHA;
        NotificacaoEnviada notificacao = new NotificacaoEnviada(UUID.randomUUID(), reserva.getSolicitanteId(),
                "Sua reserva mudou para " + reserva.getEstado(), status);
        Banco.notificacoes.put(notificacao.getId(), notificacao);
        return notificacao;
    }

    public NotificacaoEnviada notificarResponsavelCompetente(Reserva reserva) {
        Banco.seedSeNecessario();
        UUID responsavelId = encontrarResponsavelCompetente(reserva);
        StatusNotificacao status = responsavelId != null ? StatusNotificacao.ENVIADA : StatusNotificacao.FALHA;
        UUID destinatario = responsavelId != null ? responsavelId : UUID.randomUUID();
        NotificacaoEnviada notificacao = new NotificacaoEnviada(UUID.randomUUID(), destinatario,
                "Nova solicitacao restrita pendente de aprovacao", status);
        Banco.notificacoes.put(notificacao.getId(), notificacao);
        return notificacao;
    }

    private UUID encontrarResponsavelCompetente(Reserva reserva) {
        return reserva.getRecursos().stream()
                .map(rr -> rr.getRecurso())
                .filter(Sala.class::isInstance)
                .map(Sala.class::cast)
                .map(sala -> Banco.responsavelPorLocalizacao.get(sala.getLocalizacao()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
