package com.edualoca.persistencia;

import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.HistoricoReservaRepositorio;
import com.edualoca.domain.reserva.RegistroHistoricoReserva;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Historico das reservas na tabela {@code reserva_historico}. Usa o mesmo {@code DataSource} da reserva, entao
 * dentro de {@link TransacaoJdbc} o INSERT entra na transacao da mudanca de estado. So INSERT e SELECT: o banco
 * ainda recusa UPDATE/DELETE por trigger (RF-29).
 */
public class HistoricoReservaRepositorioJdbc implements HistoricoReservaRepositorio {

    private final JdbcTemplate jdbc;

    public HistoricoReservaRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(RegistroHistoricoReserva registro) {
        jdbc.update("""
                        INSERT INTO reserva_historico (id, reserva_id, usuario_id, instante, estado_anterior, estado_novo, motivo)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""",
                registro.id(), registro.reservaId(), registro.usuarioId(),
                registro.instante().atOffset(ZoneOffset.UTC),
                registro.estadoAnterior() == null ? null : registro.estadoAnterior().name(),
                registro.estadoNovo().name(), registro.motivo());
    }

    @Override
    public List<RegistroHistoricoReserva> listarPorReserva(UUID reservaId) {
        return List.copyOf(jdbc.query("""
                        SELECT id, reserva_id, usuario_id, instante, estado_anterior, estado_novo, motivo
                          FROM reserva_historico WHERE reserva_id = ? ORDER BY sequencia""",
                HistoricoReservaRepositorioJdbc::mapear, reservaId));
    }

    private static RegistroHistoricoReserva mapear(ResultSet rs, int linha) throws SQLException {
        String anterior = rs.getString("estado_anterior");
        return new RegistroHistoricoReserva(rs.getObject("id", UUID.class), rs.getObject("reserva_id", UUID.class),
                rs.getObject("usuario_id", UUID.class), rs.getObject("instante", OffsetDateTime.class).toInstant(),
                anterior == null ? null : EstadoReserva.valueOf(anterior), EstadoReserva.valueOf(rs.getString("estado_novo")),
                rs.getString("motivo"));
    }
}
