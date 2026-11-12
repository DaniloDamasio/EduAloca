package com.edualoca.perf;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.usuario.AutenticacaoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Leitura do CSV de usuarios ficticios de carga, sem subir o contexto Spring. */
class SeedUsuariosPerfTest {

    @TempDir
    Path pasta;

    @BeforeEach
    @AfterEach
    void limparBanco() {
        Banco.resetParaTeste();
    }

    @Test
    @DisplayName("Cadastra so as linhas com e-mail e senha; ignora cabecalho, linha vazia, sem virgula e campo em branco")
    void linhasInvalidas_saoIgnoradas() throws IOException {
        Path csv = pasta.resolve("usuarios.csv");
        Files.writeString(csv, String.join("\n",
                "email,senha",
                " valido@edualoca.test , Senha-1 ",
                "",
                "sem-virgula@edualoca.test",
                " ,senha-sem-email",
                "sem-senha@edualoca.test, "), StandardCharsets.UTF_8);

        new SeedUsuariosPerf(csv).run(null);

        assertEquals("valido@edualoca.test",
                new AutenticacaoService().autenticar("valido@edualoca.test", "Senha-1").getEmail(),
                "e-mail e senha sem espacos nas pontas");
        assertFalse(Banco.credenciais.containsKey("sem-virgula@edualoca.test"));
        assertFalse(Banco.credenciais.containsKey("sem-senha@edualoca.test"));
        assertFalse(Banco.credenciais.containsValue("senha-sem-email"));
    }

    @Test
    @DisplayName("Arquivo vazio nao cadastra ninguem nem falha")
    void arquivoVazio_naoCadastra() throws IOException {
        Path csv = Files.createFile(pasta.resolve("vazio.csv"));
        Banco.seedSeNecessario();
        int antes = Banco.credenciais.size();

        new SeedUsuariosPerf(csv).run(null);

        assertEquals(antes, Banco.credenciais.size());
    }

    @Test
    @DisplayName("Arquivo inexistente falha na subida com o caminho na mensagem, em vez de subir sem usuarios")
    void arquivoInexistente_falhaComCaminho() {
        Path ausente = pasta.resolve("nao-existe.csv");

        UncheckedIOException erro = assertThrows(UncheckedIOException.class, () -> new SeedUsuariosPerf(ausente).run(null));

        assertTrue(erro.getMessage().contains(ausente.toAbsolutePath().toString()));
    }
}
