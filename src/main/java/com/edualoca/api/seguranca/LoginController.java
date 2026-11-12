package com.edualoca.api.seguranca;

import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.usuario.AutenticacaoService;
import com.edualoca.domain.usuario.Usuario;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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

    /** Revoga o token do cabecalho; responde 204 mesmo sem token valido, para nao revelar quais tokens existem. */
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        tokenService.revogar(authorization);
    }

    /** Quem e o usuario do token: a interface usa o perfil para montar as telas (a autorizacao continua no servidor). */
    @GetMapping("/auth/eu")
    public EuResposta eu(@UsuarioAutenticado Usuario usuario) {
        return new EuResposta(usuario.getId(), usuario.getEmail(), usuario.getPerfil().name());
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    public record LoginRequisicao(String email, String senha) {
    }

    public record LoginResposta(String token) {
    }

    public record EuResposta(UUID id, String email, String perfil) {
    }
}
