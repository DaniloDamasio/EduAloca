package com.edualoca.domain.comum;

import java.time.LocalDateTime;

/**
 * Comparacao de intervalos de tempo compartilhada pelas regras de reserva e manutencao.
 * Semantica semiaberta [inicio, fim): encostar nao e sobrepor (ver P-03 em bdd-aprovacao-bloqueio.md).
 */
public final class Intervalos {

    private Intervalos() {
    }

    public static boolean sobrepoe(LocalDateTime inicioA, LocalDateTime fimA, LocalDateTime inicioB, LocalDateTime fimB) {
        return inicioA.isBefore(fimB) && inicioB.isBefore(fimA);
    }
}
