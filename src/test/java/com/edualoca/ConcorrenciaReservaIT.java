package com.edualoca;

import com.edualoca.api.seguranca.TokenService;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RF-17 / RNF-08 / Driver #2: 20 solicitacoes simultaneas para o mesmo recurso e o mesmo intervalo resultam em
 * exatamente uma reserva aceita (201) e 19 recusadas (409), e numa unica linha no PostgreSQL. O resultado e
 * conferido por consulta SQL, nao so pelas respostas HTTP.
 *
 * <p>Servidor HTTP real (porta aleatoria) e PostgreSQL real via Testcontainers; sem H2.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcorrenciaReservaIT {

    private static final int SOLICITACOES = 20;
    private static final LocalDateTime INICIO = LocalDateTime.of(2027, 3, 15, 8, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2027, 3, 15, 10, 0);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void persistencia(DynamicPropertyRegistry registro) {
        registro.add("edualoca.persistencia.url", POSTGRES::getJdbcUrl);
        registro.add("edualoca.persistencia.usuario", POSTGRES::getUsername);
        registro.add("edualoca.persistencia.senha", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int porta;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeEach
    void limparBanco() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE reserva_historico, reserva_recurso, reserva");
    }

    @Test
    @DisplayName("20 reservas simultaneas da mesma sala no mesmo horario: 1 aceita, 19 recusadas com 409, 1 linha no banco")
    void salaDisputadaPorVinteSolicitacoesSimultaneas() throws Exception {
        Sala sala = new Sala(UUID.randomUUID(), "Sala 101", "aula", 40, "Bloco B", false);
        Banco.salas.put(sala.getId(), sala);

        verificarUmaAceitaEDezenoveRecusadas("SALA", sala.getId(), dispararSimultaneas("SALA", sala.getId()));
    }

    @Test
    @DisplayName("20 reservas simultaneas do mesmo professor no mesmo horario: 1 aceita, 19 recusadas com 409, 1 linha no banco")
    void agendaDoProfessorDisputadaPorVinteSolicitacoesSimultaneas() throws Exception {
        Professor professor = new Professor(UUID.randomUUID(), "Prof. Concorrencia", "CONC-001",
                List.of(new Competencia("Matematica")));
        Banco.professores.put(professor.getId(), professor);
        Banco.professorIdPorIdentificador.put(professor.getIdentificadorInstitucional(), professor.getId());

        verificarUmaAceitaEDezenoveRecusadas("PROFESSOR", professor.getId(), dispararSimultaneas("PROFESSOR", professor.getId()));
    }

    private void verificarUmaAceitaEDezenoveRecusadas(String tipo, UUID recursoId, List<HttpResponse<String>> respostas) {
        List<HttpResponse<String>> aceitas = respostas.stream().filter(r -> r.statusCode() == 201).toList();
        long recusadas409 = respostas.stream().filter(r -> r.statusCode() == 409).count();
        String resumo = respostas.stream()
                .collect(Collectors.groupingBy(HttpResponse::statusCode, Collectors.counting())).toString();

        Integer linhasDoRecursoNoIntervalo = jdbc.queryForObject("""
                        SELECT count(*)
                          FROM reserva r
                          JOIN reserva_recurso rr ON rr.reserva_id = r.id
                         WHERE rr.tipo_recurso = ? AND rr.recurso_id = ?
                           AND r.inicio = ? AND r.fim = ?""",
                Integer.class, tipo, recursoId, Timestamp.valueOf(INICIO), Timestamp.valueOf(FIM));
        Integer reservasGravadas = jdbc.queryForObject("SELECT count(*) FROM reserva", Integer.class);

        assertAll(
                () -> assertEquals(SOLICITACOES, respostas.size(), "todas as solicitacoes devem ter resposta"),
                () -> assertEquals(1, aceitas.size(), "respostas 201 esperadas: 1; obtido por status: " + resumo),
                () -> assertEquals(SOLICITACOES - 1, recusadas409, "respostas 409 esperadas: 19; obtido por status: " + resumo),
                () -> assertEquals(1, linhasDoRecursoNoIntervalo,
                        "linhas no banco para " + tipo + " " + recursoId + " em [" + INICIO + ", " + FIM + ")"),
                () -> assertEquals(1, reservasGravadas, "linhas na tabela reserva"));

        String idAceito = aceitas.getFirst().body().replaceAll(".*\"id\"\\s*:\\s*\"([0-9a-f-]{36})\".*", "$1");
        UUID idNoBanco = jdbc.queryForObject("SELECT id FROM reserva", UUID.class);
        assertEquals(idNoBanco.toString(), idAceito, "a linha gravada deve ser a da resposta 201");
    }

    /** Prepara 20 requisicoes de usuarios distintos e as libera juntas com um CountDownLatch. */
    private List<HttpResponse<String>> dispararSimultaneas(String tipo, UUID recursoId) throws Exception {
        String corpo = """
                {"inicio":"%s","fim":"%s","finalidade":"Disputa concorrente",
                 "recursos":[{"tipo":"%s","id":"%s"}]}""".formatted(INICIO, FIM, tipo, recursoId);

        List<HttpRequest> requisicoes = new ArrayList<>();
        for (int i = 0; i < SOLICITACOES; i++) {
            String token = tokenService.emitir(new Usuario(UUID.randomUUID(), "solicitante" + i + "@edualoca.test", "h", Perfil.SOLICITANTE));
            requisicoes.add(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/reservas"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corpo))
                    .build());
        }

        ExecutorService executor = Executors.newFixedThreadPool(SOLICITACOES);
        CountDownLatch prontas = new CountDownLatch(SOLICITACOES);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<HttpResponse<String>>> futuros = new ArrayList<>();
            for (HttpRequest requisicao : requisicoes) {
                futuros.add(executor.submit(() -> {
                    prontas.countDown();
                    largada.await();
                    return http.send(requisicao, HttpResponse.BodyHandlers.ofString());
                }));
            }
            assertTrue(prontas.await(10, TimeUnit.SECONDS), "as 20 threads devem ficar prontas antes da largada");
            largada.countDown();

            List<HttpResponse<String>> respostas = new ArrayList<>();
            for (Future<HttpResponse<String>> futuro : futuros) {
                respostas.add(futuro.get(60, TimeUnit.SECONDS));
            }
            return respostas;
        } finally {
            executor.shutdownNow();
        }
    }
}
