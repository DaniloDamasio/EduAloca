package com.edualoca.domain.relatorio;

import java.util.List;

public class RelatorioUtilizacao {

    private final List<?> registros;

    public RelatorioUtilizacao(List<?> registros) {
        this.registros = registros;
    }

    public List<?> getRegistros() {
        return registros;
    }
}
