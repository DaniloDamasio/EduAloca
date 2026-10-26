package com.edualoca;

import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.usuario.AutenticacaoService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutenticacaoAutorizacaoTest {

    private final AutenticacaoService autenticacaoService = new AutenticacaoService();
    private final ReservaService reservaService = new ReservaService();

    @Test
    @DisplayName("RF-01-T1: login com credenciais validas deve autenticar o usuario")
    void rf01T1_credenciaisValidas_deveAutenticar() {
        // arrange
        String email = "usuario@example.com";
        String senha = "senhaValida123";

        // act
        Usuario usuario = autenticacaoService.autenticar(email, senha);

        // assert
        assertNotNull(usuario);
    }

    @Test
    @DisplayName("RF-01-T2: senha incorreta deve retornar HTTP 401 sem indicar o campo errado")
    void rf01T2_senhaIncorreta_deveRetornar401() {
        // arrange
        String email = "usuario@example.com";
        String senhaErrada = "senhaErrada";

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> autenticacaoService.autenticar(email, senhaErrada));

        // assert
        assertEquals(401, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-01-T3 [gap Q6, assumindo resposta identica a senha incorreta]: email inexistente deve retornar HTTP 401 generico")
    void rf01T3_emailInexistente_deveRetornar401Generico() {
        // arrange
        String emailInexistente = "naoexiste@example.com";
        String qualquerSenha = "qualquer";

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> autenticacaoService.autenticar(emailInexistente, qualquerSenha));

        // assert
        assertEquals(401, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-02-T1: usuario ADMINISTRADOR deve ser autorizado a acessar endpoint de cadastro de sala")
    void rf02T1_administrador_deveSerAutorizado() {
        // arrange
        Usuario admin = new Usuario(UUID.randomUUID(), "admin@example.com", "hash", Perfil.ADMINISTRADOR);

        // act & assert
        autenticacaoService.autorizar(admin, Perfil.ADMINISTRADOR);
    }

    @Test
    @DisplayName("RF-02-T2: usuario SOLICITANTE chamando endpoint ADM-only deve receber HTTP 403")
    void rf02T2_solicitanteEmEndpointAdmOnly_deveRetornar403() {
        // arrange
        Usuario solicitante = new Usuario(UUID.randomUUID(), "solicitante@example.com", "hash", Perfil.SOLICITANTE);

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> autenticacaoService.autorizar(solicitante, Perfil.ADMINISTRADOR));

        // assert
        assertEquals(403, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-02-T3: usuario RESPONSAVEL chamando endpoint de CRUD de usuarios (ADM-only) deve receber HTTP 403")
    void rf02T3_responsavelEmCrudDeUsuarios_deveRetornar403() {
        // arrange
        Usuario responsavel = new Usuario(UUID.randomUUID(), "responsavel@example.com", "hash", Perfil.RESPONSAVEL);

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> autenticacaoService.autorizar(responsavel, Perfil.ADMINISTRADOR));

        // assert
        assertEquals(403, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-03-T1: solicitante deve poder alterar/cancelar a propria reserva")
    void rf03T1_solicitanteAlteraPropriaReserva_devePermitir() {
        // arrange
        Usuario solicitanteA = new Usuario(UUID.randomUUID(), "a@example.com", "hash", Perfil.SOLICITANTE);
        UUID reservaId = UUID.randomUUID();

        // act
        reservaService.cancelar(solicitanteA, reservaId, "Mudanca de planos");

        // assert — nao deve lancar excecao de autorizacao (comportamento validado via ausencia de excecao 403)
    }

    @Test
    @DisplayName("RF-03-T2: solicitante A tentando alterar/cancelar reserva de solicitante B deve receber HTTP 403")
    void rf03T2_solicitanteAlterandoReservaDeOutroSolicitante_deveRetornar403() {
        // arrange
        Usuario solicitanteB = new Usuario(UUID.randomUUID(), "b@example.com", "hash", Perfil.SOLICITANTE);
        UUID reservaDeOutroSolicitante = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.cancelar(solicitanteB, reservaDeOutroSolicitante, "Tentativa indevida"));

        // assert
        assertEquals(403, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-03-T3 [gap Q7, assumindo que GET de reserva alheia tambem e 403]: solicitante consultando reserva de outro solicitante deve receber HTTP 403")
    void rf03T3_solicitanteConsultaReservaDeOutro_deveRetornar403() {
        // arrange
        Usuario solicitanteB = new Usuario(UUID.randomUUID(), "b@example.com", "hash", Perfil.SOLICITANTE);
        UUID reservaDeOutroSolicitante = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.consultar(solicitanteB, reservaDeOutroSolicitante));

        // assert
        assertEquals(403, ex.getStatusHttp());
    }
}
