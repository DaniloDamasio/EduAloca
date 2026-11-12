package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaRepositorioEmMemoria;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RF-17 no modo padrao da aplicacao (repositorio em memoria, sem a constraint do PostgreSQL).
 *
 * <p>Lacuna achada na mutacao manual D3 (docs/testes/mutacao-manual.md): sem o lock por recurso do
 * {@link ReservaService}, RC-03-T2/T3 continuavam verdes porque a janela entre "checar conflito" e "gravar" e curta
 * demais para o escalonador abrir. Aqui a janela e aberta de proposito: o repositorio segura cada thread numa barreira
 * dentro de {@code existeConflito}. Com o lock, a segunda thread nem chega a checagem enquanto a primeira nao gravou
 * (a barreira expira e a primeira segue sozinha); sem o lock, as duas passam juntas pela checagem e as duas gravam.
 */
class ConcorrenciaReservaEmMemoriaTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 5, 4, 10, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2026, 5, 4, 12, 0);

    @Test
    @DisplayName("RF-17 (em memoria): duas criacoes que chegam juntas a checagem de conflito resultam em uma unica reserva")
    void criacoesSimultaneasNaChecagem_soUmaEAceita() throws InterruptedException {
        // arrange
        CyclicBarrier ambasNaChecagem = new CyclicBarrier(2);
        ReservaService servico = new ReservaService(Clock.systemDefaultZone(), new ReservaRepositorioEmMemoria() {
            @Override
            public boolean existeConflito(Object recurso, LocalDateTime inicio, LocalDateTime fim, UUID reservaIgnorada) {
                try {
                    ambasNaChecagem.await(1, TimeUnit.SECONDS);
                } catch (BrokenBarrierException | TimeoutException e) {
                    // Esperado com o lock: a outra thread esta bloqueada antes da checagem.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return super.existeConflito(recurso, inicio, fim, reservaIgnorada);
            }
        });
        Sala sala = new Sala(UUID.randomUUID(), "Sala disputada", "aula", 30, "Bloco A", false);
        AtomicInteger aceitas = new AtomicInteger();
        AtomicInteger conflitos = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // act
        for (int i = 0; i < 2; i++) {
            Usuario autor = new Usuario(UUID.randomUUID(), "aluno" + i + "@example.com", "hash", Perfil.SOLICITANTE);
            pool.submit(() -> {
                try {
                    servico.criar(autor, INICIO, FIM, "Disputa", List.of(new ReservaRecurso(sala, 1)));
                    aceitas.incrementAndGet();
                } catch (RegraNegocioException e) {
                    if (e.getStatusHttp() == 409) {
                        conflitos.incrementAndGet();
                    }
                }
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "tentativas nao terminaram");

        // assert
        assertEquals(1, aceitas.get(), "reservas aceitas");
        assertEquals(1, conflitos.get(), "tentativas recusadas com 409");
        long gravadas = Banco.reservas.values().stream()
                .filter(r -> r.getRecursos().stream().anyMatch(rr -> rr.getRecurso() == sala))
                .count();
        assertEquals(1, gravadas, "reservas gravadas para a sala");
    }
}
