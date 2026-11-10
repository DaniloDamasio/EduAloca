package com.edualoca.domain.reserva;

public enum EstadoReserva {
    SOLICITADA,
    APROVADA,
    EM_USO,
    CONCLUIDA,
    REJEITADA,
    CANCELADA,
    NAO_COMPARECEU;

    /** Estados que ocupam o recurso no intervalo da reserva. */
    public boolean isAtiva() {
        return this == SOLICITADA || this == APROVADA || this == EM_USO;
    }
}
