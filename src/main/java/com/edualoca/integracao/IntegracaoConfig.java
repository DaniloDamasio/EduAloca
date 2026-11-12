package com.edualoca.integracao;

import com.edualoca.domain.notificacao.NotificacaoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Monta a porta de notificacao do dominio (RNF-16). Fica na camada de integracao, como
 * {@code PersistenciaPostgresConfig} fica na de persistencia: a apresentacao so ve {@link NotificacaoService}.
 */
@Configuration
public class IntegracaoConfig {

    /**
     * RF-11 do enunciado ("notificacao simulada ou integracao com API externa"). Padrao: simulada, registrada em
     * {@code Banco.notificacoes}. Com {@code edualoca.notificacao.externa.habilitada=true}, usa o adaptador HTTP
     * (RNF-16); a chave vem de {@code EDUALOCA_NOTIFICACAO_API_KEY}, nunca do arquivo versionado.
     */
    @Bean
    public NotificacaoService notificacaoService(
            @Value("${edualoca.notificacao.externa.habilitada:false}") boolean externaHabilitada,
            @Value("${edualoca.notificacao.externa.url}") String url,
            @Value("${edualoca.notificacao.externa.api-key:}") String apiKey,
            @Value("${edualoca.notificacao.externa.timeout-conexao-ms}") long timeoutConexaoMs,
            @Value("${edualoca.notificacao.externa.timeout-leitura-ms}") long timeoutLeituraMs) {
        if (!externaHabilitada) {
            return new NotificacaoService();
        }
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("edualoca.notificacao.externa.url e obrigatoria com a integracao habilitada");
        }
        return new NotificacaoService(new NotificacaoHttpAdapter(url, apiKey,
                Duration.ofMillis(timeoutConexaoMs), Duration.ofMillis(timeoutLeituraMs)));
    }
}
