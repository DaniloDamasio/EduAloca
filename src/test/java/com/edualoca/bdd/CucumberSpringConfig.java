package com.edualoca.bdd;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Sobe o contexto Spring da aplicacao uma vez para todos os cenarios. A API e exercitada por MockMvc
 * (sem servidor HTTP real) e o relogio e fixo, para que o "instante" da auditoria seja verificavel.
 */
@CucumberContextConfiguration
@SpringBootTest
@Import(CucumberSpringConfig.RelogioFixo.class)
public class CucumberSpringConfig {

    /** 09/03/2026 11:30 em Sao Paulo, o mesmo instante usado em BddAprovacaoBloqueioTest. */
    static final Clock RELOGIO_FIXO = Clock.fixed(Instant.parse("2026-03-09T14:30:00Z"), ZoneId.of("America/Sao_Paulo"));

    @TestConfiguration
    static class RelogioFixo {
        @Bean
        @Primary
        Clock relogioFixo() {
            return RELOGIO_FIXO;
        }
    }
}
