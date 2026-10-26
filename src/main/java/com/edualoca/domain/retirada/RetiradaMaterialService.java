package com.edualoca.domain.retirada;

import com.edualoca.domain.usuario.Usuario;

import java.time.LocalDateTime;
import java.util.UUID;

public class RetiradaMaterialService {

    public RegistroRetirada registrarRetirada(Usuario autor, UUID reservaId, UUID itemId, LocalDateTime dataHora) {
        throw new UnsupportedOperationException("registro de retirada nao implementado");
    }

    public RegistroRetirada registrarDevolucao(Usuario autor, UUID registroRetiradaId, LocalDateTime dataHora, CondicaoItem condicao) {
        throw new UnsupportedOperationException("registro de devolucao nao implementado");
    }

    public void sinalizarPendencias(LocalDateTime agora) {
        throw new UnsupportedOperationException("sinalizacao de pendencias nao implementada");
    }
}
