package com.edualoca.persistencia;

import com.edualoca.domain.reserva.Transacao;
import org.springframework.transaction.support.TransactionTemplate;

public class TransacaoJdbc implements Transacao {

    private final TransactionTemplate transacao;

    public TransacaoJdbc(TransactionTemplate transacao) {
        this.transacao = transacao;
    }

    @Override
    public void executar(Runnable trabalho) {
        trabalho.run(); // esqueleto: ainda sem transacao
    }
}
