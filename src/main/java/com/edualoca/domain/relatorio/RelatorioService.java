package com.edualoca.domain.relatorio;

import com.edualoca.domain.usuario.Usuario;

import java.time.LocalDateTime;

public class RelatorioService {

    public RelatorioUtilizacao gerarRelatorioUtilizacao(Usuario autor, LocalDateTime inicio, LocalDateTime fim) {
        throw new UnsupportedOperationException("relatorio de utilizacao nao implementado");
    }

    public long contarRejeicoesPorSobreposicao(Usuario autor, LocalDateTime inicio, LocalDateTime fim) {
        throw new UnsupportedOperationException("relatorio de rejeicoes nao implementado");
    }
}
