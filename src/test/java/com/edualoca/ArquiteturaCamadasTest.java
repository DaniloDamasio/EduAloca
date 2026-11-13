package com.edualoca;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RNF-15: camadas separadas. Le os imports do codigo de producao e verifica quem pode depender de quem:
 * <ul>
 *   <li>{@code domain} (regras e servicos) nao conhece apresentacao, persistencia, integracao nem framework web/JDBC;</li>
 *   <li>{@code api} (apresentacao) fala com o dominio, nunca direto com persistencia ou integracao;</li>
 *   <li>{@code persistencia} e {@code integracao} (adaptadores) implementam portas do dominio e nao conhecem a api.</li>
 * </ul>
 */
class ArquiteturaCamadasTest {

    private static final Path RAIZ = Path.of("src/main/java/com/edualoca");

    static Stream<Arguments> regras() {
        return Stream.of(
                Arguments.of("domain", List.of("com.edualoca.api", "com.edualoca.persistencia", "com.edualoca.integracao",
                        "com.edualoca.perf", "org.springframework", "jakarta.servlet", "java.sql")),
                Arguments.of("api", List.of("com.edualoca.persistencia", "com.edualoca.integracao", "java.sql")),
                Arguments.of("persistencia", List.of("com.edualoca.api", "com.edualoca.integracao")),
                Arguments.of("integracao", List.of("com.edualoca.api", "com.edualoca.persistencia", "java.sql")),
                // Tarefas periodicas so chamam servicos de dominio (P-08 automatico, RF-26).
                Arguments.of("agendamento", List.of("com.edualoca.api", "com.edualoca.persistencia", "com.edualoca.integracao",
                        "java.sql")));
    }

    @ParameterizedTest(name = "{0} nao importa {1}")
    @MethodSource("regras")
    @DisplayName("RNF-15: cada camada so depende das camadas permitidas")
    void camadaSoDependeDoPermitido(String camada, List<String> proibidos) throws IOException {
        Path pasta = RAIZ.resolve(camada);
        assertThat(pasta).as("pacote %s deve existir", camada).isDirectory();

        List<String> violacoes = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(pasta)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                    String l = linha.strip();
                    if (!l.startsWith("import ")) {
                        continue;
                    }
                    String alvo = l.replaceFirst("^import\\s+(static\\s+)?", "").replace(";", "");
                    proibidos.stream().filter(alvo::startsWith)
                            .forEach(p -> violacoes.add(RAIZ.relativize(arquivo) + " -> " + alvo));
                }
            }
        }

        assertThat(violacoes).as("dependencias proibidas a partir de %s", camada).isEmpty();
    }
}
