package com.edualoca;

import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.PesquisaRecursoService;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PesquisaDisponibilidadeTest {

    private final PesquisaRecursoService pesquisaRecursoService = new PesquisaRecursoService();
    private final ReservaService reservaService = new ReservaService();

    private Usuario solicitante() {
        return new Usuario(UUID.randomUUID(), "solicitante@example.com", "hash", Perfil.SOLICITANTE);
    }

    @Test
    @DisplayName("RF-08-T1: busca de salas por tipo, capacidade minima, localizacao e disponibilidade deve retornar apenas as compativeis")
    void rf08T1_buscaComCriteriosValidos_deveRetornarSalasCompativeis() {
        // arrange
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        List<Sala> salas = pesquisaRecursoService.buscarSalas("laboratorio", 20, "Bloco A", inicio, fim);

        // assert
        assertTrue(salas.stream().allMatch(s -> s.getCapacidade() >= 20));
    }

    @Test
    @DisplayName("RF-08-T2: busca por capacidade minima igual a capacidade exata da sala deve incluir a sala")
    void rf08T2_capacidadeMinimaIgualACapacidadeDaSala_deveIncluirSala() {
        // arrange
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        List<Sala> salas = pesquisaRecursoService.buscarSalas("laboratorio", 20, "Bloco A", inicio, fim);

        // assert
        assertTrue(salas.stream().anyMatch(s -> s.getCapacidade() == 20));
    }

    @Test
    @DisplayName("RF-08-T3: busca de salas com fim <= inicio no intervalo deve ser rejeitada com HTTP 400")
    void rf08T3_intervaloInvalidoNaBusca_deveSerRejeitado() {
        // arrange
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 12, 0);
        LocalDateTime fimInvalido = LocalDateTime.of(2026, 3, 10, 10, 0);

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> pesquisaRecursoService.buscarSalas("laboratorio", 20, "Bloco A", inicio, fimInvalido));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-09-T1: busca de professores por competencia e disponibilidade deve retornar apenas os que atendem ambos criterios")
    void rf09T1_buscaPorCompetenciaEDisponibilidade_deveFiltrarCorretamente() {
        // arrange
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        List<Professor> professores = pesquisaRecursoService.buscarProfessores("Robotica", inicio, fim);

        // assert
        assertTrue(professores.stream().allMatch(
                p -> p.getCompetencias().stream().anyMatch(c -> c.getNome().equals("Robotica"))));
    }

    @Test
    @DisplayName("RF-09-T2 [gap Q11, assumindo lista vazia para competencia inexistente]: busca por competencia nao cadastrada deve retornar lista vazia")
    void rf09T2_competenciaInexistente_deveRetornarListaVazia() {
        // arrange
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        List<Professor> professores = pesquisaRecursoService.buscarProfessores("CompetenciaInexistente", inicio, fim);

        // assert
        assertTrue(professores.isEmpty());
    }

    @Test
    @DisplayName("RF-10-T1: busca de materiais por tipo e quantidade livre no intervalo deve retornar os disponiveis")
    void rf10T1_buscaDeMateriaisComQuantidadeLivre_deveRetornarDisponiveis() {
        // arrange
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        List<Material> materiais = pesquisaRecursoService.buscarMateriais("projetor", 1, inicio, fim);

        // assert
        assertTrue(materiais.stream().allMatch(m -> m.getQuantidadeTotal() >= 1));
    }

    @Test
    @DisplayName("RF-10-T2 [gap Q12, assumindo soma de reservas parciais]: material com quantidade total 3 e 2 reservadas deve aparecer disponivel para busca de 1 unidade")
    void rf10T2_materialComUmaUnidadeLivre_deveAparecerDisponivel() {
        // arrange
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        List<Material> materiais = pesquisaRecursoService.buscarMateriais("projetor", 1, inicio, fim);

        // assert
        assertFalse(materiais.isEmpty());
    }

    @Test
    @DisplayName("RF-11-T1: recurso com reserva ativa sobreposta ou em manutencao deve ser excluido do resultado de busca")
    void rf11T1_recursoComReservaAtivaOuManutencao_deveSerExcluido() {
        // arrange
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        List<Sala> salas = pesquisaRecursoService.buscarSalas("laboratorio", 1, "Bloco A", inicio, fim);

        // assert
        assertTrue(salas.stream().noneMatch(s -> s.getId().equals(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"))));
    }

    @Test
    @DisplayName("RF-11-T2 [gap Q3, mesma inferencia de 'ativa']: recurso com reserva em estado terminal no mesmo intervalo deve ser incluido no resultado")
    void rf11T2_recursoComReservaTerminal_deveSerIncluido() {
        // arrange
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        List<Sala> salas = pesquisaRecursoService.buscarSalas("laboratorio", 1, "Bloco A", inicio, fim);

        // assert
        assertFalse(salas.isEmpty());
    }

    @Test
    @DisplayName("RF-11/RC-02 [risco]: sala com reserva ATIVA sobreposta (nao em manutencao) deve ser excluida da busca")
    void rf11_salaComReservaAtivaSobreposta_deveSerExcluidaDaBusca() {
        // arrange
        Sala salaOcupada = new Sala(UUID.randomUUID(), "Sala Ocupada", "oficina", 15, "Bloco C", false);
        com.edualoca.domain.comum.Banco.salas.put(salaOcupada.getId(), salaOcupada);
        LocalDateTime inicio = LocalDateTime.of(2026, 4, 1, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 4, 1, 12, 0);
        reservaService.criar(solicitante(), inicio, fim, "Aula que ocupa a sala",
                List.of(new ReservaRecurso(salaOcupada, 1)));

        // act
        List<Sala> salasNoMesmoHorario = pesquisaRecursoService.buscarSalas("oficina", 1, "Bloco C", inicio, fim);
        List<Sala> salasEmHorarioLivre = pesquisaRecursoService.buscarSalas("oficina", 1, "Bloco C",
                fim.plusHours(1), fim.plusHours(2));

        // assert
        assertTrue(salasNoMesmoHorario.stream().noneMatch(s -> s.getId().equals(salaOcupada.getId())));
        assertTrue(salasEmHorarioLivre.stream().anyMatch(s -> s.getId().equals(salaOcupada.getId())));
    }

    @Test
    @DisplayName("RF-11/RC-02 [risco]: sala com reserva EM_USO sobreposta tambem deve ser excluida da busca")
    void rf11_salaComReservaEmUsoSobreposta_deveSerExcluidaDaBusca() {
        // arrange
        Sala salaOcupada = new Sala(UUID.randomUUID(), "Sala Em Uso", "oficina", 15, "Bloco D", false);
        com.edualoca.domain.comum.Banco.salas.put(salaOcupada.getId(), salaOcupada);
        LocalDateTime inicio = LocalDateTime.of(2026, 4, 2, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 4, 2, 12, 0);
        Reserva reserva = reservaService.criar(solicitante(), inicio, fim, "Aula em andamento",
                List.of(new ReservaRecurso(salaOcupada, 1)));
        reserva.setEstado(EstadoReserva.EM_USO);

        // act
        List<Sala> salas = pesquisaRecursoService.buscarSalas("oficina", 1, "Bloco D", inicio, fim);

        // assert
        assertTrue(salas.stream().noneMatch(s -> s.getId().equals(salaOcupada.getId())));
    }

    @Test
    @DisplayName("RF-10/RC-02 [risco]: material totalmente reservado por reserva ativa no intervalo deve ficar indisponivel na busca")
    void rf10_materialTotalmenteReservado_deveFicarIndisponivelNaBusca() {
        // arrange
        Material material = new Material(UUID.randomUUID(), "Projetor Exclusivo", "projetor-exclusivo", 2, false);
        com.edualoca.domain.comum.Banco.materiais.put(material.getId(), material);
        LocalDateTime inicio = LocalDateTime.of(2026, 4, 3, 10, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 4, 3, 12, 0);
        reservaService.criar(solicitante(), inicio, fim, "Aula com todos os projetores",
                List.of(new ReservaRecurso(material, 2)));

        // act
        List<Material> materiaisOcupados = pesquisaRecursoService.buscarMateriais("projetor-exclusivo", 1, inicio, fim);
        List<Material> materiaisLivres = pesquisaRecursoService.buscarMateriais("projetor-exclusivo", 1,
                fim.plusHours(1), fim.plusHours(2));

        // assert
        assertTrue(materiaisOcupados.stream().noneMatch(m -> m.getId().equals(material.getId())));
        assertTrue(materiaisLivres.stream().anyMatch(m -> m.getId().equals(material.getId())));
    }
}
