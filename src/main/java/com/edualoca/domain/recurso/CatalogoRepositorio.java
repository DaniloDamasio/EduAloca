package com.edualoca.domain.recurso;

/**
 * Porta de persistencia do catalogo (salas, professores e materiais). O cache em memoria ({@code Banco}) continua sendo a
 * fonte das leituras e da comparacao de recursos; esta porta grava cada mudanca de cadastro (write-through) para que o
 * catalogo sobreviva a um reinicio no modo PostgreSQL. No modo em memoria a implementacao e {@link #NENHUM}.
 */
public interface CatalogoRepositorio {

    /** Insere ou atualiza a sala, inclusive a inativacao. */
    void salvar(Sala sala);

    /** Insere ou atualiza o professor e substitui as competencias dele. */
    void salvar(Professor professor);

    /** Insere ou atualiza o material, inclusive a inativacao. */
    void salvar(Material material);

    /** Modo em memoria: nada a gravar alem do proprio cache. */
    CatalogoRepositorio NENHUM = new CatalogoRepositorio() {
        @Override
        public void salvar(Sala sala) {
            // memoria: o cache ja e o catalogo
        }

        @Override
        public void salvar(Professor professor) {
            // memoria: o cache ja e o catalogo
        }

        @Override
        public void salvar(Material material) {
            // memoria: o cache ja e o catalogo
        }
    };
}
