package com.edualoca.domain.reserva;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.Intervalos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Persistencia em memoria sobre {@link Banco#reservas}; comportamento original do sistema. */
public class ReservaRepositorioEmMemoria implements ReservaRepositorio {

    @Override
    public void inserir(Reserva reserva) {
        Banco.reservas.put(reserva.getId(), reserva);
    }

    @Override
    public Reserva buscar(UUID id) {
        return Banco.reservas.get(id);
    }

    @Override
    public List<Reserva> listar() {
        return List.copyOf(Banco.reservas.values());
    }

    @Override
    public void atualizar(Reserva reserva) {
        // A reserva em memoria e a propria instancia alterada; nada a gravar.
    }

    @Override
    public boolean existeConflito(Object recurso, LocalDateTime inicio, LocalDateTime fim, UUID reservaIgnorada) {
        return Banco.reservas.values().stream()
                .filter(r -> !r.getId().equals(reservaIgnorada))
                .filter(r -> r.getEstado().isAtiva())
                .filter(r -> r.getRecursos().stream().anyMatch(rr -> rr.getRecurso() == recurso))
                .anyMatch(r -> Intervalos.sobrepoe(inicio, fim, r.getInicio(), r.getFim()));
    }
}
