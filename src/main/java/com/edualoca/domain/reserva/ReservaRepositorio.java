package com.edualoca.domain.reserva;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Porta de persistencia das reservas usada por {@link ReservaService}. A implementacao padrao e em memoria
 * ({@link ReservaRepositorioEmMemoria}); com PostgreSQL configurado, a gravacao e a verificacao de conflito
 * passam a acontecer no banco (ver com.edualoca.persistencia).
 */
public interface ReservaRepositorio {

    void inserir(Reserva reserva);

    /** Retorna a reserva ou {@code null} se nao existir. */
    Reserva buscar(UUID id);

    /** Persiste mudancas de estado, intervalo ou justificativa feitas na reserva. */
    void atualizar(Reserva reserva);

    /** Ha reserva ativa do recurso que se sobrepoe a [inicio, fim), ignorando {@code reservaIgnorada}? */
    boolean existeConflito(Object recurso, LocalDateTime inicio, LocalDateTime fim, UUID reservaIgnorada);
}
