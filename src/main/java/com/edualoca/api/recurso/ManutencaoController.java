package com.edualoca.api.recurso;

import com.edualoca.api.reserva.RecursoResolver;
import com.edualoca.api.seguranca.Perfis;
import com.edualoca.api.seguranca.UsuarioAutenticado;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.recurso.Manutencao;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Cadastro e consulta de manutencao/bloqueio (RF-21, RF-23): exclusivo do Administrador (RF-02). */
@RestController
public class ManutencaoController {

    private static final String SO_ADMIN = "Apenas o Administrador pode cadastrar manutenção de recursos";
    private static final int MAX_MOTIVO = 120;

    private final CadastroRecursoService cadastroRecursoService;
    private final ReservaService reservaService;
    private final RecursoResolver recursoResolver;

    public ManutencaoController(CadastroRecursoService cadastroRecursoService, ReservaService reservaService,
                                RecursoResolver recursoResolver) {
        this.cadastroRecursoService = cadastroRecursoService;
        this.reservaService = reservaService;
        this.recursoResolver = recursoResolver;
    }

    /** Manutencao com o recurso identificado, para a lista do Administrador. */
    public record ManutencaoListada(UUID id, String tipoRecurso, UUID recursoId, String recursoNome,
                                    LocalDateTime inicio, LocalDateTime fim, String motivo) {

        static ManutencaoListada de(Manutencao m) {
            return switch (m.getRecurso()) {
                case Sala s -> new ManutencaoListada(m.getId(), "SALA", s.getId(), s.getNome(), m.getInicio(), m.getFim(), m.getMotivo());
                case Material x -> new ManutencaoListada(m.getId(), "MATERIAL", x.getId(), x.getNome(), m.getInicio(), m.getFim(), m.getMotivo());
                case Professor p -> new ManutencaoListada(m.getId(), "PROFESSOR", p.getId(), p.getNome(), m.getInicio(), m.getFim(), m.getMotivo());
                default -> new ManutencaoListada(m.getId(), "DESCONHECIDO", null, null, m.getInicio(), m.getFim(), m.getMotivo());
            };
        }
    }

    @GetMapping("/manutencoes")
    public List<ManutencaoListada> listar(@UsuarioAutenticado Usuario usuario) {
        Perfis.exigir(usuario, "Apenas o Administrador pode consultar as manutenções", Perfil.ADMINISTRADOR);
        return cadastroRecursoService.listarManutencoes().stream().map(ManutencaoListada::de).toList();
    }

    @PostMapping("/manutencoes/{id}/encerramento")
    public ManutencaoListada encerrar(@UsuarioAutenticado Usuario usuario, @PathVariable UUID id) {
        Perfis.exigir(usuario, "Apenas o Administrador pode encerrar manutenção", Perfil.ADMINISTRADOR);
        return ManutencaoListada.de(cadastroRecursoService.por(usuario).encerrarManutencao(id));
    }

    @PostMapping("/manutencoes")
    @ResponseStatus(HttpStatus.CREATED)
    public ManutencaoResposta cadastrar(@UsuarioAutenticado Usuario usuario, @RequestBody ManutencaoRequisicao requisicao) {
        Perfis.exigir(usuario, SO_ADMIN, Perfil.ADMINISTRADOR);
        if (requisicao.motivo() != null && requisicao.motivo().strip().length() > MAX_MOTIVO) {
            throw new RegraNegocioException(400, "Campo 'motivo' excede " + MAX_MOTIVO + " caracteres");
        }
        Object recurso = recursoResolver.resolver(requisicao.tipoRecurso(), requisicao.recursoId());
        if (Boolean.TRUE.equals(requisicao.confirmarColisao())) {
            liberarReservasAprovadas(usuario, recurso, requisicao);
        }
        Manutencao manutencao = switch (recurso) {
            case Sala sala -> cadastroRecursoService.por(usuario).cadastrarManutencao(sala, requisicao.inicio(), requisicao.fim(), requisicao.motivo());
            case Material material -> cadastroRecursoService.por(usuario).cadastrarManutencao(material, requisicao.inicio(), requisicao.fim(), requisicao.motivo());
            case Professor professor -> cadastroRecursoService.por(usuario).cadastrarManutencao(professor, requisicao.inicio(), requisicao.fim(), requisicao.motivo());
            default -> throw new RegraNegocioException(400, "Tipo de recurso inválido: use SALA, MATERIAL ou PROFESSOR");
        };
        return ManutencaoResposta.de(manutencao);
    }

    /**
     * RF-23 com confirmacao: se alguma reserva colidente ja esta EM_USO, nada e cancelado (409). Senao, cada reserva
     * APROVADA colidente e cancelada (historico, auditoria e notificacao ao Solicitante) antes do cadastro.
     */
    private void liberarReservasAprovadas(Usuario administrador, Object recurso, ManutencaoRequisicao requisicao) {
        List<Reserva> colidentes = cadastroRecursoService.reservasQueColidemComManutencao(
                recurso, requisicao.inicio(), requisicao.fim());
        if (colidentes.stream().anyMatch(r -> r.getEstado() == EstadoReserva.EM_USO)) {
            throw new RegraNegocioException(409, "Manutenção colide com reserva em uso; aguarde a conclusão do uso");
        }
        for (Reserva reserva : colidentes) {
            reservaService.cancelarPorManutencao(administrador, reserva.getId(), requisicao.motivo());
        }
    }
}
