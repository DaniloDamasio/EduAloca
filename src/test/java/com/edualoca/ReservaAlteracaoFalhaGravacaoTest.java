package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaRepositorioEmMemoria;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RNF-08: se o banco recusar a alteracao de horario (por exemplo, sobreposicao detectada pela constraint de
 * outra instancia, ADR-009), a reserva em memoria nao pode ficar com o horario novo que nao foi gravado.
 */
class ReservaAlteracaoFalhaGravacaoTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2027, 3, 15, 8, 0);
    private static final LocalDateTime FIM = INICIO.plusHours(2);

    private final RegraNegocioException recusaDoBanco = new RegraNegocioException(409, "Recusado pelo banco");

    /** Repositorio que grava a criacao normalmente, mas recusa toda atualizacao, como o banco faria. */
    private final ReservaRepositorioEmMemoria repositorioQueRecusaAtualizacao = new ReservaRepositorioEmMemoria() {
        @Override
        public void atualizar(Reserva reserva) {
            throw recusaDoBanco;
        }
    };

    private final ReservaService servico = new ReservaService(Clock.systemDefaultZone(), repositorioQueRecusaAtualizacao);
    private final Usuario autor = new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE);
    private final Sala sala = new Sala(UUID.randomUUID(), "Sala 301", "aula", 30, "Bloco D", false);

    @BeforeEach
    @AfterEach
    void limparBanco() {
        Banco.resetParaTeste();
    }

    @Test
    @DisplayName("Gravacao recusada na alteracao: propaga o erro e a reserva volta ao horario anterior, sem auditoria")
    void gravacaoRecusada_restauraHorarioAnteriorEPropagaErro() {
        Reserva reserva = servico.criar(autor, INICIO, FIM, "Aula", List.of(new ReservaRecurso(sala, 1)));

        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> servico.alterar(autor, reserva.getId(), INICIO.plusDays(1), FIM.plusDays(1)));

        assertSame(recusaDoBanco, erro, "o erro do repositorio chega sem ser trocado");
        Reserva emMemoria = repositorioQueRecusaAtualizacao.buscar(reserva.getId());
        assertEquals(INICIO, emMemoria.getInicio());
        assertEquals(FIM, emMemoria.getFim());
        assertTrue(Banco.auditoria.stream().noneMatch(e -> e.getEntidadeId().equals(reserva.getId())
                && "ATUALIZACAO".equals(e.getAcao())), "alteracao nao gravada nao gera evento de auditoria");
    }
}
