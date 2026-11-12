package com.edualoca;

import com.edualoca.domain.auditoria.AuditoriaEvento;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.PesquisaRecursoService;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DEF-015 / RF-04, RF-05, RF-06: o CRUD de sala, professor e material precisa de edicao e inativacao refletidas na
 * consulta, campos obrigatorios validados (400 com o nome do campo) e auditoria de cada escrita (RF-28).
 */
class CrudRecursoTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2027, 7, 1, 8, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2027, 7, 1, 10, 0);

    private final CadastroRecursoService cadastro = new CadastroRecursoService();
    private final PesquisaRecursoService pesquisa = new PesquisaRecursoService();

    private static List<AuditoriaEvento> eventos(String entidade, UUID id, String acao) {
        return Banco.auditoria.stream()
                .filter(e -> entidade.equals(e.getEntidade()) && id.equals(e.getEntidadeId()) && acao.equals(e.getAcao()))
                .toList();
    }

    private static void recusaComCampo(int status, String campo, Executable chamada) {
        RegraNegocioException ex = assertThrows(RegraNegocioException.class, chamada);
        assertAll(
                () -> assertEquals(status, ex.getStatusHttp()),
                () -> assertThat(ex.getMessage()).containsIgnoringCase(campo));
    }

    @Nested
    @DisplayName("RF-04 sala")
    class SalaCrud {

        @Test
        @DisplayName("RF-04: edicao muda os campos, aparece na busca com os novos criterios e gera um evento ATUALIZACAO")
        void edicaoDeSala_refleteNaBuscaEAudita() {
            String local = "Bloco-" + UUID.randomUUID();
            Sala sala = cadastro.cadastrarSala("Sala 1", "aula", 20, local, false);

            cadastro.atualizarSala(sala.getId(), "Sala 1 reformada", "laboratorio", 40, local, true);

            assertAll(
                    () -> assertEquals("Sala 1 reformada", sala.getNome()),
                    () -> assertEquals("laboratorio", sala.getTipo()),
                    () -> assertEquals(40, sala.getCapacidade()),
                    () -> assertEquals(true, sala.isRestrito()),
                    () -> assertThat(pesquisa.buscarSalas("laboratorio", 40, local, INICIO, FIM)).containsExactly(sala),
                    () -> assertThat(pesquisa.buscarSalas("aula", 0, local, INICIO, FIM)).isEmpty(),
                    () -> assertThat(eventos("Sala", sala.getId(), "ATUALIZACAO")).hasSize(1));
        }

        @Test
        @DisplayName("RF-04: edicao de sala inexistente responde 404")
        void edicaoDeSalaInexistente_404() {
            RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                    () -> cadastro.atualizarSala(UUID.randomUUID(), "X", "aula", 10, "Bloco A", false));
            assertEquals(404, ex.getStatusHttp());
        }

        static Stream<Arguments> salaInvalida() {
            return Stream.of(
                    Arguments.of("tipo", null, "Bloco A", 10),
                    Arguments.of("tipo", " ", "Bloco A", 10),
                    Arguments.of("localizacao", "aula", null, 10),
                    Arguments.of("localizacao", "aula", "", 10),
                    Arguments.of("capacidade", "aula", "Bloco A", 0));
        }

        @ParameterizedTest(name = "campo {0}")
        @MethodSource("salaInvalida")
        @DisplayName("RF-04: criacao e edicao recusam campo obrigatorio ausente com 400 que nomeia o campo")
        void campoObrigatorioDeSala(String campo, String tipo, String localizacao, int capacidade) {
            Sala existente = cadastro.cadastrarSala("Sala valida", "aula", 10, "Bloco A", false);

            recusaComCampo(400, campo, () -> cadastro.cadastrarSala("Sala", tipo, capacidade, localizacao, false));
            recusaComCampo(400, campo,
                    () -> cadastro.atualizarSala(existente.getId(), "Sala", tipo, capacidade, localizacao, false));
            assertEquals("aula", existente.getTipo(), "edicao recusada nao altera nada");
        }
    }

    @Nested
    @DisplayName("RF-05 professor")
    class ProfessorCrud {

        @Test
        @DisplayName("RF-05: edicao troca nome e competencias, reflete na busca e gera um evento ATUALIZACAO")
        void edicaoDeProfessor_refleteNaBuscaEAudita() {
            String antiga = "Antiga-" + UUID.randomUUID();
            String nova = "Nova-" + UUID.randomUUID();
            Professor professor = cadastro.cadastrarProfessor("Prof. Ana", "PA-" + UUID.randomUUID(),
                    List.of(new Competencia(antiga)));

            cadastro.atualizarProfessor(professor.getId(), "Prof. Ana Souza", List.of(new Competencia(nova)));

            assertAll(
                    () -> assertEquals("Prof. Ana Souza", professor.getNome()),
                    () -> assertThat(pesquisa.buscarProfessores(nova, INICIO, FIM)).containsExactly(professor),
                    () -> assertThat(pesquisa.buscarProfessores(antiga, INICIO, FIM)).isEmpty(),
                    () -> assertThat(eventos("Professor", professor.getId(), "ATUALIZACAO")).hasSize(1));
        }

        @Test
        @DisplayName("RF-05: inativacao tira o professor da busca e gera um evento INATIVACAO")
        void inativacaoDeProfessor_saiDaBuscaEAudita() {
            String competencia = "Comp-" + UUID.randomUUID();
            Professor professor = cadastro.cadastrarProfessor("Prof. Bia", "PB-" + UUID.randomUUID(),
                    List.of(new Competencia(competencia)));

            cadastro.inativarProfessor(professor.getId());

            assertAll(
                    () -> assertThat(pesquisa.buscarProfessores(competencia, INICIO, FIM)).isEmpty(),
                    () -> assertThat(eventos("Professor", professor.getId(), "INATIVACAO")).hasSize(1));
        }

        @Test
        @DisplayName("RF-05: nome e identificador obrigatorios; competencias nao podem ser nulas; inexistente 404")
        void validacoesDeProfessor() {
            Professor existente = cadastro.cadastrarProfessor("Prof. Caio", "PC-" + UUID.randomUUID(), List.of());

            recusaComCampo(400, "nome", () -> cadastro.cadastrarProfessor(" ", "PX-" + UUID.randomUUID(), List.of()));
            recusaComCampo(400, "identificador", () -> cadastro.cadastrarProfessor("Prof. X", "", List.of()));
            recusaComCampo(400, "competencias",
                    () -> cadastro.cadastrarProfessor("Prof. X", "PX-" + UUID.randomUUID(), null));
            recusaComCampo(400, "nome", () -> cadastro.atualizarProfessor(existente.getId(), null, List.of()));
            recusaComCampo(400, "competencias", () -> cadastro.atualizarProfessor(existente.getId(), "Prof. Caio", null));
            assertEquals(404, assertThrows(RegraNegocioException.class,
                    () -> cadastro.atualizarProfessor(UUID.randomUUID(), "Prof. Y", List.of())).getStatusHttp());
            assertEquals(404, assertThrows(RegraNegocioException.class,
                    () -> cadastro.inativarProfessor(UUID.randomUUID())).getStatusHttp());
            assertEquals("Prof. Caio", existente.getNome(), "edicao recusada nao altera nada");
        }
    }

    @Nested
    @DisplayName("RF-06 material")
    class MaterialCrud {

        @Test
        @DisplayName("RF-06: edicao muda os campos, reflete na busca e gera um evento ATUALIZACAO")
        void edicaoDeMaterial_refleteNaBuscaEAudita() {
            String tipoAntigo = "tipo-" + UUID.randomUUID();
            String tipoNovo = "tipo-" + UUID.randomUUID();
            Material material = cadastro.cadastrarMaterial("Notebook", tipoAntigo, 2, false);

            cadastro.atualizarMaterial(material.getId(), "Notebook i7", tipoNovo, 5, true);

            assertAll(
                    () -> assertEquals("Notebook i7", material.getNome()),
                    () -> assertEquals(5, material.getQuantidadeTotal()),
                    () -> assertEquals(true, material.isRestrito()),
                    () -> assertThat(pesquisa.buscarMateriais(tipoNovo, 5, INICIO, FIM)).containsExactly(material),
                    () -> assertThat(pesquisa.buscarMateriais(tipoAntigo, 1, INICIO, FIM)).isEmpty(),
                    () -> assertThat(eventos("Material", material.getId(), "ATUALIZACAO")).hasSize(1));
        }

        @Test
        @DisplayName("RF-06: inativacao tira o material da busca e gera um evento INATIVACAO")
        void inativacaoDeMaterial_saiDaBuscaEAudita() {
            String tipo = "tipo-" + UUID.randomUUID();
            Material material = cadastro.cadastrarMaterial("Microfone", tipo, 3, false);

            cadastro.inativarMaterial(material.getId());

            assertAll(
                    () -> assertThat(pesquisa.buscarMateriais(tipo, 1, INICIO, FIM)).isEmpty(),
                    () -> assertThat(eventos("Material", material.getId(), "INATIVACAO")).hasSize(1));
        }

        @Test
        @DisplayName("RF-06: nome e tipo obrigatorios, quantidade positiva; inexistente 404")
        void validacoesDeMaterial() {
            Material existente = cadastro.cadastrarMaterial("Cabo HDMI", "cabo", 4, false);

            recusaComCampo(400, "nome", () -> cadastro.cadastrarMaterial("", "cabo", 1, false));
            recusaComCampo(400, "tipo", () -> cadastro.cadastrarMaterial("Cabo", null, 1, false));
            recusaComCampo(400, "tipo", () -> cadastro.atualizarMaterial(existente.getId(), "Cabo", " ", 1, false));
            recusaComCampo(400, "quantidade", () -> cadastro.atualizarMaterial(existente.getId(), "Cabo", "cabo", 0, false));
            assertEquals(404, assertThrows(RegraNegocioException.class,
                    () -> cadastro.atualizarMaterial(UUID.randomUUID(), "X", "cabo", 1, false)).getStatusHttp());
            assertEquals(404, assertThrows(RegraNegocioException.class,
                    () -> cadastro.inativarMaterial(UUID.randomUUID())).getStatusHttp());
            assertEquals(4, existente.getQuantidadeTotal(), "edicao recusada nao altera nada");
        }
    }
}
