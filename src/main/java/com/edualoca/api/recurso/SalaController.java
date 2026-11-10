package com.edualoca.api.recurso;

import com.edualoca.api.seguranca.UsuarioAutenticado;
import com.edualoca.domain.recurso.PesquisaRecursoService;
import com.edualoca.domain.usuario.Usuario;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** Busca de salas disponiveis (RF-08, RF-11): exclui sala com reserva ativa sobreposta ou em manutencao. */
@RestController
public class SalaController {

    private final PesquisaRecursoService pesquisaRecursoService;

    public SalaController(PesquisaRecursoService pesquisaRecursoService) {
        this.pesquisaRecursoService = pesquisaRecursoService;
    }

    @GetMapping("/salas/disponiveis")
    public List<SalaResposta> disponiveis(@UsuarioAutenticado Usuario usuario,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim,
                                          @RequestParam(required = false) String tipo,
                                          @RequestParam(defaultValue = "0") int capacidadeMinima,
                                          @RequestParam(required = false) String localizacao) {
        return pesquisaRecursoService.buscarSalas(tipo, capacidadeMinima, localizacao, inicio, fim).stream()
                .map(SalaResposta::de)
                .toList();
    }
}
