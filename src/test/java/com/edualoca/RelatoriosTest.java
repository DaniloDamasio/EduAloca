package com.edualoca;

import com.edualoca.domain.auditoria.AuditoriaService;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.relatorio.RelatorioService;
import com.edualoca.domain.relatorio.UtilizacaoPorPeriodo;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RF-34 a RF-36 no dominio, sobre {@link RelatorioService#gerarUtilizacaoPorPeriodo}, o mesmo metodo da rota
 * {@code GET /relatorios/utilizacao}. Os valores pela API, com recorte na borda e fuso, estao em
 * {@code RelatorioUtilizacaoApiTest} e {@code RelatorioFusoHorarioTest}.
 *
 * <p>Antes estes testes exercitavam {@code gerarRelatorioUtilizacao} e {@code contarRejeicoesPorSobreposicao}, metodos
 * antigos sem rota e com regra diferente da rota (removidos como codigo morto em 12/11/2026); dois deles
 * ({@code rf34T1}, {@code rf35T1}) so conferiam {@code >= 0} e passavam com qualquer implementacao.
 */
class RelatoriosTest {

    private static final LocalDateTime INICIO_MARCO = LocalDateTime.of(2026, 3, 1, 0, 0);
    private static final LocalDateTime FIM_MARCO = LocalDateTime.of(2026, 4, 1, 0, 0);

    private final RelatorioService relatorioService = new RelatorioService();

    private static Usuario usuario(Perfil perfil) {
        return new Usuario(UUID.randomUUID(), perfil.name().toLowerCase() + "@example.com", "hash", perfil);
    }

    private static Sala sala(String nome, boolean restrita) {
        return new Sala(UUID.randomUUID(), nome, "aula", 10, "Bloco A", restrita);
    }

    @Test
    @DisplayName("RF-34-T1: reservas APROVADA, EM_USO e CONCLUIDA do periodo contam com as horas reais; SOLICITADA e fora do periodo nao")
    void rf34T1_contaSoReservasEmUsoNoPeriodo() {
        // arrange
        ReservaService reservaService = new ReservaService();
        Usuario solicitante = usuario(Perfil.SOLICITANTE);
        Sala aprovada = sala("Sala Aprovada", false);
        Sala emUso = sala("Sala Em Uso", false);
        Sala concluida = sala("Sala Concluida", false);
        Sala solicitada = sala("Sala Restrita", true);
        Sala foraDoPeriodo = sala("Sala Abril", false);
        reservaService.criar(solicitante, LocalDateTime.of(2026, 3, 4, 10, 0), LocalDateTime.of(2026, 3, 4, 11, 0),
                "Aula aprovada", List.of(new ReservaRecurso(aprovada, 1)));
        reservaService.criar(solicitante, LocalDateTime.of(2026, 3, 5, 10, 0), LocalDateTime.of(2026, 3, 5, 12, 0),
                "Aula em uso", List.of(new ReservaRecurso(emUso, 1))).setEstado(EstadoReserva.EM_USO);
        reservaService.criar(solicitante, LocalDateTime.of(2026, 3, 6, 10, 0), LocalDateTime.of(2026, 3, 6, 13, 0),
                "Aula concluida", List.of(new ReservaRecurso(concluida, 1))).setEstado(EstadoReserva.CONCLUIDA);
        Reserva pendente = reservaService.criar(solicitante, LocalDateTime.of(2026, 3, 7, 10, 0),
                LocalDateTime.of(2026, 3, 7, 12, 0), "Aula pendente", List.of(new ReservaRecurso(solicitada, 1)));
        reservaService.criar(solicitante, LocalDateTime.of(2026, 4, 5, 10, 0), LocalDateTime.of(2026, 4, 5, 12, 0),
                "Aula de abril", List.of(new ReservaRecurso(foraDoPeriodo, 1))).setEstado(EstadoReserva.CONCLUIDA);
        assertEquals(EstadoReserva.SOLICITADA, pendente.getEstado(), "pre-condicao: sala restrita nasce SOLICITADA");

        // act
        UtilizacaoPorPeriodo relatorio = relatorioService.gerarUtilizacaoPorPeriodo(usuario(Perfil.RESPONSAVEL),
                INICIO_MARCO, FIM_MARCO);
        Map<UUID, Long> minutosPorSala = relatorio.recursos().stream()
                .collect(Collectors.toMap(UtilizacaoPorPeriodo.UsoRecurso::id, UtilizacaoPorPeriodo.UsoRecurso::minutos));

        // assert
        assertAll(
                () -> assertEquals(60L, minutosPorSala.get(aprovada.getId())),
                () -> assertEquals(120L, minutosPorSala.get(emUso.getId())),
                () -> assertEquals(180L, minutosPorSala.get(concluida.getId())),
                () -> assertFalse(minutosPorSala.containsKey(solicitada.getId()), "SOLICITADA nao conta"),
                () -> assertFalse(minutosPorSala.containsKey(foraDoPeriodo.getId()), "fora do periodo nao conta"));
    }

    @Test
    @DisplayName("RF-34-T2: periodo sem nenhuma reserva devolve listas vazias e zero conflitos, sem erro")
    void rf34T2_periodoSemReservas_devolveZerado() {
        UtilizacaoPorPeriodo relatorio = relatorioService.gerarUtilizacaoPorPeriodo(usuario(Perfil.ADMINISTRADOR),
                LocalDateTime.of(2099, 1, 1, 0, 0), LocalDateTime.of(2099, 1, 2, 0, 0));

        assertAll(
                () -> assertTrue(relatorio.recursos().isEmpty()),
                () -> assertTrue(relatorio.professores().isEmpty()),
                () -> assertEquals(0, relatorio.conflitosEvitados()));
    }

    @Test
    @DisplayName("RF-34-T3: fim menor ou igual ao inicio e recusado com 400")
    void rf34T3_fimMenorOuIgualInicio_400() {
        Usuario responsavel = usuario(Perfil.RESPONSAVEL);

        assertAll(
                () -> assertEquals(400, assertThrows(RegraNegocioException.class,
                        () -> relatorioService.gerarUtilizacaoPorPeriodo(responsavel, FIM_MARCO, INICIO_MARCO)).getStatusHttp()),
                () -> assertEquals(400, assertThrows(RegraNegocioException.class,
                        () -> relatorioService.gerarUtilizacaoPorPeriodo(responsavel, INICIO_MARCO, INICIO_MARCO)).getStatusHttp()));
    }

    @Test
    @DisplayName("RF-35-T1: conflitos evitados contam so REJEICAO_SOBREPOSICAO de Reserva registrada dentro do periodo")
    void rf35T1_conflitosEvitados_respeitamEntidadeAcaoEPeriodo() {
        // arrange: um evento que conta e tres que nao contam (outra acao, outra entidade, fora do periodo)
        AuditoriaService.registrar("Reserva", UUID.randomUUID(), "REJEICAO_SOBREPOSICAO", UUID.randomUUID(),
                LocalDateTime.of(2026, 3, 10, 9, 0));
        AuditoriaService.registrar("Reserva", UUID.randomUUID(), "CRIACAO", UUID.randomUUID(),
                LocalDateTime.of(2026, 3, 10, 9, 5));
        AuditoriaService.registrar("Sala", UUID.randomUUID(), "REJEICAO_SOBREPOSICAO", UUID.randomUUID(),
                LocalDateTime.of(2026, 3, 10, 9, 10));
        AuditoriaService.registrar("Reserva", UUID.randomUUID(), "REJEICAO_SOBREPOSICAO", UUID.randomUUID(),
                FIM_MARCO);

        // act
        long conflitos = relatorioService.gerarUtilizacaoPorPeriodo(usuario(Perfil.RESPONSAVEL), INICIO_MARCO, FIM_MARCO)
                .conflitosEvitados();

        // assert: periodo semiaberto [inicio, fim): o evento exatamente no fim fica de fora
        assertEquals(1, conflitos);
    }

    @Test
    @DisplayName("RF-36-T1: Solicitante recebe 403, antes da validacao do periodo")
    void rf36T1_solicitante_403() {
        Usuario solicitante = usuario(Perfil.SOLICITANTE);

        assertAll(
                () -> assertEquals(403, assertThrows(RegraNegocioException.class,
                        () -> relatorioService.gerarUtilizacaoPorPeriodo(solicitante, INICIO_MARCO, FIM_MARCO)).getStatusHttp()),
                () -> assertEquals(403, assertThrows(RegraNegocioException.class,
                        () -> relatorioService.gerarUtilizacaoPorPeriodo(solicitante, FIM_MARCO, INICIO_MARCO)).getStatusHttp()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Perfil.class, names = {"RESPONSAVEL", "ADMINISTRADOR"})
    @DisplayName("RF-36-T2: Responsavel e Administrador geram o relatorio")
    void rf36T2_responsavelEAdministrador_podem(Perfil perfil) {
        UtilizacaoPorPeriodo relatorio = relatorioService.gerarUtilizacaoPorPeriodo(usuario(perfil), INICIO_MARCO, FIM_MARCO);

        assertAll(
                () -> assertTrue(relatorio.recursos() != null),
                () -> assertTrue(relatorio.professores() != null));
    }
}
