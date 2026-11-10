package com.edualoca.domain.reserva;

import java.util.List;
import java.util.UUID;

public class HistoricoReservaRepositorioEmMemoria implements HistoricoReservaRepositorio {

    @Override
    public void registrar(RegistroHistoricoReserva registro) {
        // esqueleto: ainda nao grava
    }

    @Override
    public List<RegistroHistoricoReserva> listarPorReserva(UUID reservaId) {
        return new java.util.ArrayList<>();
    }
}
