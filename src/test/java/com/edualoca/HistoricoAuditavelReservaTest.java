package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.HistoricoReservaRepositorio;
import com.edualoca.domain.reserva.HistoricoReservaRepositorioEmMemoria;
import com.edualoca.domain.reserva.RegistroHistoricoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaRepositorioEmMemoria;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.reserva.Transacao;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import com.edualoca.suporte.RelogioAjustavel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Historico auditavel das reservas (RF-27, RF-29, RF-30): toda mudanca de estado gera um registro com reserva,
 * usuario, instante (do Clock), estado anterior, estado novo e motivo; o registro nao pode ser alterado nem apagado;
 * reserva iniciada nao pode ser apagada; so Responsavel e Administrador consultam o historico.
 */
class HistoricoAuditavelReservaTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Instant T0 = Instant.parse("2027-03-08T13:00:00Z");
    private static final LocalDateTime INICIO = LocalDateTime.of(2027, 3, 15, 8, 0);
    private static final LocalDateTime FIM = INICIO.plusHours(2);

    private final RelogioAjustavel relogio = new RelogioAjustavel(T0, FUSO);
    private final ReservaRepositorioEmMemoria reservas = new ReservaRepositorioEmMemoria();
    private final HistoricoReservaRepositorioEmMemoria historico = new HistoricoReservaRepositorioEmMemoria();
    private final ReservaService servico = new ReservaService(relogio, reservas, historico, Transacao.direta());

    private final Usuario solicitante = usuario(Perfil.SOLICITANTE);
    private final Usuario responsavel = usuario(Perfil.RESPONSAVEL);
    private final Usuario administrador = usuario(Perfil.ADMINISTRADOR);
    private final Sala salaComum = new Sala(UUID.randomUUID(), "Sala 101", "aula", 30, "Bloco A", false);
    private final Sala salaRestrita = new Sala(UUID.randomUUID(), "Lab Quimica", "laboratorio", 20, "Bloco B", true);

    @Nested
    @DisplayName("Toda mudanca de estado gera registro")
    class MudancaDeEstado {

        @Test
        @DisplayName("Criacao de reserva sem recurso restrito registra null -> APROVADA com solicitante e instante do Clock")
        void criacaoAprovadaAutomaticamente() {
            Reserva reserva = criar(salaComum);

            RegistroHistoricoReserva registro = unico(historico.listarPorReserva(reserva.getId()));
            assertRegistro(registro, reserva, solicitante, T0, null, EstadoReserva.APROVADA);
        }

        @Test
        @DisplayName("Criacao com recurso restrito registra null -> SOLICITADA")
        void criacaoComRecursoRestrito() {
            Reserva reserva = criar(salaRestrita);

            RegistroHistoricoReserva registro = unico(historico.listarPorReserva(reserva.getId()));
            assertRegistro(registro, reserva, solicitante, T0, null, EstadoReserva.SOLICITADA);
        }

        @Test
        @DisplayName("Aprovacao registra SOLICITADA -> APROVADA com o responsavel e o instante da aprovacao")
        void aprovacao() {
            Reserva reserva = criar(salaRestrita);
            relogio.avancar(Duration.ofHours(3));

            servico.aprovar(responsavel, reserva.getId());

            RegistroHistoricoReserva registro = ultimo(reserva);
            assertRegistro(registro, reserva, responsavel, T0.plus(Duration.ofHours(3)),
                    EstadoReserva.SOLICITADA, EstadoReserva.APROVADA);
        }

        @Test
        @DisplayName("Rejeicao registra SOLICITADA -> REJEITADA com a justificativa como motivo")
        void rejeicao() {
            Reserva reserva = criar(salaRestrita);
            relogio.avancar(Duration.ofMinutes(45));

            servico.rejeitar(administrador, reserva.getId(), "Laboratorio reservado para banca");

            RegistroHistoricoReserva registro = ultimo(reserva);
            assertRegistro(registro, reserva, administrador, T0.plus(Duration.ofMinutes(45)),
                    EstadoReserva.SOLICITADA, EstadoReserva.REJEITADA);
            assertEquals("Laboratorio reservado para banca", registro.motivo());
        }

        @Test
        @DisplayName("Cancelamento registra APROVADA -> CANCELADA com o motivo informado")
        void cancelamento() {
            Reserva reserva = criar(salaComum);
            relogio.avancar(Duration.ofDays(1));

            servico.cancelar(solicitante, reserva.getId(), "Aula transferida para EAD");

            RegistroHistoricoReserva registro = ultimo(reserva);
            assertRegistro(registro, reserva, solicitante, T0.plus(Duration.ofDays(1)),
                    EstadoReserva.APROVADA, EstadoReserva.CANCELADA);
            assertEquals("Aula transferida para EAD", registro.motivo());
        }

        @Test
        @DisplayName("Ciclo criar -> aprovar -> cancelar deixa 3 registros encadeados na ordem em que ocorreram")
        void historicoCompletoEmOrdem() {
            Reserva reserva = criar(salaRestrita);
            relogio.avancar(Duration.ofHours(1));
            servico.aprovar(responsavel, reserva.getId());
            relogio.avancar(Duration.ofHours(1));
            servico.cancelar(solicitante, reserva.getId(), "Evento adiado");

            List<RegistroHistoricoReserva> registros = historico.listarPorReserva(reserva.getId());

            assertEquals(List.of(EstadoReserva.SOLICITADA, EstadoReserva.APROVADA, EstadoReserva.CANCELADA),
                    registros.stream().map(RegistroHistoricoReserva::estadoNovo).toList());
            assertEquals(java.util.Arrays.asList(null, EstadoReserva.SOLICITADA, EstadoReserva.APROVADA),
                    registros.stream().map(RegistroHistoricoReserva::estadoAnterior).toList());
            assertEquals(List.of(T0, T0.plus(Duration.ofHours(1)), T0.plus(Duration.ofHours(2))),
                    registros.stream().map(RegistroHistoricoReserva::instante).toList());
        }

        @Test
        @DisplayName("Alterar o horario nao muda o estado e nao gera registro de historico")
        void alteracaoDeHorarioNaoEMudancaDeEstado() {
            Reserva reserva = criar(salaComum);

            servico.alterar(solicitante, reserva.getId(), INICIO.plusDays(1), FIM.plusDays(1));

            assertEquals(1, historico.listarPorReserva(reserva.getId()).size());
        }

        @Test
        @DisplayName("Transicao recusada (aprovar reserva ja aprovada) nao gera registro")
        void transicaoRecusadaNaoGeraRegistro() {
            Reserva reserva = criar(salaComum);

            assertThrows(RegraNegocioException.class, () -> servico.aprovar(responsavel, reserva.getId()));

            assertEquals(1, historico.listarPorReserva(reserva.getId()).size());
        }

        @Test
        @DisplayName("Falha ao gravar o historico desfaz a mudanca de estado e a justificativa em memoria e propaga o erro")
        void falhaAoGravarHistoricoDesfazMudanca() {
            HistoricoReservaRepositorio quebrado = new HistoricoReservaRepositorio() {
                @Override
                public void registrar(RegistroHistoricoReserva registro) {
                    if (registro.estadoAnterior() != null) {
                        throw new IllegalStateException("falha simulada ao gravar historico");
                    }
                }

                @Override
                public List<RegistroHistoricoReserva> listarPorReserva(UUID reservaId) {
                    return List.of();
                }
            };
            ReservaService servicoComFalha = new ReservaService(relogio, reservas, quebrado, Transacao.direta());
            Reserva reserva = servicoComFalha.criar(solicitante, INICIO, FIM, "Aula", List.of(new ReservaRecurso(salaRestrita, 1)));

            assertThrows(IllegalStateException.class,
                    () -> servicoComFalha.rejeitar(responsavel, reserva.getId(), "Sem disponibilidade"));

            assertEquals(EstadoReserva.SOLICITADA, reserva.getEstado());
            assertNull(reserva.getJustificativaRejeicao());
        }
    }

    @Nested
    @DisplayName("Registro de historico e imutavel")
    class Imutabilidade {

        @Test
        @DisplayName("A lista consultada nao aceita inclusao, remocao nem limpeza, e o historico guardado fica intacto")
        void listaConsultadaNaoPodeSerModificada() {
            Reserva reserva = criar(salaRestrita);
            servico.aprovar(responsavel, reserva.getId());
            List<RegistroHistoricoReserva> consultado = servico.consultarHistorico(responsavel, reserva.getId());
            RegistroHistoricoReserva forjado = new RegistroHistoricoReserva(UUID.randomUUID(), reserva.getId(),
                    responsavel.getId(), T0, EstadoReserva.APROVADA, EstadoReserva.CANCELADA, "forjado");

            assertAll(
                    () -> assertThrows(UnsupportedOperationException.class, () -> consultado.add(forjado)),
                    () -> assertThrows(UnsupportedOperationException.class, () -> consultado.remove(0)),
                    () -> assertThrows(UnsupportedOperationException.class, () -> consultado.set(0, forjado)),
                    () -> assertThrows(UnsupportedOperationException.class, consultado::clear));
            assertEquals(2, historico.listarPorReserva(reserva.getId()).size());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.edualoca.HistoricoAuditavelReservaTest#registrosInvalidos")
        @DisplayName("Registro incompleto ou sem transicao real e recusado na construcao")
        void registroInvalidoERecusado(String caso, Runnable construcao) {
            assertThrows(IllegalArgumentException.class, construcao::run, caso);
        }
    }

    @Nested
    @DisplayName("Reserva iniciada nao pode ser apagada")
    class Exclusao {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = EstadoReserva.class, names = "SOLICITADA", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("DELETE de reserva fora de SOLICITADA e recusado com 403 e reserva e historico continuam la")
        void reservaIniciadaNaoEApagada(EstadoReserva estado) {
            Reserva reserva = criar(salaComum);
            // EM_USO, CONCLUIDA e NAO_COMPARECEU nao tem gatilho definido (P-07/P-08): estado atribuido direto.
            reserva.setEstado(estado);
            List<RegistroHistoricoReserva> antes = historico.listarPorReserva(reserva.getId());

            RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                    () -> servico.excluir(administrador, reserva.getId()));

            assertEquals(403, erro.getStatusHttp());
            assertSame(reserva, reservas.buscar(reserva.getId()));
            assertFalse(antes.isEmpty(), "a reserva criada deveria ter historico");
            assertEquals(antes, historico.listarPorReserva(reserva.getId()));
        }
    }

    @Nested
    @DisplayName("Consulta do historico so para Responsavel e Administrador")
    class Consulta {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = Perfil.class, names = {"RESPONSAVEL", "ADMINISTRADOR"})
        @DisplayName("Responsavel e Administrador consultam o historico completo")
        void perfisAutorizados(Perfil perfil) {
            Reserva reserva = criar(salaRestrita);
            servico.aprovar(responsavel, reserva.getId());

            List<RegistroHistoricoReserva> registros = servico.consultarHistorico(usuario(perfil), reserva.getId());

            assertEquals(2, registros.size());
        }

        @Test
        @DisplayName("Solicitante e recusado com 403, mesmo sendo o dono da reserva")
        void solicitanteDonoRecebe403() {
            Reserva reserva = criar(salaComum);

            RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                    () -> servico.consultarHistorico(solicitante, reserva.getId()));

            assertEquals(403, erro.getStatusHttp());
        }

        @Test
        @DisplayName("Solicitante recebe 403 tambem para reserva inexistente (nao revela quais ids existem)")
        void solicitanteReservaInexistenteRecebe403() {
            RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                    () -> servico.consultarHistorico(solicitante, UUID.randomUUID()));

            assertEquals(403, erro.getStatusHttp());
        }

        @Test
        @DisplayName("Responsavel consultando reserva inexistente recebe 409, como nas demais operacoes")
        void reservaInexistente() {
            RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                    () -> servico.consultarHistorico(responsavel, UUID.randomUUID()));

            assertEquals(409, erro.getStatusHttp());
        }
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> registrosInvalidos() {
        UUID id = UUID.randomUUID();
        UUID reserva = UUID.randomUUID();
        UUID usuario = UUID.randomUUID();
        EstadoReserva s = EstadoReserva.SOLICITADA;
        EstadoReserva a = EstadoReserva.APROVADA;
        return Stream.of(
                caso("sem id", () -> new RegistroHistoricoReserva(null, reserva, usuario, T0, s, a, "ok")),
                caso("sem reserva", () -> new RegistroHistoricoReserva(id, null, usuario, T0, s, a, "ok")),
                caso("sem usuario", () -> new RegistroHistoricoReserva(id, reserva, null, T0, s, a, "ok")),
                caso("sem instante", () -> new RegistroHistoricoReserva(id, reserva, usuario, null, s, a, "ok")),
                caso("sem estado novo", () -> new RegistroHistoricoReserva(id, reserva, usuario, T0, s, null, "ok")),
                caso("estado nao muda", () -> new RegistroHistoricoReserva(id, reserva, usuario, T0, a, a, "ok")),
                caso("motivo nulo", () -> new RegistroHistoricoReserva(id, reserva, usuario, T0, s, a, null)),
                caso("motivo em branco", () -> new RegistroHistoricoReserva(id, reserva, usuario, T0, s, a, "   ")));
    }

    private static org.junit.jupiter.params.provider.Arguments caso(String nome, Runnable construcao) {
        return org.junit.jupiter.params.provider.Arguments.of(nome, construcao);
    }

    private Reserva criar(Sala sala) {
        Banco.seedSeNecessario();
        return servico.criar(solicitante, INICIO, FIM, "Aula de quimica", List.of(new ReservaRecurso(sala, 1)));
    }

    private RegistroHistoricoReserva ultimo(Reserva reserva) {
        List<RegistroHistoricoReserva> registros = historico.listarPorReserva(reserva.getId());
        assertFalse(registros.isEmpty(), "nenhum registro de historico para a reserva");
        return registros.get(registros.size() - 1);
    }

    private static RegistroHistoricoReserva unico(List<RegistroHistoricoReserva> registros) {
        assertEquals(1, registros.size(), "esperava exatamente um registro de historico");
        return registros.get(0);
    }

    private static void assertRegistro(RegistroHistoricoReserva registro, Reserva reserva, Usuario autor, Instant instante,
                                       EstadoReserva anterior, EstadoReserva novo) {
        assertAll(
                () -> assertNotNull(registro.id()),
                () -> assertEquals(reserva.getId(), registro.reservaId()),
                () -> assertEquals(autor.getId(), registro.usuarioId()),
                () -> assertEquals(instante, registro.instante()),
                () -> assertEquals(anterior, registro.estadoAnterior()),
                () -> assertEquals(novo, registro.estadoNovo()),
                () -> assertFalse(registro.motivo() == null || registro.motivo().isBlank(), "motivo obrigatorio"));
    }

    /** O Responsavel e o competente pelo Bloco B, onde fica o laboratorio restrito (P-2, DEF-010). */
    private static Usuario usuario(Perfil perfil) {
        UUID id = perfil == Perfil.RESPONSAVEL ? Banco.RESPONSAVEL_BLOCO_B_ID : UUID.randomUUID();
        return new Usuario(id, perfil.name().toLowerCase() + "@edualoca.test", "hash", perfil);
    }
}
