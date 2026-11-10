package com.edualoca.api;

import com.edualoca.api.seguranca.UsuarioAutenticadoResolver;
import com.edualoca.domain.reserva.ReservaService;
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
    public ReservaService reservaService(Clock clock) {
        return new ReservaService(clock);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(usuarioAutenticadoResolver);
    }
}
