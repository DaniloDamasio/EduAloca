package com.edualoca.api.recurso;

import com.edualoca.api.reserva.RecursoResolver;
import com.edualoca.api.seguranca.UsuarioAutenticado;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.recurso.Manutencao;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cadastro de manutencao/bloqueio (RF-21): exclusivo do Administrador (RF-02). */
@RestController
public class ManutencaoController {

    private final CadastroRecursoService cadastroRecursoService;
    private final RecursoResolver recursoResolver;

    public ManutencaoController(CadastroRecursoService cadastroRecursoService, RecursoResolver recursoResolver) {
        this.cadastroRecursoService = cadastroRecursoService;
        this.recursoResolver = recursoResolver;
    }

    @PostMapping("/manutencoes")
    @ResponseStatus(HttpStatus.CREATED)
    public ManutencaoResposta cadastrar(@UsuarioAutenticado Usuario usuario, @RequestBody ManutencaoRequisicao requisicao) {
        if (usuario.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new RegraNegocioException(403, "Apenas o Administrador pode cadastrar manutencao de recursos");
        }
        Object recurso = recursoResolver.resolver(requisicao.tipoRecurso(), requisicao.recursoId());
        Manutencao manutencao = switch (recurso) {
            case Sala sala -> cadastroRecursoService.cadastrarManutencao(sala, requisicao.inicio(), requisicao.fim(), requisicao.motivo());
            case Material material -> cadastroRecursoService.cadastrarManutencao(material, requisicao.inicio(), requisicao.fim(), requisicao.motivo());
            case Professor professor -> cadastroRecursoService.cadastrarManutencao(professor, requisicao.inicio(), requisicao.fim(), requisicao.motivo());
            default -> throw new RegraNegocioException(400, "Tipo de recurso invalido: use SALA, MATERIAL ou PROFESSOR");
        };
        return ManutencaoResposta.de(manutencao);
    }
}
