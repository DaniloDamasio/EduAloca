package com.edualoca.domain.relatorio;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;

import java.time.LocalDateTime;

public class RelatorioService {

    public RelatorioUtilizacao gerarRelatorioUtilizacao(Usuario autor, LocalDateTime inicio, LocalDateTime fim) {
        Banco.seedSeNecessario();
        validarAcesso(autor);
        validarIntervalo(inicio, fim);
        var registros = Banco.reservas.values().stream()
                .filter(r -> r.getEstado() == EstadoReserva.CONCLUIDA || r.getEstado() == EstadoReserva.EM_USO)
                .filter(r -> !r.getInicio().isBefore(inicio) && !r.getFim().isAfter(fim))
                .toList();
        return new RelatorioUtilizacao(registros);
    }

    public long contarRejeicoesPorSobreposicao(Usuario autor, LocalDateTime inicio, LocalDateTime fim) {
        Banco.seedSeNecessario();
        validarAcesso(autor);
        validarIntervalo(inicio, fim);
        return Banco.auditoria.stream()
                .filter(e -> "Reserva".equals(e.getEntidade()))
                .filter(e -> "REJEICAO_SOBREPOSICAO".equals(e.getAcao()))
                .filter(e -> !e.getDataHora().isBefore(inicio) && !e.getDataHora().isAfter(fim))
                .count();
    }

    private void validarAcesso(Usuario autor) {
        if (autor.getPerfil() != Perfil.RESPONSAVEL && autor.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new RegraNegocioException(403, "Apenas Responsavel ou Administrador podem acessar relatorios");
        }
    }

    private void validarIntervalo(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            throw new RegraNegocioException(400, "Intervalo de relatorio invalido: fim deve ser posterior ao inicio");
        }
    }
}
