package com.edualoca.api;

import com.edualoca.api.seguranca.UsuarioAutenticadoResolver;
import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.recurso.PesquisaRecursoService;
import com.edualoca.domain.relatorio.RelatorioService;
import com.edualoca.domain.reserva.HistoricoReservaRepositorio;
import com.edualoca.domain.reserva.HistoricoReservaRepositorioEmMemoria;
import com.edualoca.domain.reserva.ReservaRepositorio;
import com.edualoca.domain.reserva.ReservaRepositorioEmMemoria;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.reserva.Transacao;
import com.edualoca.domain.retirada.RetiradaMaterialService;
import com.edualoca.domain.usuario.AutenticacaoService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.util.List;

/** Liga os servicos de dominio (POJOs com Clock injetado) a camada HTTP. */
@Configuration
public class ApiConfig implements WebMvcConfigurer {

    private final UsuarioAutenticadoResolver usuarioAutenticadoResolver;

    public ApiConfig(UsuarioAutenticadoResolver usuarioAutenticadoResolver) {
        this.usuarioAutenticadoResolver = usuarioAutenticadoResolver;
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public ReservaService reservaService(Clock clock, ObjectProvider<ReservaRepositorio> repositorio,
                                         ObjectProvider<HistoricoReservaRepositorio> historico,
                                         ObjectProvider<Transacao> transacao) {
        // PostgreSQL quando configurado (PersistenciaPostgresConfig); caso contrario, memoria.
        return new ReservaService(clock, repositorio.getIfAvailable(ReservaRepositorioEmMemoria::new),
                historico.getIfAvailable(HistoricoReservaRepositorioEmMemoria::new),
                transacao.getIfAvailable(Transacao::direta));
    }

    @Bean
    public CadastroRecursoService cadastroRecursoService(Clock clock) {
        return new CadastroRecursoService(clock);
    }

    @Bean
    public AutenticacaoService autenticacaoService() {
        return new AutenticacaoService();
    }

    @Bean
    public PesquisaRecursoService pesquisaRecursoService() {
        return new PesquisaRecursoService();
    }

    @Bean
    public RelatorioService relatorioService(Clock clock) {
        return new RelatorioService(clock.getZone());
    }

    @Bean
    public RetiradaMaterialService retiradaMaterialService() {
        return new RetiradaMaterialService();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(usuarioAutenticadoResolver);
    }
}
