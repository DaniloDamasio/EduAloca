package com.edualoca.persistencia;

import com.edualoca.domain.recurso.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Mapeamento recurso -> (tipo_recurso, recurso_id) gravado em reserva_recurso; nao depende de banco. */
class ReservaRepositorioJdbcRecursoTest {

    @Test
    @DisplayName("Material e gravado com tipo MATERIAL e o proprio id")
    void material_mapeadoParaTipoEId() {
        UUID id = UUID.randomUUID();
        Material projetor = new Material(id, "Projetor", "audiovisual", 5, false);

        assertEquals("MATERIAL", ReservaRepositorioJdbc.tipo(projetor));
        assertEquals(id, ReservaRepositorioJdbc.id(projetor));
    }

    @Test
    @DisplayName("Tipo de recurso desconhecido e recusado em vez de gravado com tipo errado")
    void recursoDesconhecido_recusado() {
        Object desconhecido = "nao e um recurso";

        IllegalArgumentException erroTipo = assertThrows(IllegalArgumentException.class,
                () -> ReservaRepositorioJdbc.tipo(desconhecido));
        IllegalArgumentException erroId = assertThrows(IllegalArgumentException.class,
                () -> ReservaRepositorioJdbc.id(desconhecido));

        assertEquals("Tipo de recurso nao persistivel: String", erroTipo.getMessage());
        assertEquals("Tipo de recurso nao persistivel: String", erroId.getMessage());
    }
}
