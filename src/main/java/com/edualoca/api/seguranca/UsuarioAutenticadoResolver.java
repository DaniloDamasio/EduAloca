package com.edualoca.api.seguranca;

import com.edualoca.domain.usuario.Usuario;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class UsuarioAutenticadoResolver implements HandlerMethodArgumentResolver {

    private final TokenService tokenService;

    public UsuarioAutenticadoResolver(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(UsuarioAutenticado.class)
                && Usuario.class.equals(parameter.getParameterType());
    }

    @Override
    public Usuario resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return tokenService.autenticar(webRequest.getHeader(HttpHeaders.AUTHORIZATION));
    }
}
