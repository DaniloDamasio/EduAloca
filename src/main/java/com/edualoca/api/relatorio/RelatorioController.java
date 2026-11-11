package com.edualoca.api.relatorio;

import com.edualoca.api.seguranca.UsuarioAutenticado;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.relatorio.RelatorioService;
import com.edualoca.domain.usuario.Usuario;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * Relatorio de utilizacao (RF-34, RF-35, RF-36), so leitura. A ordem das checagens e fixa: token (401), perfil
 * (403) e so entao os parametros (400), para que o Solicitante nao receba nada alem do 403. Por isso inicio e fim
 * chegam como texto e sao convertidos aqui, depois da checagem de perfil.
 */
@RestController
public class RelatorioController {

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    @GetMapping("/relatorios/utilizacao")
    public UtilizacaoResposta utilizacao(@UsuarioAutenticado Usuario usuario,
                                         @RequestParam(required = false) String inicio,
                                         @RequestParam(required = false) String fim) {
        relatorioService.validarAcesso(usuario);
        return UtilizacaoResposta.de(relatorioService.gerarUtilizacaoPorPeriodo(usuario, data(inicio), data(fim)));
    }

    private static LocalDateTime data(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new RegraNegocioException(400, "Informe inicio e fim do periodo (aaaa-mm-ddThh:mm)");
        }
        try {
            return LocalDateTime.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new RegraNegocioException(400, "Data do periodo fora do formato aaaa-mm-ddThh:mm");
        }
    }
}
