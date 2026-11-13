package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.PesquisaRecursoService;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.retirada.CondicaoItem;
import com.edualoca.domain.retirada.RegistroRetirada;
import com.edualoca.domain.retirada.RetiradaMaterialService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes escritos a partir dos mutantes que sobreviveram ao PIT (DOCS/testes/mutacao-pit.md). Cada caso diz, no nome,
 * qual comportamento passou a ser conferido; antes, a mutacao correspondente passava pela suite inteira.
 */
class LacunasMutacaoPitTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2027, 11, 3, 8, 0);
    private static final LocalDateTime FIM = INICIO.plusHours(2);

    private final ReservaService reservas = new ReservaService();
    private final CadastroRecursoService cadastro = new CadastroRecursoService();
    private final Usuario solicitante = new Usuario(UUID.randomUUID(), "sol@example.com", "hash", Perfil.SOLICITANTE);
    private final Usuario responsavelA = new Usuario(Banco.RESPONSAVEL_BLOCO_A_ID, "responsavel@example.com", "hash", Perfil.RESPONSAVEL);
    private final Usuario administrador = new Usuario(Banco.ADMINISTRADOR_ID, "admin@example.com", "hash", Perfil.ADMINISTRADOR);

    @BeforeEach
    void semear() {
        Banco.seedSeNecessario();
    }

    private Sala sala(String nome, String tipo, String bloco, boolean restrita) {
        return cadastro.cadastrarSala(nome + " " + UUID.randomUUID(), tipo, 20, bloco, restrita);
    }

    private Reserva reservar(Object recurso, LocalDateTime inicio, LocalDateTime fim) {
        return reservas.criar(solicitante, inicio, fim, "Aula", List.of(new ReservaRecurso(recurso, 1)));
    }

    private static int status(Runnable acao) {
        try {
            acao.run();
            return 200;
        } catch (RegraNegocioException e) {
            return e.getStatusHttp();
        }
    }

    @Nested
    @DisplayName("Reserva")
    class DeReserva {

        @Test
        @DisplayName("Alterar grava ATUALIZACAO com o autor e libera o lock: outra thread reserva o horário antigo")
        void alterarAuditaELiberaLock() throws Exception {
            Sala sala = sala("Sala", "aula", "Bloco A", false);
            Reserva reserva = reservar(sala, INICIO, FIM);

            reservas.alterar(solicitante, reserva.getId(), INICIO.plusHours(4), FIM.plusHours(4));

            assertThat(Banco.auditoria).anyMatch(e -> reserva.getId().equals(e.getEntidadeId())
                    && "ATUALIZACAO".equals(e.getAcao()) && solicitante.getId().equals(e.getAutorId()));
            ExecutorService outra = Executors.newSingleThreadExecutor();
            try {
                Reserva noHorarioAntigo = outra.submit(() -> reservar(sala, INICIO, FIM)).get(5, TimeUnit.SECONDS);
                assertEquals(EstadoReserva.APROVADA, noHorarioAntigo.getEstado());
            } finally {
                outra.shutdownNow();
            }
        }

        @Test
        @DisplayName("Alterar para um horário que se sobrepõe ao próprio horário antigo é aceito (a reserva não conflita consigo)")
        void alterarSobreOProprioHorario() {
            Sala sala = sala("Sala", "aula", "Bloco A", false);
            Reserva reserva = reservar(sala, INICIO, FIM);

            reservas.alterar(solicitante, reserva.getId(), INICIO.plusHours(1), FIM.plusHours(1));

            assertAll(
                    () -> assertEquals(INICIO.plusHours(1), reserva.getInicio()),
                    () -> assertEquals(EstadoReserva.APROVADA, reserva.getEstado()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = EstadoReserva.class, names = {"CONCLUIDA", "CANCELADA", "REJEITADA", "NAO_COMPARECEU"})
        @DisplayName("Cancelar reserva em estado terminal responde 409 e não muda o estado")
        void cancelarTerminal(EstadoReserva terminal) {
            Reserva reserva = reservar(sala("Sala", "aula", "Bloco A", false), INICIO, FIM);
            reserva.setEstado(terminal);

            assertAll(
                    () -> assertEquals(409, status(() -> reservas.cancelar(solicitante, reserva.getId(), "motivo"))),
                    () -> assertEquals(terminal, reserva.getEstado()));
        }

        @Test
        @DisplayName("Rejeitar: Administrador rejeita; Solicitante recebe 403; reserva já APROVADA recebe 409; sem justificativa, 400")
        void rejeitarPorPerfilEEstado() {
            Reserva pendente = reservar(sala("Lab", "laboratorio", "Bloco A", true), INICIO, FIM);
            Reserva aprovada = reservar(sala("Sala", "aula", "Bloco A", false), INICIO, FIM);

            assertAll(
                    () -> assertEquals(403, status(() -> reservas.rejeitar(solicitante, pendente.getId(), "não"))),
                    () -> assertEquals(400, status(() -> reservas.rejeitar(administrador, pendente.getId(), " "))),
                    () -> assertEquals(409, status(() -> reservas.rejeitar(administrador, aprovada.getId(), "não"))),
                    () -> assertEquals(EstadoReserva.SOLICITADA, pendente.getEstado()));
            reservas.rejeitar(administrador, pendente.getId(), "laboratório em reforma");
            assertAll(
                    () -> assertEquals(EstadoReserva.REJEITADA, pendente.getEstado()),
                    () -> assertEquals("laboratório em reforma", pendente.getJustificativaRejeicao()));
        }

        @Test
        @DisplayName("Cancelar por manutenção: só Administrador, só APROVADA, com motivo; devolve a reserva cancelada")
        void cancelarPorManutencao() {
            Reserva aprovada = reservar(sala("Sala", "aula", "Bloco A", false), INICIO, FIM);
            Reserva emUso = reservar(sala("Sala", "aula", "Bloco A", false), INICIO, FIM);
            reservas.iniciarUso(administrador, emUso.getId());

            assertAll(
                    () -> assertEquals(403, status(() -> reservas.cancelarPorManutencao(responsavelA, aprovada.getId(), "vazamento"))),
                    () -> assertEquals(400, status(() -> reservas.cancelarPorManutencao(administrador, aprovada.getId(), " "))),
                    () -> assertEquals(409, status(() -> reservas.cancelarPorManutencao(administrador, emUso.getId(), "vazamento"))),
                    () -> assertEquals(409, status(() -> reservas.cancelarPorManutencao(administrador, UUID.randomUUID(), "vazamento"))));
            Reserva cancelada = reservas.cancelarPorManutencao(administrador, aprovada.getId(), "vazamento");
            assertAll(
                    () -> assertEquals(aprovada.getId(), cancelada.getId()),
                    () -> assertEquals(EstadoReserva.CANCELADA, cancelada.getEstado()),
                    () -> assertEquals(EstadoReserva.EM_USO, emUso.getEstado()));
        }
    }

    @Nested
    @DisplayName("Busca")
    class DeBusca {

        private final PesquisaRecursoService busca = new PesquisaRecursoService();

        private List<UUID> salas(String tipo, String bloco) {
            return busca.buscarSalas(tipo, 0, bloco, INICIO, FIM).stream().map(Sala::getId).toList();
        }

        @Test
        @DisplayName("Filtros de tipo e de localização excluem as outras salas (sem diferença de maiúsculas)")
        void filtrosDeTipoELocalizacao() {
            Sala labA = sala("Lab", "laboratorio", "Bloco A", false);
            Sala aulaA = sala("Aula", "aula", "Bloco A", false);
            Sala labB = sala("Lab", "laboratorio", "Bloco B", false);

            assertAll(
                    () -> assertThat(salas("LABORATORIO", null)).contains(labA.getId(), labB.getId()).doesNotContain(aulaA.getId()),
                    () -> assertThat(salas(null, "bloco b")).contains(labB.getId()).doesNotContain(labA.getId(), aulaA.getId()),
                    () -> assertThat(salas("laboratorio", "Bloco A")).contains(labA.getId()).doesNotContain(labB.getId(), aulaA.getId()));
        }

        @Test
        @DisplayName("Capacidade mínima exclui sala menor e mantém sala igual ou maior (borda inclusiva)")
        void filtroDeCapacidade() {
            Sala vinte = sala("Vinte", "aula", "Bloco A", false);
            Sala trinta = cadastro.cadastrarSala("Trinta " + UUID.randomUUID(), "aula", 30, "Bloco A", false);
            List<UUID> para21 = busca.buscarSalas(null, 21, null, INICIO, FIM).stream().map(Sala::getId).toList();
            List<UUID> para20 = busca.buscarSalas(null, 20, null, INICIO, FIM).stream().map(Sala::getId).toList();

            assertAll(
                    () -> assertThat(para21).contains(trinta.getId()).doesNotContain(vinte.getId()),
                    () -> assertThat(para20).contains(trinta.getId(), vinte.getId()));
        }

        @Test
        @DisplayName("Reserva cancelada libera a sala na busca; reserva de outra sala não a esconde")
        void canceladaLiberaEOutraSalaNaoEsconde() {
            Sala ocupada = sala("Ocupada", "aula", "Bloco A", false);
            Sala vizinha = sala("Vizinha", "aula", "Bloco A", false);
            Reserva reserva = reservar(ocupada, INICIO, FIM);

            assertThat(salas(null, null)).doesNotContain(ocupada.getId()).contains(vizinha.getId());
            reservas.cancelar(solicitante, reserva.getId(), "remarcada");
            assertThat(salas(null, null)).contains(ocupada.getId(), vizinha.getId());
        }

        @Test
        @DisplayName("Intervalo inválido na busca de professor e de material responde 400")
        void intervaloInvalido() {
            cadastro.cadastrarProfessor("Prof. X", "PX-" + UUID.randomUUID(), List.of(new Competencia("Logica")));

            assertAll(
                    () -> assertEquals(400, status(() -> busca.buscarProfessores("Logica", FIM, INICIO))),
                    () -> assertEquals(400, status(() -> busca.buscarMateriais(null, 1, FIM, INICIO))),
                    () -> assertEquals(400, status(() -> busca.buscarMateriais(null, 1, INICIO, null))));
        }
    }

    @Nested
    @DisplayName("Retirada")
    class DeRetirada {

        private final RetiradaMaterialService retiradas = new RetiradaMaterialService();

        private Reserva emUsoCom(Material material) {
            Reserva reserva = reservas.criar(solicitante, INICIO, FIM, "Oficina", List.of(
                    new ReservaRecurso(sala("Sala", "aula", "Bloco A", false), 1), new ReservaRecurso(material, 1)));
            reservas.iniciarUso(administrador, reserva.getId());
            return reserva;
        }

        @Test
        @DisplayName("Retiradas listadas são só as da reserva pedida")
        void listarSoDaReserva() {
            Material kitA = cadastro.cadastrarMaterial("Kit A", "kit", 1, false);
            Material kitB = cadastro.cadastrarMaterial("Kit B", "kit", 1, false);
            Reserva primeira = emUsoCom(kitA);
            Reserva segunda = emUsoCom(kitB);
            RegistroRetirada daPrimeira = retiradas.registrarRetirada(responsavelA, primeira.getId(), kitA.getId(), INICIO);
            retiradas.registrarRetirada(responsavelA, segunda.getId(), kitB.getId(), INICIO);

            assertThat(retiradas.listarPorReserva(primeira.getId())).extracting(RegistroRetirada::getId)
                    .containsExactly(daPrimeira.getId());
        }

        @Test
        @DisplayName("Pendência: só depois do fim, nunca para item devolvido, e reserva removida é ignorada sem erro")
        void sinalizacaoDePendencias() {
            Material devolvido = cadastro.cadastrarMaterial("Projetor", "projetor", 1, false);
            Material atrasado = cadastro.cadastrarMaterial("Câmera", "camera", 1, false);
            Material orfao = cadastro.cadastrarMaterial("Tripé", "tripe", 1, false);
            Reserva reservaDevolvido = emUsoCom(devolvido);
            Reserva reservaAtrasado = emUsoCom(atrasado);
            Reserva reservaOrfa = emUsoCom(orfao);
            RegistroRetirada r1 = retiradas.registrarRetirada(responsavelA, reservaDevolvido.getId(), devolvido.getId(), INICIO);
            RegistroRetirada r2 = retiradas.registrarRetirada(responsavelA, reservaAtrasado.getId(), atrasado.getId(), INICIO);
            RegistroRetirada r3 = retiradas.registrarRetirada(responsavelA, reservaOrfa.getId(), orfao.getId(), INICIO);
            retiradas.registrarDevolucao(responsavelA, r1.getId(), FIM.minusMinutes(5), CondicaoItem.INTEGRO);
            Banco.reservas.remove(reservaOrfa.getId());

            retiradas.sinalizarPendencias(FIM);
            assertThat(retiradas.listarPendentes()).as("no instante do fim ainda não atrasou").isEmpty();

            retiradas.sinalizarPendencias(FIM.plusMinutes(1));
            assertAll(
                    () -> assertThat(retiradas.listarPendentes()).extracting(RegistroRetirada::getId).containsExactly(r2.getId()),
                    () -> assertThat(r1.isPendenciaSinalizada()).as("devolvido nunca fica pendente").isFalse(),
                    () -> assertThat(r3.isPendenciaSinalizada()).as("reserva removida é ignorada").isFalse());
        }
    }

    @Nested
    @DisplayName("Usuarios (bordas e perfil no dominio)")
    class DeUsuario {

        private final com.edualoca.domain.usuario.UsuarioService usuarios =
                new com.edualoca.domain.usuario.UsuarioService(java.time.Clock.systemDefaultZone());

        private int cadastrar(String email, String senha, Perfil perfil, String local) {
            return status(() -> usuarios.cadastrar(administrador, email, senha, perfil, local));
        }

        @Test
        @DisplayName("O dominio exige Administrador mesmo sem o controller (listar, cadastrar e inativar)")
        void dominioExigeAdministrador() {
            assertAll(
                    () -> assertEquals(403, status(() -> usuarios.listar(responsavelA))),
                    () -> assertEquals(403, status(() -> usuarios.cadastrar(solicitante, "x@example.com", "senhaForte123", Perfil.SOLICITANTE, null))),
                    () -> assertEquals(403, status(() -> usuarios.inativar(responsavelA, Banco.RESPONSAVEL_BLOCO_B_ID))));
        }

        @Test
        @DisplayName("Senha de 8 a 120 caracteres: 7 e 121 sao recusadas, 8 e 120 aceitas")
        void bordasDaSenha() {
            assertAll(
                    () -> assertEquals(400, cadastrar("s7@example.com", "a".repeat(7), Perfil.SOLICITANTE, null)),
                    () -> assertEquals(200, cadastrar("s8@example.com", "a".repeat(8), Perfil.SOLICITANTE, null)),
                    () -> assertEquals(200, cadastrar("s120@example.com", "a".repeat(120), Perfil.SOLICITANTE, null)),
                    () -> assertEquals(400, cadastrar("s121@example.com", "a".repeat(121), Perfil.SOLICITANTE, null)));
        }

        @Test
        @DisplayName("E-mail com ate 120 caracteres e aceito; com 121, recusado; local com mais de 120, recusado")
        void bordasDoEmailEDoLocal() {
            String dominio = "@example.com";
            assertAll(
                    () -> assertEquals(200, cadastrar("a".repeat(120 - dominio.length()) + dominio, "senhaForte123", Perfil.SOLICITANTE, null)),
                    () -> assertEquals(400, cadastrar("b".repeat(121 - dominio.length()) + dominio, "senhaForte123", Perfil.SOLICITANTE, null)),
                    () -> assertEquals(400, cadastrar("r@example.com", "senhaForte123", Perfil.RESPONSAVEL, "L".repeat(121))),
                    () -> assertEquals(200, cadastrar("r2@example.com", "senhaForte123", Perfil.RESPONSAVEL, "Bloco " + "L".repeat(114))));
        }

        @Test
        @DisplayName("Local vago aceita Responsavel; local ocupado responde 409; perfil sem local nao exige local")
        void localDoResponsavel() {
            assertAll(
                    () -> assertEquals(409, cadastrar("ra@example.com", "senhaForte123", Perfil.RESPONSAVEL, "Bloco A")),
                    () -> assertEquals(200, cadastrar("rz@example.com", "senhaForte123", Perfil.RESPONSAVEL, "Bloco Z")),
                    () -> assertEquals(Banco.usuarioIdPorEmail.get("rz@example.com"), Banco.responsavelPorLocalizacao.get("Bloco Z")),
                    () -> assertEquals(200, cadastrar("adm2@example.com", "senhaForte123", Perfil.ADMINISTRADOR, "ignorado")),
                    () -> assertThat(Banco.responsavelPorLocalizacao).doesNotContainKey("ignorado"));
        }
    }

    @Nested
    @DisplayName("Manutencao (colisao do RF-23)")
    class DeManutencao {

        @Test
        @DisplayName("So colide reserva APROVADA ou EM_USO da mesma sala no periodo: outra sala, SOLICITADA e cancelada nao bloqueiam")
        void soColideReservaAtivaDaMesmaSala() {
            Sala alvo = sala("Alvo", "laboratorio", "Bloco A", true);
            Sala vizinha = sala("Vizinha", "aula", "Bloco A", false);
            reservar(vizinha, INICIO, FIM);
            Reserva solicitada = reservar(alvo, INICIO, FIM);
            assertEquals(EstadoReserva.SOLICITADA, solicitada.getEstado(), "pre-condicao: sala restrita");

            assertThat(cadastro.reservasQueColidemComManutencao(alvo, INICIO, FIM)).isEmpty();
            reservas.aprovar(administrador, solicitada.getId());
            assertThat(cadastro.reservasQueColidemComManutencao(alvo, INICIO, FIM)).extracting(Reserva::getId)
                    .containsExactly(solicitada.getId());
            reservas.cancelar(solicitante, solicitada.getId(), "remarcada");
            assertAll(
                    () -> assertThat(cadastro.reservasQueColidemComManutencao(alvo, INICIO, FIM)).isEmpty(),
                    () -> assertEquals("vazamento", cadastro.cadastrarManutencao(alvo, INICIO, FIM, "vazamento").getMotivo()));
        }

        @Test
        @DisplayName("Periodo nulo e recusado com 400; colisao com periodo nulo e vazia; encerrar id nulo responde 404")
        void entradasNulas() {
            Sala sala = sala("Sala", "aula", "Bloco A", false);
            assertAll(
                    () -> assertEquals(400, status(() -> cadastro.cadastrarManutencao(sala, null, FIM, "x"))),
                    () -> assertEquals(400, status(() -> cadastro.cadastrarManutencao(sala, INICIO, null, "x"))),
                    () -> assertThat(cadastro.reservasQueColidemComManutencao(sala, null, FIM)).isEmpty(),
                    () -> assertThat(cadastro.reservasQueColidemComManutencao(sala, INICIO, null)).isEmpty(),
                    () -> assertEquals(404, status(() -> cadastro.encerrarManutencao(null))));
        }

        @Test
        @DisplayName("Rejeitar reserva inexistente responde 409")
        void rejeitarInexistente() {
            assertEquals(409, status(() -> reservas.rejeitar(administrador, UUID.randomUUID(), "motivo")));
        }
    }

    @Nested
    @DisplayName("Senha e auditoria")
    class DeSenhaEAuditoria {

        @Test
        @DisplayName("Hash guardado malformado nunca confere; senha nula ou vazia nunca confere; o hash certo confere")
        void hashMalformado() {
            String valido = com.edualoca.domain.usuario.SenhaHasher.gerar("senhaForte123");
            String[] partes = valido.split("\\$");
            List<String> malformados = List.of("texto-qualquer", "md5$1$" + partes[2] + "$" + partes[3],
                    "pbkdf2-sha256$abc$" + partes[2] + "$" + partes[3], "pbkdf2-sha256$0$" + partes[2] + "$" + partes[3],
                    "pbkdf2-sha256$600000$$" + partes[3], "pbkdf2-sha256$600000$" + partes[2] + "$",
                    "pbkdf2-sha256$600000$!!$" + partes[3], valido + "$extra");
            assertAll(
                    () -> assertThat(com.edualoca.domain.usuario.SenhaHasher.confere("senhaForte123", valido)).isTrue(),
                    () -> assertThat(com.edualoca.domain.usuario.SenhaHasher.confere(null, valido)).isFalse(),
                    () -> assertThat(com.edualoca.domain.usuario.SenhaHasher.confere("", valido)).isFalse(),
                    () -> assertThat(com.edualoca.domain.usuario.SenhaHasher.confere("senhaForte123", null)).isFalse(),
                    () -> malformados.forEach(m -> assertThat(com.edualoca.domain.usuario.SenhaHasher.confere("senhaForte123", m))
                            .as(m).isFalse()));
        }

        @Test
        @DisplayName("Auditoria: filtro so por acao, entidade com espacos, filtros vazios trazem tudo e limite zero nada")
        void filtrosDaAuditoria() {
            com.edualoca.domain.auditoria.AuditoriaService auditoria = new com.edualoca.domain.auditoria.AuditoriaService();
            Sala sala = sala("Sala", "aula", "Bloco A", false);
            Reserva reserva = reservar(sala, INICIO, FIM);
            reservas.cancelar(solicitante, reserva.getId(), "remarcada");
            int total = Banco.auditoria.size();

            assertAll(
                    () -> assertThat(auditoria.listar(null, "criacao", 500)).allMatch(e -> "CRIACAO".equals(e.getAcao()))
                            .extracting(e -> e.getEntidadeId()).contains(sala.getId(), reserva.getId()),
                    () -> assertThat(auditoria.listar("Reserva", null, 500)).extracting(e -> e.getAcao())
                            .contains("CRIACAO", "CANCELAMENTO"),
                    () -> assertThat(auditoria.listar(" sala ", null, 500)).allMatch(e -> "Sala".equals(e.getEntidade()))
                            .extracting(e -> e.getEntidadeId()).contains(sala.getId()),
                    () -> assertThat(auditoria.listar(" ", "", 500)).hasSize(total),
                    () -> assertThat(auditoria.listar(null, null, 0)).isEmpty(),
                    () -> assertThat(auditoria.listar("Reserva", "CRIACAO", 500)).extracting(e -> e.getEntidadeId())
                            .containsExactly(reserva.getId()));
        }
    }
}
