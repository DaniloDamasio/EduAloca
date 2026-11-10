package com.edualoca.persistencia;

import com.edualoca.domain.reserva.HistoricoReservaRepositorio;
import com.edualoca.domain.reserva.RegistroHistoricoReserva;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

public class HistoricoReservaRepositorioJdbc implements HistoricoReservaRepositorio {

    private final JdbcTemplate jdbc;

    public HistoricoReservaRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(RegistroHistoricoReserva registro) {
        // esqueleto: ainda nao grava
    }

    @Override
    public List<RegistroHistoricoReserva> listarPorReserva(UUID reservaId) {
        return List.of();
    }
}
