package com.edualoca.perf;

import com.edualoca.domain.comum.Banco;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Cadastra os usuarios ficticios do teste de carga (perf/usuarios.csv, colunas email,senha).
 * Ativo somente no perfil Spring {@code perf}: nunca carrega contas de teste em outros ambientes.
 */
@Component
@Profile("perf")
public class SeedUsuariosPerf implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SeedUsuariosPerf.class);

    private final Path arquivo;

    public SeedUsuariosPerf(@Value("${edualoca.perf.usuarios-csv:perf/usuarios.csv}") Path arquivo) {
        this.arquivo = arquivo;
    }

    @Override
    public void run(ApplicationArguments args) {
        Banco.seedSeNecessario();
        List<String> linhas;
        try {
            linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Nao foi possivel ler os usuarios de carga em " + arquivo.toAbsolutePath(), e);
        }
        int cadastrados = 0;
        for (String linha : linhas.subList(Math.min(1, linhas.size()), linhas.size())) {
            String[] campos = linha.split(",", 2);
            if (campos.length == 2 && !campos[0].isBlank() && !campos[1].isBlank()) {
                Banco.credenciais.put(campos[0].trim(), campos[1].trim());
                cadastrados++;
            }
        }
        LOG.info("Perfil perf: {} usuarios ficticios de carga cadastrados a partir de {}", cadastrados, arquivo);
    }
}
