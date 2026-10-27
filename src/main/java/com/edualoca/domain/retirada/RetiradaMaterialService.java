package com.edualoca.domain.retirada;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.usuario.Usuario;

import java.time.LocalDateTime;
import java.util.UUID;

public class RetiradaMaterialService {

    public RegistroRetirada registrarRetirada(Usuario autor, UUID reservaId, UUID itemId, LocalDateTime dataHora) {
        Banco.seedSeNecessario();
        Reserva reserva = Banco.reservas.get(reservaId);
        if (reserva == null || reserva.getEstado() != EstadoReserva.EM_USO) {
            throw new RegraNegocioException(409, "Retirada so pode ser registrada para reserva EM_USO");
        }
        boolean cicloAberto = Banco.retiradas.values().stream()
                .anyMatch(r -> r.getReservaId().equals(reservaId) && r.getDataDevolucao() == null);
        if (cicloAberto) {
            throw new RegraNegocioException(409, "Ja existe retirada em aberto para este item");
        }
        RegistroRetirada registro = new RegistroRetirada(UUID.randomUUID(), reservaId, autor.getId(), dataHora);
        Banco.retiradas.put(registro.getId(), registro);
        return registro;
    }

    public RegistroRetirada registrarDevolucao(Usuario autor, UUID registroRetiradaId, LocalDateTime dataHora, CondicaoItem condicao) {
        Banco.seedSeNecessario();
        if (condicao == null) {
            throw new RegraNegocioException(400, "Condicao de devolucao e obrigatoria");
        }
        RegistroRetirada registro = Banco.retiradas.get(registroRetiradaId);
        if (registro == null) {
            throw new RegraNegocioException(409, "Nao existe retirada em aberto para este registro");
        }
        registro.setDataDevolucao(dataHora);
        registro.setCondicaoDevolucao(condicao);
        return registro;
    }

    public void sinalizarPendencias(LocalDateTime agora) {
        Banco.seedSeNecessario();
        for (RegistroRetirada registro : Banco.retiradas.values()) {
            if (registro.getDataDevolucao() != null || registro.isPendenciaSinalizada()) {
                continue;
            }
            com.edualoca.domain.reserva.Reserva reserva = Banco.reservas.get(registro.getReservaId());
            if (reserva == null) {
                continue;
            }
            if (agora.isAfter(reserva.getFim())) {
                registro.setPendenciaSinalizada(true);
            }
        }
    }
}
