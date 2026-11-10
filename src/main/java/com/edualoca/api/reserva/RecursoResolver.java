package com.edualoca.api.reserva;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/** Converte (tipo, id) recebidos na borda no objeto de dominio, recusando tipo ou id desconhecido com 400. */
@Component
public class RecursoResolver {

    public Object resolver(String tipo, UUID id) {
        if (tipo == null || id == null) {
            throw new RegraNegocioException(400, "Informe o tipo (SALA, MATERIAL ou PROFESSOR) e o id de cada recurso");
        }
        Object recurso = switch (tipo.toUpperCase(Locale.ROOT)) {
            case "SALA" -> Banco.salas.get(id);
            case "MATERIAL" -> Banco.materiais.get(id);
            case "PROFESSOR" -> Banco.professores.get(id);
            default -> throw new RegraNegocioException(400, "Tipo de recurso invalido: use SALA, MATERIAL ou PROFESSOR");
        };
        if (recurso == null) {
            throw new RegraNegocioException(400, "Recurso informado nao existe");
        }
        return recurso;
    }
}
