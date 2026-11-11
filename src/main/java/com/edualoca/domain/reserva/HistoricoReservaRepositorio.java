package com.edualoca.domain.reserva;

import java.util.List;
import java.util.UUID;

/**
 * Porta do historico auditavel das reservas. So inclusao e consulta: nao existe operacao de alterar ou apagar
 * um registro (RF-29).
 */
public interface HistoricoReservaRepositorio {

    void registrar(RegistroHistoricoReserva registro);

    /** Registros da reserva em ordem de gravacao, numa lista que nao pode ser modificada. */
    List<RegistroHistoricoReserva> listarPorReserva(UUID reservaId);
}
