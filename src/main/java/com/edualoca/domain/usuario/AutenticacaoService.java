package com.edualoca.domain.usuario;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;

import java.util.UUID;

public class AutenticacaoService {

    public Usuario autenticar(String email, String senha) {
        Banco.seedSeNecessario();
        String armazenado = email == null ? null : Banco.credenciais.get(email);
        // confere() gasta o mesmo custo quando a conta nao existe: a resposta e o tempo nao revelam o e-mail.
        if (!SenhaHasher.confere(senha, armazenado)) {
            throw new RegraNegocioException(401, "Credenciais invalidas");
        }
        UUID id = Banco.usuarioIdPorEmail.computeIfAbsent(email, e -> UUID.randomUUID());
        Perfil perfil = Banco.perfilPorEmail.getOrDefault(email, Perfil.SOLICITANTE);
        return new Usuario(id, email, armazenado, perfil);
    }

    public void autorizar(Usuario usuario, Perfil perfilExigido) {
        if (usuario == null || usuario.getPerfil() != perfilExigido) {
            throw new RegraNegocioException(403, "Perfil nao autorizado para esta operacao");
        }
    }
}
