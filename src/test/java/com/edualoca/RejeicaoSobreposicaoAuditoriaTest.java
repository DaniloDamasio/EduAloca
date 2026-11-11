package com.edualoca;

import com.edualoca.domain.auditoria.AuditoriaEvento;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Manutencao;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaRepositorioEmMemoria;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RF-28/RF-35: toda tentativa recusada por sobreposicao com reserva ativa grava um evento REJEICAO_SOBREPOSICAO
 * (autor e instante do Clock), que e a base de "conflitos evitados" no relatorio. Recusa por manutencao nao e
 * sobreposicao entre reservas e nao conta.
 */
class RejeicaoSobreposicaoAuditoriaTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2027, 3, 5, 10, 0);
    private static final LocalDateTime INICIO = LocalDateTime.of(2027, 3, 15, 8, 0);
    private static final LocalDateTime FIM = INICIO.plusHours(2);

    private final Clock relogio = Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO);
    private final ReservaService servico = new ReservaService(relogio);
    private final Usuario autor = new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE);
    private final Sala sala = new Sala(UUID.randomUUID(), "Sala 301", "aula", 30, "Bloco D", false);

    @Test
    @DisplayName("Criacao recusada por sobreposicao grava um unico REJEICAO_SOBREPOSICAO com autor e instante do Clock")
    void criacaoRecusadaPorSobreposicao_gravaRejeicao() {
        Sala outra = new Sala(UUID.randomUUID(), "Sala 302", "aula", 30, "Bloco D", false);
        servico.criar(autor, INICIO, FIM, "Aula", List.of(new ReservaRecurso(sala, 1), new ReservaRecurso(outra, 1)));

        // A tentativa conflita nos dois recursos, mas e uma tentativa so.
        assertThrows(RegraNegocioException.class, () -> servico.criar(autor, INICIO.plusMinutes(30), FIM, "Aula",
                List.of(new ReservaRecurso(sala, 1), new ReservaRecurso(outra, 1))));

        List<AuditoriaEvento> rejeicoes = rejeicoes();
        assertEquals(1, rejeicoes.size());
        assertEquals(autor.getId(), rejeicoes.get(0).getAutorId());
        assertEquals(AGORA, rejeicoes.get(0).getDataHora());
        assertEquals(0, Banco.reservas.values().stream().filter(r -> r.getId().equals(rejeicoes.get(0).getEntidadeId())).count(),
                "a tentativa recusada nao vira reserva");
    }

    @Test
    @DisplayName("Sobreposicao detectada so na gravacao (constraint do banco) tambem grava REJEICAO_SOBREPOSICAO")
    void recusaDaConstraintNoBanco_gravaRejeicao() {
        RegraNegocioException recusaDoBanco = new RegraNegocioException(409, "Recurso indisponivel: sobreposicao com reserva ativa");
        ReservaService comBancoQueRecusa = new ReservaService(relogio, new ReservaRepositorioEmMemoria() {
            @Override
            public void inserir(Reserva reserva) {
                throw recusaDoBanco;
            }
        });

        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> comBancoQueRecusa.criar(autor, INICIO, FIM, "Aula", List.of(new ReservaRecurso(sala, 1))));

        assertEquals(409, erro.getStatusHttp());
        assertEquals(1, rejeicoes().size());
    }

    @Test
    @DisplayName("Alteracao de horario recusada por sobreposicao grava REJEICAO_SOBREPOSICAO da reserva alterada")
    void alteracaoRecusadaPorSobreposicao_gravaRejeicao() {
        servico.criar(autor, INICIO, FIM, "Aula", List.of(new ReservaRecurso(sala, 1)));
        Reserva depois = servico.criar(autor, FIM, FIM.plusHours(2), "Aula", List.of(new ReservaRecurso(sala, 1)));

        assertThrows(RegraNegocioException.class, () -> servico.alterar(autor, depois.getId(), INICIO, FIM));

        List<AuditoriaEvento> rejeicoes = rejeicoes();
        assertEquals(1, rejeicoes.size());
        assertEquals(depois.getId(), rejeicoes.get(0).getEntidadeId());
    }

    @Test
    @DisplayName("Recusa por manutencao nao e conflito entre reservas e nao grava REJEICAO_SOBREPOSICAO")
    void recusaPorManutencao_naoGravaRejeicao() {
        Banco.seedSeNecessario();
        Manutencao manutencao = new Manutencao(UUID.randomUUID(), sala, INICIO, FIM, "pintura");
        Banco.manutencoes.put(manutencao.getId(), manutencao);

        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> servico.criar(autor, INICIO, FIM, "Aula", List.of(new ReservaRecurso(sala, 1))));

        assertEquals(409, erro.getStatusHttp());
        assertEquals(0, rejeicoes().size());
    }

    @Test
    @DisplayName("Reserva aceita e intervalo invalido nao gravam REJEICAO_SOBREPOSICAO")
    void semSobreposicao_naoGravaRejeicao() {
        servico.criar(autor, INICIO, FIM, "Aula", List.of(new ReservaRecurso(sala, 1)));
        servico.criar(autor, FIM, FIM.plusHours(1), "Aula encostada", List.of(new ReservaRecurso(sala, 1)));
        assertThrows(RegraNegocioException.class,
                () -> servico.criar(autor, FIM, INICIO, "Aula", List.of(new ReservaRecurso(sala, 1))));

        assertEquals(0, rejeicoes().size());
    }

    private static List<AuditoriaEvento> rejeicoes() {
        return Banco.auditoria.stream()
                .filter(e -> "Reserva".equals(e.getEntidade()) && "REJEICAO_SOBREPOSICAO".equals(e.getAcao()))
                .toList();
    }
}
