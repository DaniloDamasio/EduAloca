package com.edualoca.api.seguranca;

import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;

import java.util.Set;

/** Checagem de perfil na borda, para operacoes cujo servico de dominio nao recebe o ator. */
public final class Perfis {

    private Perfis() {
    }

    public static void exigir(Usuario usuario, String mensagem, Perfil... permitidos) {
        if (!Set.of(permitidos).contains(usuario.getPerfil())) {
            throw new RegraNegocioException(403, mensagem);
        }
    }
}
