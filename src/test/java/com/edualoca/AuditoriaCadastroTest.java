package com.edualoca;

import com.edualoca.domain.auditoria.AuditoriaEvento;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RF-28: toda operacao de escrita em cadastro gera entrada de auditoria.
 *
 * <p>Lacuna achada na mutacao manual U8 (docs/testes/mutacao-manual.md): sem a auditoria do cadastro de manutencao,
 * toda a suite ficava verde. Os testes RF-28 de {@code AuditoriaTest} consultam um id aleatorio por
 * {@code AuditoriaService.consultarPorEntidade}, que devolve um evento inventado quando nao ha nenhum, entao passam com
 * qualquer codigo. Aqui o evento e lido direto de {@link Banco#auditoria}, filtrado pelo id da entidade gravada.
 */
class AuditoriaCadastroTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-06-01T13:00:00Z"), FUSO);
    private static final LocalDateTime INICIO = LocalDateTime.of(2027, 1, 10, 8, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2027, 1, 10, 18, 0);

    private static Stream<Arguments> escritas() {
        return Stream.of(
                Arguments.of("Sala", "CRIACAO", escrita(c -> c.cadastrarSala("Lab 9", "laboratorio", 20, "Bloco C", false).getId())),
                Arguments.of("Professor", "CRIACAO", escrita(c -> c.cadastrarProfessor("Prof. Auditoria",
                        "AUD-" + UUID.randomUUID(), List.of(new Competencia("Redes"))).getId())),
                Arguments.of("Material", "CRIACAO", escrita(c -> c.cadastrarMaterial("Kit Arduino", "kit", 5, false).getId())),
                Arguments.of("Manutencao", "CRIACAO", escrita(c -> c.cadastrarManutencao(
                        c.cadastrarSala("Lab 10", "laboratorio", 20, "Bloco C", false), INICIO, FIM, "Troca de piso").getId())),
                Arguments.of("Manutencao", "CRIACAO", escrita(c -> c.cadastrarManutencao(
                        c.cadastrarMaterial("Osciloscopio", "equipamento", 2, false), INICIO, FIM, "Calibracao").getId())),
                Arguments.of("Manutencao", "CRIACAO", escrita(c -> c.cadastrarManutencao(
                        c.cadastrarProfessor("Prof. Licenca", "AUD-" + UUID.randomUUID(), List.of(new Competencia("Fisica"))),
                        INICIO, FIM, "Licenca").getId())),
                Arguments.of("Sala", "INATIVACAO", escrita(c -> {
                    Sala sala = c.cadastrarSala("Sala 404", "aula", 40, "Bloco C", false);
                    c.inativarSala(sala);
                    return sala.getId();
                })));
    }

    private static Function<CadastroRecursoService, UUID> escrita(Function<CadastroRecursoService, UUID> f) {
        return f;
    }

    @ParameterizedTest(name = "{0} {1} gera um evento de auditoria")
    @MethodSource("escritas")
    @DisplayName("RF-28: cada escrita de cadastro gera exatamente um evento com acao e instante do relogio")
    void escritaDeCadastroGeraAuditoria(String entidade, String acao, Function<CadastroRecursoService, UUID> escrita) {
        // arrange
        CadastroRecursoService cadastro = new CadastroRecursoService(RELOGIO);

        // act
        UUID entidadeId = escrita.apply(cadastro);

        // assert
        List<AuditoriaEvento> eventos = Banco.auditoria.stream()
                .filter(e -> e.getEntidade().equals(entidade) && e.getEntidadeId().equals(entidadeId)
                        && e.getAcao().equals(acao))
                .toList();
        assertEquals(1, eventos.size(), "eventos " + acao + " de " + entidade);
        assertEquals(LocalDateTime.now(RELOGIO), eventos.get(0).getDataHora());
    }
}
