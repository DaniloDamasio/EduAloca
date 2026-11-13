package com.edualoca.domain.usuario;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Login por e-mail e senha (RF-01, RNF-05). Contra forca bruta: depois de {@link #MAX_FALHAS} senhas erradas para o
 * mesmo e-mail dentro de {@link #JANELA}, o login desse e-mail fica bloqueado por {@link #BLOQUEIO} (429), inclusive com
 * a senha certa. A regra vale igual para e-mail inexistente, para que a resposta nao revele quais contas existem.
 *
 * <p>O relogio padrao e o do sistema: a protecao depende do tempo real, nao do {@code Clock} de negocio.
 */
public class AutenticacaoService {

    public static final int MAX_FALHAS = 5;
    public static final Duration JANELA = Duration.ofMinutes(15);
    public static final Duration BLOQUEIO = Duration.ofMinutes(15);

    private final Clock relogio;

    public AutenticacaoService() {
        this(Clock.systemUTC());
    }

    public AutenticacaoService(Clock relogio) {
        this.relogio = relogio;
    }

    public Usuario autenticar(String email, String senha) {
        Banco.seedSeNecessario();
        String chave = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        Instant agora = relogio.instant();
        TentativasLogin tentativas = Banco.tentativasLogin.get(chave);
        if (tentativas != null && tentativas.bloqueadoAte() != null && agora.isBefore(tentativas.bloqueadoAte())) {
            long minutos = Math.max(1, Duration.between(agora, tentativas.bloqueadoAte()).toMinutes());
            throw new RegraNegocioException(429,
                    "Muitas tentativas de login: aguarde " + minutos + " minuto(s) e tente novamente");
        }
        String armazenado = email == null ? null : Banco.credenciais.get(email);
        // confere() gasta o mesmo custo quando a conta nao existe: a resposta e o tempo nao revelam o e-mail.
        if (!SenhaHasher.confere(senha, armazenado)) {
            registrarFalha(chave, agora);
            throw new RegraNegocioException(401, "Credenciais inválidas");
        }
        Banco.tentativasLogin.remove(chave);
        UUID id = Banco.usuarioIdPorEmail.computeIfAbsent(email, e -> UUID.randomUUID());
        Perfil perfil = Banco.perfilPorEmail.getOrDefault(email, Perfil.SOLICITANTE);
        return new Usuario(id, email, armazenado, perfil);
    }

    private void registrarFalha(String chave, Instant agora) {
        Banco.tentativasLogin.compute(chave, (k, atual) -> {
            boolean janelaNova = atual == null
                    || !agora.isBefore(atual.inicioDaJanela().plus(JANELA))
                    || (atual.bloqueadoAte() != null && !agora.isBefore(atual.bloqueadoAte()));
            int falhas = janelaNova ? 1 : atual.falhas() + 1;
            Instant inicio = janelaNova ? agora : atual.inicioDaJanela();
            return new TentativasLogin(falhas, inicio, falhas >= MAX_FALHAS ? agora.plus(BLOQUEIO) : null);
        });
    }
}
