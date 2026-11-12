package com.edualoca.api.seguranca;

import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.usuario.Usuario;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emite e resolve tokens opacos de acesso (Bearer). O token e aleatorio (CSPRNG, 256 bits) e nao carrega
 * dados do usuario; o perfil vem sempre do estado no servidor, nunca do cliente.
 */
@Component
public class TokenService {

    private static final String PREFIXO = "Bearer ";
    private static final int BYTES_DO_TOKEN = 32;

    private final SecureRandom aleatorio = new SecureRandom();
    private final Map<String, Usuario> usuarioPorToken = new ConcurrentHashMap<>();

    public String emitir(Usuario usuario) {
        if (usuario == null) {
            throw new IllegalArgumentException("usuário obrigatório");
        }
        byte[] bytes = new byte[BYTES_DO_TOKEN];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        usuarioPorToken.put(token, usuario);
        return token;
    }

    /** Resolve o cabecalho Authorization; qualquer ausencia ou token desconhecido resulta em 401. */
    public Usuario autenticar(String cabecalhoAuthorization) {
        if (cabecalhoAuthorization == null || !cabecalhoAuthorization.startsWith(PREFIXO)) {
            throw naoAutenticado();
        }
        Usuario usuario = usuarioPorToken.get(cabecalhoAuthorization.substring(PREFIXO.length()).trim());
        if (usuario == null) {
            throw naoAutenticado();
        }
        return usuario;
    }

    /** Logout: o token deixa de valer imediatamente. Token ausente ou desconhecido e ignorado. */
    public void revogar(String cabecalhoAuthorization) {
        if (cabecalhoAuthorization != null && cabecalhoAuthorization.startsWith(PREFIXO)) {
            usuarioPorToken.remove(cabecalhoAuthorization.substring(PREFIXO.length()).trim());
        }
    }

    /** Revoga todos os tokens do usuario (ex.: conta inativada pelo Administrador). */
    public void revogarDoUsuario(java.util.UUID usuarioId) {
        usuarioPorToken.values().removeIf(usuario -> usuarioId.equals(usuario.getId()));
    }

    private static RegraNegocioException naoAutenticado() {
        return new RegraNegocioException(401, "Autenticação necessária: informe um token de acesso válido");
    }
}
