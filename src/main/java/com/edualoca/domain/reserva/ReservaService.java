package com.edualoca.domain.reserva;

import com.edualoca.domain.usuario.Usuario;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ReservaService {

    public Reserva criar(Usuario solicitante, LocalDateTime inicio, LocalDateTime fim,
                          String finalidade, List<ReservaRecurso> recursos) {
        throw new UnsupportedOperationException("criacao de reserva nao implementada");
    }

    public Reserva alterar(Usuario autor, UUID reservaId, LocalDateTime novoInicio, LocalDateTime novoFim) {
        throw new UnsupportedOperationException("alteracao de reserva nao implementada");
    }

    public Reserva cancelar(Usuario autor, UUID reservaId, String motivo) {
        throw new UnsupportedOperationException("cancelamento de reserva nao implementado");
    }

    public Reserva aprovar(Usuario responsavel, UUID reservaId) {
        throw new UnsupportedOperationException("aprovacao de reserva nao implementada");
    }

    public Reserva rejeitar(Usuario responsavel, UUID reservaId, String justificativa) {
        throw new UnsupportedOperationException("rejeicao de reserva nao implementada");
    }

    public Reserva consultar(Usuario autor, UUID reservaId) {
        throw new UnsupportedOperationException("consulta de reserva nao implementada");
    }

    public void excluir(Usuario autor, UUID reservaId) {
        throw new UnsupportedOperationException("exclusao de reserva nao implementada");
    }
}
