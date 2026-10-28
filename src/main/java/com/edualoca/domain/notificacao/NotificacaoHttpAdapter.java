package com.edualoca.domain.notificacao;

import com.edualoca.domain.reserva.Reserva;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adapter HTTP real para {@link NotificacaoService.NotificadorExterno}. 5xx recebe uma
 * tentativa extra (erro transitorio); 4xx, timeout e demais falhas de comunicacao nao
 * tem retry (erro permanente ou sem garantia de idempotencia). Nunca propaga excecao,
 * corpo de resposta do provedor ou a api-key para fora de {@link #enviar(Reserva)}.
 */
public class NotificacaoHttpAdapter implements NotificacaoService.NotificadorExterno {

    private static final Logger LOG = Logger.getLogger(NotificacaoHttpAdapter.class.getName());
    private static final int MAX_TENTATIVAS_ERRO_TRANSITORIO = 2;

    private final String apiKey;
    private final RestClient restClient;

    public NotificacaoHttpAdapter(String baseUrl, String apiKey, Duration timeoutConexao, Duration timeoutLeitura) {
        this.apiKey = apiKey;
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeoutConexao)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeoutLeitura);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean enviar(Reserva reserva) {
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS_ERRO_TRANSITORIO; tentativa++) {
            try {
                restClient.post()
                        .uri("/notificacoes")
                        .header("Authorization", "Bearer " + apiKey)
                        .body(corpoDaNotificacao(reserva))
                        .retrieve()
                        .toBodilessEntity();
                return true;
            } catch (RestClientResponseException ex) {
                HttpStatusCode status = ex.getStatusCode();
                if (status.is4xxClientError()) {
                    registrarFalha("provedor de notificacao recusou a requisicao (erro permanente)");
                    return false;
                }
                boolean ultimaTentativa = tentativa == MAX_TENTATIVAS_ERRO_TRANSITORIO;
                registrarFalha("provedor de notificacao respondeu com erro transitorio"
                        + (ultimaTentativa ? ", limite de tentativas atingido" : ", nova tentativa"));
                if (ultimaTentativa) {
                    return false;
                }
            } catch (RestClientException ex) {
                registrarFalha("falha de comunicacao com o provedor de notificacao (" + ex.getClass().getSimpleName() + ")");
                return false;
            }
        }
        return false;
    }

    private Map<String, Object> corpoDaNotificacao(Reserva reserva) {
        return Map.of(
                "reservaId", reserva.getId().toString(),
                "estado", reserva.getEstado().name(),
                "destinatarioId", reserva.getSolicitanteId().toString()
        );
    }

    private void registrarFalha(String mensagem) {
        UUID correlacaoId = UUID.randomUUID();
        LOG.log(Level.WARNING, "[correlacaoId={0}] {1}", new Object[] {correlacaoId, mensagem});
    }
}
