package com.edualoca.api.auditoria;

import com.edualoca.api.seguranca.Perfis;
import com.edualoca.api.seguranca.UsuarioAutenticado;
import com.edualoca.domain.auditoria.AuditoriaEvento;
import com.edualoca.domain.auditoria.AuditoriaService;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Consulta da auditoria (RF-28, RF-29): so leitura e so Administrador. Mostra reservas, cadastro de recursos,
 * manutencoes e usuarios, do evento mais recente para o mais antigo.
 */
@RestController
public class AuditoriaController {

    static final int LIMITE_MAXIMO = 500;
    private static final int TAMANHO_FILTRO = 60;

    private final AuditoriaService auditoria;

    public AuditoriaController(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    public record EventoResposta(UUID id, String entidade, UUID entidadeId, String acao, UUID autorId,
                                 LocalDateTime dataHora) {

        static EventoResposta de(AuditoriaEvento e) {
            return new EventoResposta(e.getId(), e.getEntidade(), e.getEntidadeId(), e.getAcao(), e.getAutorId(),
                    e.getDataHora());
        }
    }

    @GetMapping("/auditoria")
    public List<EventoResposta> listar(@UsuarioAutenticado Usuario usuario,
                                       @RequestParam(required = false) String entidade,
                                       @RequestParam(required = false) String acao,
                                       @RequestParam(defaultValue = "100") int limite) {
        Perfis.exigir(usuario, "Apenas o Administrador pode consultar a auditoria", Perfil.ADMINISTRADOR);
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new RegraNegocioException(400, "Limite inválido: informe de 1 a " + LIMITE_MAXIMO + " eventos");
        }
        if (tamanho(entidade) > TAMANHO_FILTRO || tamanho(acao) > TAMANHO_FILTRO) {
            throw new RegraNegocioException(400, "Filtro de auditoria excede " + TAMANHO_FILTRO + " caracteres");
        }
        return auditoria.listar(entidade, acao, limite).stream().map(EventoResposta::de).toList();
    }

    private static int tamanho(String valor) {
        return valor == null ? 0 : valor.length();
    }
}
