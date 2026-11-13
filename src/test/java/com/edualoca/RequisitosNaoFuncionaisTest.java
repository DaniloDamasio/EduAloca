package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.usuario.AutenticacaoService;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequisitosNaoFuncionaisTest {

    private final CadastroRecursoService cadastroRecursoService = new CadastroRecursoService();
    private final AutenticacaoService autenticacaoService = new AutenticacaoService();

    @Test
    @DisplayName("RNF-04-T1: campo obrigatorio ausente/mal formatado em endpoint de escrita deve retornar HTTP 400")
    void rnf04T1_campoObrigatorioAusente_deveRetornar400() {
        // arrange & act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> cadastroRecursoService.cadastrarSala(null, "laboratorio", 20, "Bloco A", false));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RNF-05-T1: nenhuma credencial guardada esta em texto plano; todas sao PBKDF2 com sal proprio")
    void rnf05T1_senhaDeUsuario_nuncaDevePersistirEmTextoPlano() {
        // arrange: contas do seed (senhas conhecidas) e duas contas novas com a MESMA senha
        Banco.seedSeNecessario();
        java.util.Map<String, String> senhasDoSeed = java.util.Map.of(
                "usuario@example.com", "senhaValida123", "novo@example.com", "minhaSenha123",
                "responsavel@example.com", "senhaResponsavel123", "admin@example.com", "senhaAdmin123");
        Banco.registrarCredencial("gemeo.a@example.com", "mesmaSenha123");
        Banco.registrarCredencial("gemeo.b@example.com", "mesmaSenha123");

        // act + assert: o que fica guardado, e nao um hash calculado na hora pelo teste
        assertAll(Banco.credenciais.entrySet().stream().<org.junit.jupiter.api.function.Executable>map(credencial -> () -> {
            String guardado = credencial.getValue();
            assertTrue(guardado.startsWith("pbkdf2-sha256$600000$"), credencial.getKey() + " sem PBKDF2: " + guardado);
            String senha = senhasDoSeed.get(credencial.getKey());
            if (senha != null) {
                assertFalse(guardado.contains(senha), credencial.getKey() + " guarda a senha em claro");
            }
        }));
        assertAll(
                () -> assertNotEquals(Banco.credenciais.get("gemeo.a@example.com"), Banco.credenciais.get("gemeo.b@example.com"),
                        "mesma senha deve gerar hashes diferentes (sal por conta)"),
                () -> assertFalse(Banco.credenciais.get("gemeo.a@example.com").contains("mesmaSenha123")),
                // e o login continua funcionando a partir do hash guardado
                () -> assertEquals("gemeo.a@example.com", autenticacaoService.autenticar("gemeo.a@example.com", "mesmaSenha123").getEmail()));
    }

    @Test
    @DisplayName("RNF-06-T1: erro inesperado com SQL, classe e stack trace na mensagem vira 500 generico, sem nenhum detalhe interno")
    void rnf06T1_respostaDeErro_naoDeveExporDetalheTecnico() {
        // arrange: uma excecao do tipo que vazaria detalhe se a resposta repetisse a mensagem
        RuntimeException falhaInterna = new IllegalStateException(
                "org.postgresql.util.PSQLException: SELECT * FROM usuario WHERE senha = 'x' at com.edualoca.persistencia.ReservaRepositorioJdbc.inserir(ReservaRepositorioJdbc.java:57)");
        falhaInterna.addSuppressed(new NullPointerException("com.edualoca.domain.reserva.Reserva.getId()"));

        // act
        org.springframework.http.ResponseEntity<org.springframework.http.ProblemDetail> resposta =
                new com.edualoca.api.TratadorDeErros().inesperado(falhaInterna);

        // assert
        org.springframework.http.ProblemDetail corpo = resposta.getBody();
        String tudo = String.valueOf(corpo.getTitle()) + corpo.getDetail() + corpo.getProperties();
        assertAll(
                () -> assertEquals(500, resposta.getStatusCode().value()),
                () -> assertEquals("Não foi possível concluir a operação. Tente novamente mais tarde.", corpo.getDetail()),
                () -> java.util.List.of("SELECT", "usuario", "com.edualoca", "PSQLException", ".java:", "Exception", "senha")
                        .forEach(vazamento -> assertFalse(tudo.contains(vazamento), "resposta expoe '" + vazamento + "': " + tudo)));
    }

    @Test
    @DisplayName("RNF-09-T1: apos um fluxo real, todo historico e evento de auditoria aponta para reserva existente e todo recurso para o catalogo")
    void rnf09T1_integridadeReferencial_naoDeveApresentarRegistrosOrfaos() {
        // arrange: fluxo real (criacao, aprovacao, cancelamento e uma rejeicao por sobreposicao)
        Banco.seedSeNecessario();
        com.edualoca.domain.reserva.HistoricoReservaRepositorioEmMemoria historico =
                new com.edualoca.domain.reserva.HistoricoReservaRepositorioEmMemoria();
        com.edualoca.domain.reserva.ReservaService reservas = new com.edualoca.domain.reserva.ReservaService(
                java.time.Clock.systemDefaultZone(), new com.edualoca.domain.reserva.ReservaRepositorioEmMemoria(), historico,
                com.edualoca.domain.reserva.Transacao.direta());
        Usuario solicitante = new Usuario(java.util.UUID.randomUUID(), "sol@example.com", "hash", com.edualoca.domain.usuario.Perfil.SOLICITANTE);
        Usuario admin = new Usuario(Banco.ADMINISTRADOR_ID, "admin@example.com", "hash", com.edualoca.domain.usuario.Perfil.ADMINISTRADOR);
        com.edualoca.domain.recurso.Sala comum = cadastroRecursoService.cadastrarSala("Sala RNF09", "aula", 20, "Bloco A", false);
        com.edualoca.domain.recurso.Sala restrita = cadastroRecursoService.cadastrarSala("Lab RNF09", "laboratorio", 20, "Bloco A", true);
        java.time.LocalDateTime inicio = java.time.LocalDateTime.of(2027, 9, 1, 8, 0);
        var primeira = reservas.criar(solicitante, inicio, inicio.plusHours(2), "Aula",
                java.util.List.of(new com.edualoca.domain.reserva.ReservaRecurso(comum, 1)));
        var pendente = reservas.criar(solicitante, inicio, inicio.plusHours(2), "Lab",
                java.util.List.of(new com.edualoca.domain.reserva.ReservaRecurso(restrita, 1)));
        reservas.aprovar(admin, pendente.getId());
        reservas.cancelar(solicitante, primeira.getId(), "remarcada");
        assertThrows(RegraNegocioException.class, () -> reservas.criar(solicitante, inicio, inicio.plusHours(1), "Conflito",
                java.util.List.of(new com.edualoca.domain.reserva.ReservaRecurso(restrita, 1))));

        // act: verificacao de integridade sobre o estado resultante
        java.util.Set<java.util.UUID> idsDeReserva = Banco.reservas.keySet();
        java.util.List<String> orfaos = new java.util.ArrayList<>();
        for (var reserva : Banco.reservas.values()) {
            historico.listarPorReserva(reserva.getId()).stream()
                    .filter(h -> !idsDeReserva.contains(h.reservaId()))
                    .forEach(h -> orfaos.add("historico " + h.id()));
            reserva.getRecursos().forEach(rr -> {
                boolean noCatalogo = rr.getRecurso() instanceof com.edualoca.domain.recurso.Sala sala
                        ? Banco.salas.get(sala.getId()) == sala
                        : rr.getRecurso() instanceof com.edualoca.domain.recurso.Material material
                        ? Banco.materiais.get(material.getId()) == material
                        : rr.getRecurso() instanceof com.edualoca.domain.recurso.Professor professor
                        && Banco.professores.get(professor.getId()) == professor;
                if (!noCatalogo) {
                    orfaos.add("recurso fora do catalogo na reserva " + reserva.getId());
                }
            });
        }
        // REJEICAO_SOBREPOSICAO aponta, por definicao, para a reserva tentada e recusada, que nunca foi gravada
        Banco.auditoria.stream()
                .filter(e -> "Reserva".equals(e.getEntidade()) && !"REJEICAO_SOBREPOSICAO".equals(e.getAcao()))
                .filter(e -> !idsDeReserva.contains(e.getEntidadeId()))
                .forEach(e -> orfaos.add("auditoria " + e.getAcao() + " " + e.getEntidadeId()));

        // assert
        assertAll(
                () -> assertEquals(java.util.List.of(), orfaos),
                () -> assertEquals(2, historico.listarPorReserva(primeira.getId()).size(), "criacao + cancelamento"),
                () -> assertEquals(2, historico.listarPorReserva(pendente.getId()).size(), "criacao + aprovacao"),
                () -> assertTrue(Banco.auditoria.stream().anyMatch(e -> "REJEICAO_SOBREPOSICAO".equals(e.getAcao())),
                        "pre-condicao: houve rejeicao por sobreposicao"));
    }
}
