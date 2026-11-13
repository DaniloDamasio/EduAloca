package com.edualoca.domain.usuario;

import com.edualoca.domain.auditoria.AuditoriaService;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gestao de usuarios pelo Administrador (escopo do enunciado: "Administrador gerencia ... usuarios"; RF-28 audita a
 * alteracao de cadastro de usuario). Cadastra conta com perfil e, para Responsavel, o local sob sua responsabilidade;
 * lista as contas; inativa conta (o login passa a falhar). A senha e guardada so como hash com sal (RNF-05).
 */
public class UsuarioService {

    public static final int SENHA_MINIMA = 8;
    public static final int TEXTO_MAXIMO = 120;

    private final Clock clock;

    public UsuarioService(Clock clock) {
        this.clock = clock;
    }

    /** Conta como o Administrador a ve; nunca inclui a senha nem o hash. */
    public record ContaUsuario(UUID id, String email, Perfil perfil, boolean ativo, String localizacao) {
    }

    public List<ContaUsuario> listar(Usuario ator) {
        exigirAdministrador(ator);
        Banco.seedSeNecessario();
        Set<String> emails = new HashSet<>(Banco.credenciais.keySet());
        emails.addAll(Banco.usuariosInativos);
        return emails.stream().sorted(Comparator.naturalOrder()).map(UsuarioService::conta).toList();
    }

    public ContaUsuario cadastrar(Usuario ator, String email, String senha, Perfil perfil, String localizacao) {
        exigirAdministrador(ator);
        Banco.seedSeNecessario();
        String emailNormalizado = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (emailNormalizado.length() > TEXTO_MAXIMO || !emailValido(emailNormalizado)) {
            throw new RegraNegocioException(400, "E-mail inválido: informe um endereço como nome@example.com");
        }
        if (senha == null || senha.length() < SENHA_MINIMA || senha.length() > TEXTO_MAXIMO) {
            throw new RegraNegocioException(400, "Senha deve ter entre " + SENHA_MINIMA + " e " + TEXTO_MAXIMO + " caracteres");
        }
        if (perfil == null) {
            throw new RegraNegocioException(400, "Perfil obrigatório: use SOLICITANTE, RESPONSAVEL ou ADMINISTRADOR");
        }
        String local = localizacao == null || localizacao.isBlank() ? null : localizacao.strip();
        if (perfil == Perfil.RESPONSAVEL && local == null) {
            throw new RegraNegocioException(400, "Responsável precisa do local sob sua responsabilidade (ex.: Bloco A)");
        }
        if (local != null && local.length() > TEXTO_MAXIMO) {
            throw new RegraNegocioException(400, "Campo 'localizacao' excede " + TEXTO_MAXIMO + " caracteres");
        }
        synchronized (Banco.credenciais) {
            if (Banco.credenciais.containsKey(emailNormalizado) || Banco.usuariosInativos.contains(emailNormalizado)) {
                throw new RegraNegocioException(409, "E-mail já cadastrado");
            }
            if (perfil == Perfil.RESPONSAVEL && Banco.responsavelPorLocalizacao.containsKey(local)) {
                throw new RegraNegocioException(409, "O local informado já tem um Responsável");
            }
            UUID id = UUID.randomUUID();
            Banco.registrarCredencial(emailNormalizado, senha);
            Banco.usuarioIdPorEmail.put(emailNormalizado, id);
            Banco.perfilPorEmail.put(emailNormalizado, perfil);
            if (perfil == Perfil.RESPONSAVEL) {
                Banco.responsavelPorLocalizacao.put(local, id);
            }
            AuditoriaService.registrar("Usuario", id, "CRIACAO", ator.getId(), LocalDateTime.now(clock));
            return conta(emailNormalizado);
        }
    }

    /**
     * Inativa a conta: a credencial e apagada (o login responde 401 como para e-mail inexistente) e o Responsavel deixa
     * de responder pelo local. O Administrador nao inativa a propria conta, para nao ficar sem acesso.
     */
    public ContaUsuario inativar(Usuario ator, UUID usuarioId) {
        exigirAdministrador(ator);
        Banco.seedSeNecessario();
        if (ator.getId().equals(usuarioId)) {
            throw new RegraNegocioException(409, "O Administrador não pode inativar a própria conta");
        }
        synchronized (Banco.credenciais) {
            String email = Banco.credenciais.keySet().stream()
                    .filter(e -> usuarioId.equals(Banco.usuarioIdPorEmail.get(e)))
                    .findFirst()
                    .orElseThrow(() -> new RegraNegocioException(404, "Usuário ativo inexistente"));
            Banco.credenciais.remove(email);
            Banco.usuariosInativos.add(email);
            Banco.responsavelPorLocalizacao.values().removeIf(usuarioId::equals);
            AuditoriaService.registrar("Usuario", usuarioId, "INATIVACAO", ator.getId(), LocalDateTime.now(clock));
            return conta(email);
        }
    }

    /**
     * Um unico "@", parte local nao vazia, dominio com ponto que nao esta na ponta e nenhum espaco. Sem regex, para nao
     * haver backtracking super-linear com entrada maliciosa (Sonar java:S8786).
     */
    static boolean emailValido(String email) {
        int arroba = email.indexOf('@');
        if (arroba <= 0 || arroba != email.lastIndexOf('@') || email.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        String dominio = email.substring(arroba + 1);
        int ponto = dominio.lastIndexOf('.');
        return ponto > 0 && ponto < dominio.length() - 1;
    }

    private static ContaUsuario conta(String email) {
        // Mesmo id que o login atribui (AutenticacaoService): contas de seed sem id ganham um na primeira consulta.
        UUID id = Banco.usuarioIdPorEmail.computeIfAbsent(email, e -> UUID.randomUUID());
        String local = Banco.responsavelPorLocalizacao.entrySet().stream()
                .filter(entrada -> id.equals(entrada.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .findFirst()
                .orElse(null);
        boolean ativo = Banco.credenciais.containsKey(email) && !Banco.usuariosInativos.contains(email);
        return new ContaUsuario(id, email, Banco.perfilPorEmail.getOrDefault(email, Perfil.SOLICITANTE), ativo, local);
    }

    private static void exigirAdministrador(Usuario ator) {
        if (ator == null || ator.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new RegraNegocioException(403, "Apenas o Administrador pode gerenciar usuários");
        }
    }
}
