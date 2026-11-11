package com.edualoca.domain.reserva;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Historico append-only em memoria; a consulta devolve uma copia imutavel. */
public class HistoricoReservaRepositorioEmMemoria implements HistoricoReservaRepositorio {

    private final Map<UUID, List<RegistroHistoricoReserva>> porReserva = new ConcurrentHashMap<>();

    @Override
    public void registrar(RegistroHistoricoReserva registro) {
        porReserva.computeIfAbsent(registro.reservaId(), id -> new CopyOnWriteArrayList<>()).add(registro);
    }

    @Override
    public List<RegistroHistoricoReserva> listarPorReserva(UUID reservaId) {
        return List.copyOf(porReserva.getOrDefault(reservaId, List.of()));
    }
}
