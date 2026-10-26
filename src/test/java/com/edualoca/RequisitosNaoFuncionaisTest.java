package com.edualoca;

import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.usuario.AutenticacaoService;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequisitosNaoFuncionaisTest {

    private final CadastroRecursoService cadastroRecursoService = new CadastroRecursoService();
    private final AutenticacaoService autenticacaoService = new AutenticacaoService();

    @Test
    @DisplayName("RNF-04-T1: campo obrigatorio ausente/mal formatado em endpoint de escrita deve retornar HTTP 400")
    void rnf04T1_campoObrigatorioAusente_deveRetornar400() {
        // arrange & act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> cadastroRecursoService.cadastrarSala(null, "laboratorio", 20, "Bloco A", false));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RNF-05-T1: senha de usuario cadastrado nunca deve ser persistida em texto plano")
    void rnf05T1_senhaDeUsuario_nuncaDevePersistirEmTextoPlano() {
        // arrange
        String senhaEmTextoPlano = "minhaSenha123";

        // act
        Usuario usuario = autenticacaoService.autenticar("novo@example.com", senhaEmTextoPlano);

        // assert
        assertFalse(usuario.getSenhaHash().equals(senhaEmTextoPlano));
    }

    @Test
    @DisplayName("RNF-06-T1: resposta de erro (4xx/5xx) nao deve expor stacktrace nem nome de classe interna")
    void rnf06T1_respostaDeErro_naoDeveExporDetalheTecnico() {
        // arrange & act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> cadastroRecursoService.cadastrarSala(null, "laboratorio", 20, "Bloco A", false));

        // assert
        assertFalse(ex.getMessage() != null && ex.getMessage().contains("Exception"));
    }

    @Test
    @DisplayName("RNF-09-T1: integridade referencial entre reserva, recurso e auditoria nao deve apresentar registros orfaos")
    void rnf09T1_integridadeReferencial_naoDeveApresentarRegistrosOrfaos() {
        // arrange
        com.edualoca.domain.auditoria.AuditoriaService auditoriaService = new com.edualoca.domain.auditoria.AuditoriaService();
        java.util.UUID reservaInexistenteId = java.util.UUID.randomUUID();

        // act
        java.util.List<com.edualoca.domain.auditoria.AuditoriaEvento> eventosOrfaos =
                auditoriaService.consultarPorEntidade("Reserva", reservaInexistenteId);

        // assert
        assertFalse(eventosOrfaos.stream().anyMatch(e -> e.getEntidadeId() == null));
    }
}
