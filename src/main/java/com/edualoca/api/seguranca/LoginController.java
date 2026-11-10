package com.edualoca.api.seguranca;

import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.usuario.AutenticacaoService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login (RF-01): troca e-mail e senha por um token Bearer opaco. Credencial invalida responde 401 com
 * mensagem unica, sem revelar se o e-mail existe.
 */
@RestController
public class LoginController {

    private final AutenticacaoService autenticacaoService;
    private final TokenService tokenService;

    public LoginController(AutenticacaoService autenticacaoService, TokenService tokenService) {
        this.autenticacaoService = autenticacaoService;
        this.tokenService = tokenService;
    }

    @PostMapping("/auth/login")
    public LoginResposta login(@RequestBody LoginRequisicao requisicao) {
        if (requisicao == null || vazio(requisicao.email()) || vazio(requisicao.senha())) {
            throw new RegraNegocioException(400, "Informe e-mail e senha");
        }
        return new LoginResposta(tokenService.emitir(autenticacaoService.autenticar(requisicao.email(), requisicao.senha())));
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    public record LoginRequisicao(String email, String senha) {
    }

    public record LoginResposta(String token) {
    }
}
