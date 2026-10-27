package com.edualoca.domain.usuario;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;

import java.util.UUID;

public class AutenticacaoService {

    public Usuario autenticar(String email, String senha) {
        Banco.seedSeNecessario();
        String senhaCadastrada = Banco.credenciais.get(email);
        if (senhaCadastrada == null || !senhaCadastrada.equals(senha)) {
            throw new RegraNegocioException(401, "Credenciais invalidas");
        }
        UUID id = Banco.usuarioIdPorEmail.computeIfAbsent(email, e -> UUID.randomUUID());
        return new Usuario(id, email, SenhaHasher.hash(senhaCadastrada), Perfil.SOLICITANTE);
    }

    public void autorizar(Usuario usuario, Perfil perfilExigido) {
        if (usuario == null || usuario.getPerfil() != perfilExigido) {
            throw new RegraNegocioException(403, "Perfil nao autorizado para esta operacao");
        }
    }
}
