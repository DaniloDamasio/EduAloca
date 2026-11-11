package com.edualoca.api.reserva;

import com.edualoca.api.seguranca.UsuarioAutenticado;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.usuario.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Borda HTTP das reservas. O ator e sempre o usuario do token; a autorizacao por perfil e por dono
 * (RF-02, RF-03, RF-19) e decidida em {@link ReservaService}, sem regra de negocio aqui.
 */
@RestController
@RequestMapping("/reservas")
public class ReservaController {

    private static final int MAX_RECURSOS_POR_RESERVA = 50;

    private final ReservaService reservaService;
    private final RecursoResolver recursoResolver;

    public ReservaController(ReservaService reservaService, RecursoResolver recursoResolver) {
        this.reservaService = reservaService;
        this.recursoResolver = recursoResolver;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResposta criar(@UsuarioAutenticado Usuario usuario, @RequestBody CriarReservaRequisicao requisicao) {
        return ReservaResposta.de(reservaService.criar(usuario, requisicao.inicio(), requisicao.fim(),
                requisicao.finalidade(), recursos(requisicao.recursos())));
    }

    @GetMapping("/{id}")
    public ReservaResposta consultar(@UsuarioAutenticado Usuario usuario, @PathVariable UUID id) {
        return ReservaResposta.de(reservaService.consultar(usuario, id));
    }

    /** RF-13: so o dono altera a propria reserva; intervalo, posse, estado e conflito sao checados no servico. */
    @PutMapping("/{id}")
    public ReservaResposta alterar(@UsuarioAutenticado Usuario usuario, @PathVariable UUID id,
                                   @RequestBody AlterarReservaRequisicao requisicao) {
        return ReservaResposta.de(reservaService.alterar(usuario, id, requisicao.inicio(), requisicao.fim()));
    }

    /**
     * RF-27/RF-29: historico auditavel, so leitura. A autorizacao (Responsavel e Administrador) fica em
     * {@link ReservaService#consultarHistorico}; sem rota de escrita, PUT/PATCH/DELETE/POST respondem 405.
     */
    @GetMapping("/{id}/historico")
    public List<RegistroHistoricoResposta> historico(@UsuarioAutenticado Usuario usuario, @PathVariable UUID id) {
        return reservaService.consultarHistorico(usuario, id).stream().map(RegistroHistoricoResposta::de).toList();
    }

    @PostMapping("/{id}/aprovacao")
    public ReservaResposta aprovar(@UsuarioAutenticado Usuario usuario, @PathVariable UUID id) {
        return ReservaResposta.de(reservaService.aprovar(usuario, id));
    }

    @PostMapping("/{id}/rejeicao")
    public ReservaResposta rejeitar(@UsuarioAutenticado Usuario usuario, @PathVariable UUID id,
                                    @RequestBody JustificativaRequisicao requisicao) {
        return ReservaResposta.de(reservaService.rejeitar(usuario, id, requisicao.justificativa()));
    }

    @PostMapping("/{id}/cancelamento")
    public ReservaResposta cancelar(@UsuarioAutenticado Usuario usuario, @PathVariable UUID id,
                                    @RequestBody MotivoRequisicao requisicao) {
        return ReservaResposta.de(reservaService.cancelar(usuario, id, requisicao.motivo()));
    }

    /** RF-30: a regra de dominio responde 403 para reserva iniciada e 405 para exclusao fisica nao exposta. */
    @DeleteMapping("/{id}")
    public void excluir(@UsuarioAutenticado Usuario usuario, @PathVariable UUID id) {
        reservaService.excluir(usuario, id);
    }

    private List<ReservaRecurso> recursos(List<CriarReservaRequisicao.RecursoRequisicao> recursos) {
        if (recursos == null) {
            return List.of();
        }
        if (recursos.size() > MAX_RECURSOS_POR_RESERVA) {
            throw new RegraNegocioException(400, "Uma reserva aceita no maximo " + MAX_RECURSOS_POR_RESERVA + " recursos");
        }
        return recursos.stream().map(this::recurso).toList();
    }

    private ReservaRecurso recurso(CriarReservaRequisicao.RecursoRequisicao r) {
        if (r == null) {
            throw new RegraNegocioException(400, "Recurso da reserva nao pode ser vazio");
        }
        int quantidade = r.quantidade() == null ? 1 : r.quantidade();
        if (quantidade < 1) {
            throw new RegraNegocioException(400, "Quantidade do recurso deve ser maior que zero");
        }
        return new ReservaRecurso(recursoResolver.resolver(r.tipo(), r.id()), quantidade);
    }
}
