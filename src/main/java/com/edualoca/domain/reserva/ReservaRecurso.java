package com.edualoca.domain.reserva;

public class ReservaRecurso {

    private final Object recurso;
    private final int quantidade;

    public ReservaRecurso(Object recurso, int quantidade) {
        this.recurso = recurso;
        this.quantidade = quantidade;
    }

    public Object getRecurso() {
        return recurso;
    }

    public int getQuantidade() {
        return quantidade;
    }
}
