package com.edualoca.api.usuario;

import com.edualoca.api.seguranca.Perfis;
import com.edualoca.api.seguranca.TokenService;
import com.edualoca.api.seguranca.UsuarioAutenticado;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import com.edualoca.domain.usuario.UsuarioService;
import com.edualoca.domain.usuario.UsuarioService.ContaUsuario;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Gestao de usuarios (escopo do Administrador). As regras ficam em {@link UsuarioService}; aqui so a conversao do
 * perfil e a revogacao dos tokens da conta inativada. A resposta nunca traz senha nem hash.
 */
@RestController
public class UsuarioController {

    private final UsuarioService usuarios;
    private final TokenService tokens;

    public UsuarioController(UsuarioService usuarios, TokenService tokens) {
        this.usuarios = usuarios;
        this.tokens = tokens;
    }

    public record UsuarioRequisicao(String email, String senha, String perfil, String localizacao) {
    }

    @GetMapping("/usuarios")
    public List<ContaUsuario> listar(@UsuarioAutenticado Usuario usuario) {
        return usuarios.listar(usuario);
    }

    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public ContaUsuario cadastrar(@UsuarioAutenticado Usuario usuario, @RequestBody UsuarioRequisicao requisicao) {
        // Perfil antes do corpo: o 403 nao depende do que foi enviado (nem de um perfil invalido no corpo).
        Perfis.exigir(usuario, "Apenas o Administrador pode gerenciar usuários", Perfil.ADMINISTRADOR);
        return usuarios.cadastrar(usuario, requisicao.email(), requisicao.senha(), perfil(requisicao.perfil()),
                requisicao.localizacao());
    }

    @PostMapping("/usuarios/{id}/inativacao")
    public ContaUsuario inativar(@UsuarioAutenticado Usuario usuario, @PathVariable UUID id) {
        ContaUsuario conta = usuarios.inativar(usuario, id);
        tokens.revogarDoUsuario(id);
        return conta;
    }

    private static Perfil perfil(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Perfil.valueOf(valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException(400, "Perfil inválido: use SOLICITANTE, RESPONSAVEL ou ADMINISTRADOR");
        }
    }
}
