package com.edualoca.api.retirada;

import com.edualoca.api.seguranca.Perfis;
import com.edualoca.api.seguranca.UsuarioAutenticado;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.retirada.CondicaoItem;
import com.edualoca.domain.retirada.RegistroRetirada;
import com.edualoca.domain.retirada.RetiradaMaterialService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Retirada e devolucao de material (RF-24, RF-25). So Responsavel e Administrador; o Responsavel so atua nas reservas
 * que pode decidir (mesma regra de {@link ReservaService#listar}). Data e autor vem do servidor e do token.
 */
@RestController
public class RetiradaController {

    private static final String SO_RESPONSAVEL = "Apenas Responsavel ou Administrador registram retirada e devolucao";

    private final RetiradaMaterialService retiradas;
    private final ReservaService reservas;
    private final Clock clock;

    public RetiradaController(RetiradaMaterialService retiradas, ReservaService reservas, Clock clock) {
        this.retiradas = retiradas;
        this.reservas = reservas;
        this.clock = clock;
    }

    public record RetiradaRequisicao(UUID materialId) {
    }

    public record DevolucaoRequisicao(String condicao) {
    }

    public record RetiradaResposta(UUID id, UUID reservaId, UUID materialId, UUID autorId, LocalDateTime dataRetirada,
                                   LocalDateTime dataDevolucao, String condicaoDevolucao) {

        static RetiradaResposta de(RegistroRetirada r) {
            return new RetiradaResposta(r.getId(), r.getReservaId(), r.getItemId(), r.getAutorId(), r.getDataRetirada(),
                    r.getDataDevolucao(), r.getCondicaoDevolucao() == null ? null : r.getCondicaoDevolucao().name());
        }
    }

    @GetMapping("/reservas/{id}/retiradas")
    public List<RetiradaResposta> listar(@UsuarioAutenticado Usuario usuario, @PathVariable UUID id) {
        exigirCompetencia(usuario, id);
        return retiradas.listarPorReserva(id).stream().map(RetiradaResposta::de).toList();
    }

    @PostMapping("/reservas/{id}/retiradas")
    @ResponseStatus(HttpStatus.CREATED)
    public RetiradaResposta registrarRetirada(@UsuarioAutenticado Usuario usuario, @PathVariable UUID id,
                                              @RequestBody RetiradaRequisicao requisicao) {
        exigirCompetencia(usuario, id);
        return RetiradaResposta.de(retiradas.registrarRetirada(usuario, id, requisicao.materialId(), LocalDateTime.now(clock)));
    }

    @PostMapping("/retiradas/{id}/devolucao")
    public RetiradaResposta registrarDevolucao(@UsuarioAutenticado Usuario usuario, @PathVariable UUID id,
                                               @RequestBody DevolucaoRequisicao requisicao) {
        Perfis.exigir(usuario, SO_RESPONSAVEL, Perfil.RESPONSAVEL, Perfil.ADMINISTRADOR);
        CondicaoItem condicao = condicao(requisicao.condicao());
        RegistroRetirada registro = retiradas.buscar(id);
        if (registro == null) {
            throw new RegraNegocioException(409, "Nao existe retirada em aberto para este registro");
        }
        exigirCompetencia(usuario, registro.getReservaId());
        return RetiradaResposta.de(retiradas.registrarDevolucao(usuario, id, LocalDateTime.now(clock), condicao));
    }

    /** Perfil antes da reserva; reserva inexistente ou de outro local recebe o mesmo 403 (nao revela ids). */
    private void exigirCompetencia(Usuario usuario, UUID reservaId) {
        Perfis.exigir(usuario, SO_RESPONSAVEL, Perfil.RESPONSAVEL, Perfil.ADMINISTRADOR);
        boolean visivel = reservas.listar(usuario).stream().anyMatch(r -> r.getId().equals(reservaId));
        if (!visivel) {
            throw new RegraNegocioException(403, "Reserva fora da sua responsabilidade ou inexistente");
        }
    }

    private static CondicaoItem condicao(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new RegraNegocioException(400, "Condicao de devolucao e obrigatoria: use INTEGRO ou DANIFICADO");
        }
        try {
            return CondicaoItem.valueOf(valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException(400, "Condicao de devolucao invalida: use INTEGRO ou DANIFICADO");
        }
    }
}
