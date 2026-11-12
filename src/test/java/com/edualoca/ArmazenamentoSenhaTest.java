package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.usuario.AutenticacaoService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.perf.SeedUsuariosPerf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DEF-013 / RNF-05: a senha nunca fica guardada em claro nem como hash rapido sem sal. O teste olha o que fica
 * armazenado em {@code Banco.credenciais}, e nao o que o login devolve.
 */
class ArmazenamentoSenhaTest {

    /** Senhas em claro das contas de seed (as mesmas usadas pelos testes de API). */
    private static final Map<String, String> SEED = Map.of(
            "usuario@example.com", "senhaValida123",
            "novo@example.com", "minhaSenha123",
            "responsavel@example.com", "senhaResponsavel123",
            "responsavel.b@example.com", "senhaResponsavelB123",
            "admin@example.com", "senhaAdmin123");

    @TempDir
    Path pasta;

    private final AutenticacaoService autenticacao = new AutenticacaoService();

    @BeforeEach
    @AfterEach
    void limpar() {
        Banco.resetParaTeste();
    }

    @Test
    @DisplayName("DEF-013: nenhuma credencial de seed fica em texto plano nem como SHA-256 sem sal")
    void credenciaisDeSeed_naoFicamEmClaroNemComHashSemSal() throws Exception {
        Banco.seedSeNecessario();

        for (Map.Entry<String, String> conta : SEED.entrySet()) {
            String armazenado = Banco.credenciais.get(conta.getKey());
            assertThat(armazenado).as("credencial de %s", conta.getKey())
                    .isNotNull()
                    .isNotEqualTo(conta.getValue())
                    .doesNotContain(conta.getValue())
                    .isNotEqualToIgnoringCase(sha256Hex(conta.getValue()));
        }
    }

    @Test
    @DisplayName("DEF-013: duas contas com a mesma senha guardam valores diferentes (hash com sal)")
    void mesmaSenha_geraValoresArmazenadosDiferentes() throws Exception {
        Path csv = pasta.resolve("usuarios.csv");
        Files.writeString(csv, "email,senha\na@edualoca.test,MesmaSenha-1\nb@edualoca.test,MesmaSenha-1\n",
                StandardCharsets.UTF_8);

        new SeedUsuariosPerf(csv).run(null);

        String a = Banco.credenciais.get("a@edualoca.test");
        String b = Banco.credenciais.get("b@edualoca.test");
        assertThat(a).isNotEqualTo("MesmaSenha-1").isNotEqualTo(b);
    }

    @Test
    @DisplayName("DEF-013: com o hash, login certo continua autenticando e senha errada continua 401")
    void loginContinuaFuncionando() {
        assertEquals(Perfil.ADMINISTRADOR, autenticacao.autenticar("admin@example.com", "senhaAdmin123").getPerfil());

        RegraNegocioException errada = assertThrows(RegraNegocioException.class,
                () -> autenticacao.autenticar("admin@example.com", "senhaAdmin124"));
        RegraNegocioException vazia = assertThrows(RegraNegocioException.class,
                () -> autenticacao.autenticar("admin@example.com", ""));
        RegraNegocioException inexistente = assertThrows(RegraNegocioException.class,
                () -> autenticacao.autenticar("ninguem@example.com", "senhaAdmin123"));

        assertEquals(401, errada.getStatusHttp());
        assertEquals(401, vazia.getStatusHttp());
        assertEquals(401, inexistente.getStatusHttp());
        assertEquals(errada.getMessage(), inexistente.getMessage(), "nao revela se o e-mail existe");
    }

    @Test
    @DisplayName("DEF-013: o Usuario autenticado carrega o valor armazenado, nunca a senha em claro")
    void usuarioAutenticado_naoCarregaSenhaEmClaro() {
        var usuario = autenticacao.autenticar("novo@example.com", "minhaSenha123");

        assertThat(usuario.getSenhaHash())
                .isEqualTo(Banco.credenciais.get("novo@example.com"))
                .isNotEqualTo("minhaSenha123");
    }

    private static String sha256Hex(String texto) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
    }
}
