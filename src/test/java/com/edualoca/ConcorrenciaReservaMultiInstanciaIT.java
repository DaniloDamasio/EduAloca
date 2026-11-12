package com.edualoca;

import com.edualoca.api.seguranca.TokenService;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import com.edualoca.suporte.InstanciaExterna;
import org.junit.jupiter.api.AfterAll;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * Mesma disputa de {@link ConcorrenciaReservaIT}, agora com duas instancias da aplicacao (duas JVMs) sobre o
 * mesmo PostgreSQL: 10 solicitacoes vao para cada instancia, todas liberadas juntas. A regra "uma unica aceita"
 * precisa valer no banco (arquitetura.md §11), nao num lock em memoria de um processo.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcorrenciaReservaMultiInstanciaIT {

    private static final int SOLICITACOES = 20;
    /** Id da reserva no corpo 201. Sem ".*" nas pontas: busca linear (Sonar java:S8786). */
    private static final Pattern ID_NA_RESPOSTA = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f-]{36})\"");
    private static final LocalDateTime INICIO = LocalDateTime.of(2027, 3, 15, 8, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2027, 3, 15, 10, 0);
    private static final UUID SALA_ID = UUID.randomUUID();
    private static final UUID PROFESSOR_ID = UUID.randomUUID();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static InstanciaExterna instanciaB;

    @DynamicPropertySource
    static void persistencia(DynamicPropertyRegistry registro) {
        registro.add("edualoca.persistencia.url", POSTGRES::getJdbcUrl);
        registro.add("edualoca.persistencia.usuario", POSTGRES::getUsername);
        registro.add("edualoca.persistencia.senha", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int portaA;
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
    void prepararInstancias() throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        // A instancia B sobe depois do contexto A, quando o esquema ja existe no banco.
        if (instanciaB == null) {
            Path log = Path.of("target", "instancia-externa.log");
            Files.createDirectories(log.getParent());
            instanciaB = InstanciaExterna.iniciar(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(),
                    SALA_ID, PROFESSOR_ID, SOLICITACOES / 2, log);
        }
        jdbc.execute("TRUNCATE reserva_historico, reserva_recurso, reserva");

        Banco.salas.put(SALA_ID, new Sala(SALA_ID, "Sala 101", "aula", 40, "Bloco B", false));
        Professor professor = new Professor(PROFESSOR_ID, "Prof. Concorrencia", "CONC-001", List.of(new Competencia("Matematica")));
        Banco.professores.put(PROFESSOR_ID, professor);
        Banco.professorIdPorIdentificador.put(professor.getIdentificadorInstitucional(), PROFESSOR_ID);
    }

    @AfterAll
    static void encerrarInstanciaB() throws Exception {
        if (instanciaB != null) {
            instanciaB.close();
            instanciaB = null;
        }
    }

    @Test
    @DisplayName("Duas instancias, 20 reservas simultaneas da mesma sala: 1 aceita, 19 recusadas com 409, 1 linha no banco")
    void salaDisputadaEntreDuasInstancias() throws Exception {
        verificarUmaAceitaEDezenoveRecusadas("SALA", SALA_ID, dispararSimultaneas("SALA", SALA_ID));
    }

    @Test
    @DisplayName("Duas instancias, 20 reservas simultaneas do mesmo professor: 1 aceita, 19 recusadas com 409, 1 linha no banco")
    void agendaDoProfessorDisputadaEntreDuasInstancias() throws Exception {
        verificarUmaAceitaEDezenoveRecusadas("PROFESSOR", PROFESSOR_ID, dispararSimultaneas("PROFESSOR", PROFESSOR_ID));
    }

    private void verificarUmaAceitaEDezenoveRecusadas(String tipo, UUID recursoId, List<HttpResponse<String>> respostas) {
        List<HttpResponse<String>> aceitas = respostas.stream().filter(r -> r.statusCode() == 201).toList();
        long recusadas409 = respostas.stream().filter(r -> r.statusCode() == 409).count();
        String resumo = respostas.stream()
                .collect(Collectors.groupingBy(r -> r.uri().getPort() + "->" + r.statusCode(), Collectors.counting())).toString();

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
                () -> assertEquals(1, aceitas.size(), "respostas 201 esperadas: 1; obtido (porta->status): " + resumo),
                () -> assertEquals(SOLICITACOES - 1, recusadas409, "respostas 409 esperadas: 19; obtido (porta->status): " + resumo),
                () -> assertEquals(1, linhasDoRecursoNoIntervalo,
                        "linhas no banco para " + tipo + " " + recursoId + " em [" + INICIO + ", " + FIM + ")"),
                () -> assertEquals(1, reservasGravadas, "linhas na tabela reserva"));

        Matcher id = ID_NA_RESPOSTA.matcher(aceitas.getFirst().body());
        assertTrue(id.find(), "a resposta 201 deve trazer o id da reserva");
        String idAceito = id.group(1);
        UUID idNoBanco = jdbc.queryForObject("SELECT id FROM reserva", UUID.class);
        assertEquals(idNoBanco.toString(), idAceito, "a linha gravada deve ser a da resposta 201");
    }

    /** Alterna as 20 requisicoes entre as instancias A e B e as libera juntas com um CountDownLatch. */
    private List<HttpResponse<String>> dispararSimultaneas(String tipo, UUID recursoId) throws Exception {
        String corpo = """
                {"inicio":"%s","fim":"%s","finalidade":"Disputa entre instancias",
                 "recursos":[{"tipo":"%s","id":"%s"}]}""".formatted(INICIO, FIM, tipo, recursoId);

        List<HttpRequest> requisicoes = new ArrayList<>();
        for (int i = 0; i < SOLICITACOES; i++) {
            boolean paraA = i % 2 == 0;
            String token = paraA
                    ? tokenService.emitir(new Usuario(UUID.randomUUID(), "solicitante" + i + "@edualoca.test", "h", Perfil.SOLICITANTE))
                    : instanciaB.tokens().get(i / 2);
            int porta = paraA ? portaA : instanciaB.porta();
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
