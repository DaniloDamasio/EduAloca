package com.edualoca.persistencia;

import com.edualoca.domain.reserva.Transacao;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transacao de banco para o trabalho do dominio. Os repositorios JDBC que usam o mesmo {@code DataSource}
 * participam dela (propagacao REQUIRED); qualquer excecao desfaz todas as escritas.
 */
public class TransacaoJdbc implements Transacao {

    private final TransactionTemplate transacao;

    public TransacaoJdbc(TransactionTemplate transacao) {
        this.transacao = transacao;
    }

    @Override
    public void executar(Runnable trabalho) {
        transacao.executeWithoutResult(status -> trabalho.run());
    }
}
