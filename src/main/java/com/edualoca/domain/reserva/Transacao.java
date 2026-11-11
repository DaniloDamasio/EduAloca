package com.edualoca.domain.reserva;

/**
 * Unidade de trabalho: o que roda dentro de {@link #executar} e confirmado ou desfeito junto. Usada para gravar a
 * mudanca de estado da reserva e o registro de historico na mesma transacao.
 */
@FunctionalInterface
public interface Transacao {

    void executar(Runnable trabalho);

    /** Sem transacao: executa direto (persistencia em memoria). */
    static Transacao direta() {
        return Runnable::run;
    }
}
