package com.edualoca.agendamento;

import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.retirada.RetiradaMaterialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tarefas periodicas do dominio:
 * <ul>
 *   <li>P-08: reserva {@code APROVADA} que chegou ao fim do horario sem inicio do uso vira {@code NAO_COMPARECEU};</li>
 *   <li>RF-26: material retirado e nao devolvido depois do fim da reserva e sinalizado como pendente.</li>
 * </ul>
 * Roda a cada 30 s (decisao D-6 de {@code DOCS/arquitetura.md}): com {@code fixedDelay}, o atraso maximo entre o
 * vencimento e a sinalizacao fica em 30 s mais a duracao de uma execucao, dentro do "ate 1 min" do RF-26.
 * Ligado por padrao ({@code edualoca.agendamento.habilitado}); desligado na suite de testes
 * ({@code src/test/resources/config/application.properties}), em que os testes chamam os metodos diretamente.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "edualoca.agendamento.habilitado", havingValue = "true", matchIfMissing = true)
public class AgendamentoConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AgendamentoConfig.class);

    private final ReservaService reservas;
    private final RetiradaMaterialService retiradas;
    private final Clock clock;

    public AgendamentoConfig(ReservaService reservas, RetiradaMaterialService retiradas, Clock clock) {
        this.reservas = reservas;
        this.retiradas = retiradas;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${edualoca.agendamento.intervalo-ms:30000}",
            fixedDelayString = "${edualoca.agendamento.intervalo-ms:30000}")
    public void executar() {
        List<Reserva> encerradas = reservas.registrarNaoComparecimentosVencidos();
        if (!encerradas.isEmpty()) {
            LOG.info("Nao comparecimento automatico: {} reserva(s) encerrada(s) no fim do horario", encerradas.size());
        }
        retiradas.sinalizarPendencias(LocalDateTime.now(clock));
    }
}
