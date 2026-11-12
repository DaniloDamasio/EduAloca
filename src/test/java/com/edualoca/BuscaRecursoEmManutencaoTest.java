package com.edualoca;

import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.PesquisaRecursoService;
import com.edualoca.domain.recurso.Professor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEF-012 / RF-11: a busca de disponibilidade nao pode oferecer professor ou material em manutencao. Cada teste usa
 * competencia/tipo unicos, para nao depender do estado compartilhado de {@code Banco}.
 */
class BuscaRecursoEmManutencaoTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2027, 5, 10, 8, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2027, 5, 10, 10, 0);

    private final CadastroRecursoService cadastro = new CadastroRecursoService();
    private final PesquisaRecursoService pesquisa = new PesquisaRecursoService();

    @Test
    @DisplayName("DEF-012: professor em manutencao (afastamento) no intervalo nao aparece na busca; o livre aparece")
    void professorEmManutencao_naoApareceNaBusca() {
        String competencia = "Competencia-" + UUID.randomUUID();
        Professor afastado = cadastro.cadastrarProfessor("Prof. Afastado", "AF-" + UUID.randomUUID(),
                List.of(new Competencia(competencia)));
        Professor livre = cadastro.cadastrarProfessor("Prof. Livre", "LV-" + UUID.randomUUID(),
                List.of(new Competencia(competencia)));
        cadastro.cadastrarManutencao(afastado, INICIO.minusHours(1), FIM.plusHours(1), "licenca");

        List<Professor> encontrados = pesquisa.buscarProfessores(competencia, INICIO, FIM);

        assertThat(encontrados).containsExactly(livre);
    }

    @Test
    @DisplayName("DEF-012: material em manutencao no intervalo nao aparece na busca; o livre aparece")
    void materialEmManutencao_naoApareceNaBusca() {
        String tipo = "tipo-" + UUID.randomUUID();
        Material quebrado = cadastro.cadastrarMaterial("Projetor quebrado", tipo, 5, false);
        Material livre = cadastro.cadastrarMaterial("Projetor livre", tipo, 5, false);
        cadastro.cadastrarManutencao(quebrado, INICIO, FIM, "lampada queimada");

        List<Material> encontrados = pesquisa.buscarMateriais(tipo, 1, INICIO, FIM);

        assertThat(encontrados).containsExactly(livre);
    }

    @Test
    @DisplayName("DEF-012: manutencao que so encosta no intervalo (termina no inicio) nao tira o recurso da busca")
    void manutencaoEncostada_naoBloqueiaBusca() {
        String tipo = "tipo-" + UUID.randomUUID();
        Material material = cadastro.cadastrarMaterial("Notebook", tipo, 2, false);
        cadastro.cadastrarManutencao(material, INICIO.minusHours(2), INICIO, "revisao");
        String competencia = "Competencia-" + UUID.randomUUID();
        Professor professor = cadastro.cadastrarProfessor("Prof. Volta", "VT-" + UUID.randomUUID(),
                List.of(new Competencia(competencia)));
        cadastro.cadastrarManutencao(professor, FIM, FIM.plusHours(2), "curso");

        assertThat(pesquisa.buscarMateriais(tipo, 1, INICIO, FIM)).containsExactly(material);
        assertThat(pesquisa.buscarProfessores(competencia, INICIO, FIM)).containsExactly(professor);
    }
}
