package com.edualoca;

import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.PesquisaRecursoService;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CadastroConsultaTest {

    private final CadastroRecursoService cadastroRecursoService = new CadastroRecursoService();

    @Test
    @DisplayName("RF-04-T1: ADM cria sala com todos os campos validos")
    void rf04T1_criacaoDeSalaComCamposValidos_deveCriar() {
        // arrange & act
        Sala sala = cadastroRecursoService.cadastrarSala("Sala 101", "laboratorio", 30, "Bloco A", false);

        // assert
        assertEquals(30, sala.getCapacidade());
    }

    @Test
    @DisplayName("RF-04-T2: criacao de sala sem capacidade deve ser rejeitada com HTTP 400")
    void rf04T2_criacaoDeSalaSemCapacidade_deveSerRejeitada() {
        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> cadastroRecursoService.cadastrarSala("Sala 101", "laboratorio", 0, "Bloco A", false));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-04-T3 [gap Q8, assumindo capacidade minima positiva]: capacidade zero ou negativa deve ser rejeitada com HTTP 400")
    void rf04T3_capacidadeZeroOuNegativa_deveSerRejeitada() {
        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> cadastroRecursoService.cadastrarSala("Sala 101", "laboratorio", -5, "Bloco A", false));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-04-T4: ADM inativa sala existente (soft delete) e ela nao aparece mais na listagem de ativos")
    void rf04T4_inativacaoDeSala_naoDeveAparecerNaListagem() {
        // arrange
        Sala sala = cadastroRecursoService.cadastrarSala("Sala 101", "laboratorio", 30, "Bloco A", false);

        // act
        cadastroRecursoService.inativarSala(sala);
        List<Sala> salasAtivas = cadastroRecursoService.listarSalasAtivas();

        // assert
        assertFalse(salasAtivas.contains(sala));
    }

    @Test
    @DisplayName("RF-05-T1: ADM cria professor com nome, identificador institucional e competencias")
    void rf05T1_criacaoDeProfessorComCamposValidos_deveCriar() {
        // act
        Professor professor = cadastroRecursoService.cadastrarProfessor("Prof. Ana", "MAT-100", List.of());

        // assert
        assertEquals("MAT-100", professor.getIdentificadorInstitucional());
    }

    @Test
    @DisplayName("RF-05-T2 [gap Q9, assumindo HTTP 409 para violacao de unicidade]: identificador institucional ja existente deve ser rejeitado como conflito")
    void rf05T2_identificadorInstitucionalDuplicado_deveSerRejeitadoComConflito() {
        // arrange
        cadastroRecursoService.cadastrarProfessor("Prof. Ana", "MAT-100", List.of());

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> cadastroRecursoService.cadastrarProfessor("Prof. Beto", "MAT-100", List.of()));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-06-T1: ADM cria material com nome, tipo, quantidade e restrito")
    void rf06T1_criacaoDeMaterialComCamposValidos_deveCriar() {
        // act
        Material material = cadastroRecursoService.cadastrarMaterial("Projetor", "audiovisual", 5, false);

        // assert
        assertEquals(5, material.getQuantidadeTotal());
    }

    @Test
    @DisplayName("RF-06-T2 [gap Q8, assumindo quantidade minima positiva]: quantidadeTotal zero deve ser rejeitada com HTTP 400")
    void rf06T2_quantidadeTotalZero_deveSerRejeitada() {
        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> cadastroRecursoService.cadastrarMaterial("Projetor", "audiovisual", 0, false));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-07-T1: consulta de salas deve retornar apenas registros ativos")
    void rf07T1_consultaDeSalas_deveRetornarApenasAtivas() {
        // act
        List<Sala> salas = cadastroRecursoService.listarSalasAtivas();

        // assert
        assertTrue(salas.stream().allMatch(Sala::isAtivo));
    }

    @Test
    @DisplayName("RF-07-T2: sala inativada nao deve aparecer na listagem de consulta")
    void rf07T2_salaInativada_naoDeveAparecerNaListagem() {
        // arrange
        Sala salaInativa = cadastroRecursoService.cadastrarSala("Sala Inativa", "laboratorio", 20, "Bloco B", false);
        cadastroRecursoService.inativarSala(salaInativa);

        // act
        List<Sala> salas = cadastroRecursoService.listarSalasAtivas();

        // assert
        assertFalse(salas.contains(salaInativa));
    }

    @Test
    @DisplayName("RF-07-T3 [gap Q10, assumindo que consulta exige autenticacao]: usuario nao autenticado consultando salas deve receber HTTP 401")
    void rf07T3_usuarioNaoAutenticado_deveReceber401() {
        // arrange
        com.edualoca.domain.usuario.Usuario usuarioAnonimo = null;

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> cadastroRecursoService.listarSalasAtivas(usuarioAnonimo));

        // assert
        assertEquals(401, ex.getStatusHttp());
    }

    // Lacunas da mutacao manual de cadastro (C1, C3, C5, C7): o cadastro precisa ficar visivel na consulta, e nao
    // so no objeto devolvido.

    private final PesquisaRecursoService pesquisaRecursoService = new PesquisaRecursoService();
    private static final LocalDateTime INICIO = LocalDateTime.of(2027, 6, 1, 8, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2027, 6, 1, 10, 0);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("RF-04-T5: sala sem nome (nulo, vazio ou em branco) e recusada com 400")
    void rf04T5_salaSemNome_eRecusada(String nome) {
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> cadastroRecursoService.cadastrarSala(nome, "laboratorio", 30, "Bloco A", false));

        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-04-T6: sala criada aparece na listagem de ativas")
    void rf04T6_salaCriada_apareceNaConsulta() {
        Sala sala = cadastroRecursoService.cadastrarSala("Sala " + UUID.randomUUID(), "aula", 30, "Bloco C", false);

        assertTrue(cadastroRecursoService.listarSalasAtivas().contains(sala));
    }

    @Test
    @DisplayName("RF-05-T3: professor criado aparece na busca por competencia")
    void rf05T3_professorCriado_apareceNaConsulta() {
        String competencia = "Competencia-" + UUID.randomUUID();
        Professor professor = cadastroRecursoService.cadastrarProfessor("Prof. Novo", "PN-" + UUID.randomUUID(),
                List.of(new Competencia(competencia)));

        assertEquals(List.of(professor), pesquisaRecursoService.buscarProfessores(competencia, INICIO, FIM));
    }

    @Test
    @DisplayName("RF-06-T3: material criado aparece na busca por tipo")
    void rf06T3_materialCriado_apareceNaConsulta() {
        String tipo = "tipo-" + UUID.randomUUID();
        Material material = cadastroRecursoService.cadastrarMaterial("Kit", tipo, 2, false);

        assertEquals(List.of(material), pesquisaRecursoService.buscarMateriais(tipo, 1, INICIO, FIM));
    }
}
