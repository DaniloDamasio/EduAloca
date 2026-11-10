package com.edualoca.suporte;

import com.edualoca.EduAlocaApplication;
import com.edualoca.api.seguranca.TokenService;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Segunda instancia da aplicacao, em outra JVM, apontando para o mesmo PostgreSQL. Simula a implantacao com
 * mais de um processo: locks em memoria de uma JVM nao protegem a outra. Os recursos (em memoria nesta fase)
 * e os tokens sao criados no proprio processo; os tokens sao publicados na saida padrao para o teste.
 */
public final class InstanciaExterna implements AutoCloseable {

    static final String MARCADOR_PRONTA = "EDUALOCA-INSTANCIA-PRONTA ";

    private final Process processo;
    private final int porta;
    private final List<String> tokens;

    private InstanciaExterna(Process processo, int porta, List<String> tokens) {
        this.processo = processo;
        this.porta = porta;
        this.tokens = tokens;
    }

    /** Sobe a instancia e espera ela publicar os tokens; a saida do processo vai para {@code log}. */
    public static InstanciaExterna iniciar(String jdbcUrl, String usuario, String senha, UUID salaId, UUID professorId,
                                           int quantidadeTokens, Path log) throws IOException, InterruptedException {
        int porta = portaLivre();
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder construtor = new ProcessBuilder(java,
                "-cp", System.getProperty("java.class.path"),
                "-Dinstancia.sala=" + salaId,
                "-Dinstancia.professor=" + professorId,
                "-Dinstancia.tokens=" + quantidadeTokens,
                InstanciaExterna.class.getName(),
                "--server.port=" + porta,
                "--spring.main.banner-mode=off",
                "--edualoca.persistencia.url=" + jdbcUrl)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        Map<String, String> ambiente = construtor.environment();
        ambiente.put("EDUALOCA_PERSISTENCIA_USUARIO", usuario);
        ambiente.put("EDUALOCA_PERSISTENCIA_SENHA", senha);
        Process processo = construtor.start();

        Instant limite = Instant.now().plus(Duration.ofSeconds(90));
        while (Instant.now().isBefore(limite)) {
            if (!processo.isAlive()) {
                throw new IllegalStateException("Instancia externa terminou (codigo " + processo.exitValue() + "); ver " + log);
            }
            for (String linha : Files.readAllLines(log, StandardCharsets.UTF_8)) {
                if (linha.startsWith(MARCADOR_PRONTA)) {
                    return new InstanciaExterna(processo, porta,
                            List.of(linha.substring(MARCADOR_PRONTA.length()).trim().split(",")));
                }
            }
            Thread.sleep(200);
        }
        processo.destroyForcibly();
        throw new IllegalStateException("Instancia externa nao ficou pronta em 90 s; ver " + log);
    }

    public int porta() {
        return porta;
    }

    public List<String> tokens() {
        return tokens;
    }

    @Override
    public void close() throws InterruptedException {
        processo.destroy();
        if (!processo.waitFor(15, TimeUnit.SECONDS)) {
            processo.destroyForcibly();
        }
    }

    private static int portaLivre() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** Ponto de entrada do processo filho. */
    public static void main(String[] args) {
        ConfigurableApplicationContext contexto = SpringApplication.run(EduAlocaApplication.class, args);

        Banco.seedSeNecessario();
        Sala sala = new Sala(UUID.fromString(System.getProperty("instancia.sala")), "Sala 101", "aula", 40, "Bloco B", false);
        Banco.salas.put(sala.getId(), sala);
        Professor professor = new Professor(UUID.fromString(System.getProperty("instancia.professor")),
                "Prof. Concorrencia", "CONC-001", List.of(new Competencia("Matematica")));
        Banco.professores.put(professor.getId(), professor);
        Banco.professorIdPorIdentificador.put(professor.getIdentificadorInstitucional(), professor.getId());

        TokenService tokenService = contexto.getBean(TokenService.class);
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < Integer.parseInt(System.getProperty("instancia.tokens")); i++) {
            tokens.add(tokenService.emitir(new Usuario(UUID.randomUUID(), "externo" + i + "@edualoca.test", "h", Perfil.SOLICITANTE)));
        }
        System.out.println(MARCADOR_PRONTA + String.join(",", tokens));
        System.out.flush();
        // O Tomcat mantem o processo vivo ate o teste encerra-lo. Credenciais chegam por variavel de ambiente.
    }
}
