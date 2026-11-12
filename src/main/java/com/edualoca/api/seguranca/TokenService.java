package com.edualoca.api.seguranca;

import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.usuario.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emite e resolve tokens opacos de acesso (Bearer). O token e aleatorio (CSPRNG, 256 bits) e nao carrega
 * dados do usuario; o perfil vem sempre do estado no servidor, nunca do cliente.
 *
 * <p>A sessao expira por inatividade e por duracao maxima (padrao 30 min e 8 h). O relogio e o do sistema, de
 * proposito independente do {@code Clock} de negocio que alguns testes avancam dias para simular o calendario.
 */
@Component
public class TokenService {

    private static final String PREFIXO = "Bearer ";
    private static final int BYTES_DO_TOKEN = 32;

    private record Sessao(Usuario usuario, Instant emitidoEm, Instant ultimoUso) {
    }

    private final SecureRandom aleatorio = new SecureRandom();
    private final Map<String, Sessao> sessaoPorToken = new ConcurrentHashMap<>();
    private final Clock relogio;
    private final Duration inatividadeMaxima;
    private final Duration duracaoMaxima;

    @Autowired
    public TokenService(@Value("${edualoca.seguranca.token.inatividade-minutos:30}") long inatividadeMinutos,
                        @Value("${edualoca.seguranca.token.duracao-maxima-horas:8}") long duracaoMaximaHoras) {
        this(Clock.systemUTC(), Duration.ofMinutes(inatividadeMinutos), Duration.ofHours(duracaoMaximaHoras));
    }

    /** Para teste: relogio controlado e limites explicitos. */
    public TokenService(Clock relogio, Duration inatividadeMaxima, Duration duracaoMaxima) {
        if (inatividadeMaxima.isNegative() || inatividadeMaxima.isZero() || duracaoMaxima.isNegative() || duracaoMaxima.isZero()) {
            throw new IllegalArgumentException("limites de sessao devem ser positivos");
        }
        this.relogio = relogio;
        this.inatividadeMaxima = inatividadeMaxima;
        this.duracaoMaxima = duracaoMaxima;
    }

    public String emitir(Usuario usuario) {
        if (usuario == null) {
            throw new IllegalArgumentException("usuário obrigatório");
        }
        byte[] bytes = new byte[BYTES_DO_TOKEN];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant agora = relogio.instant();
        sessaoPorToken.put(token, new Sessao(usuario, agora, agora));
        return token;
    }

    /**
     * Resolve o cabecalho Authorization; ausencia, token desconhecido ou sessao vencida resultam em 401. Cada uso valido
     * renova o prazo de inatividade, mas nunca alem da duracao maxima contada da emissao.
     */
    public Usuario autenticar(String cabecalhoAuthorization) {
        if (cabecalhoAuthorization == null || !cabecalhoAuthorization.startsWith(PREFIXO)) {
            throw naoAutenticado();
        }
        String token = cabecalhoAuthorization.substring(PREFIXO.length()).trim();
        Sessao sessao = sessaoPorToken.get(token);
        if (sessao == null) {
            throw naoAutenticado();
        }
        Instant agora = relogio.instant();
        boolean inativa = !agora.isBefore(sessao.ultimoUso().plus(inatividadeMaxima));
        boolean vencida = !agora.isBefore(sessao.emitidoEm().plus(duracaoMaxima));
        if (inativa || vencida) {
            sessaoPorToken.remove(token, sessao);
            throw new RegraNegocioException(401, "Sessão expirada: entre novamente");
        }
        sessaoPorToken.replace(token, sessao, new Sessao(sessao.usuario(), sessao.emitidoEm(), agora));
        return sessao.usuario();
    }

    /** Logout: o token deixa de valer imediatamente. Token ausente ou desconhecido e ignorado. */
    public void revogar(String cabecalhoAuthorization) {
        if (cabecalhoAuthorization != null && cabecalhoAuthorization.startsWith(PREFIXO)) {
            sessaoPorToken.remove(cabecalhoAuthorization.substring(PREFIXO.length()).trim());
        }
    }

    /** Revoga todos os tokens do usuario (ex.: conta inativada pelo Administrador). */
    public void revogarDoUsuario(UUID usuarioId) {
        sessaoPorToken.values().removeIf(sessao -> usuarioId.equals(sessao.usuario().getId()));
    }

    private static RegraNegocioException naoAutenticado() {
        return new RegraNegocioException(401, "Autenticação necessária: informe um token de acesso válido");
    }
}
