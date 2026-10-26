package com.edualoca;

import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AprovacaoRecursosRestritosTest {

    private final ReservaService reservaService = new ReservaService();

    @Test
    @DisplayName("RF-18-T1: reserva restrita nao pode transicionar diretamente de SOLICITADA para EM_USO, pulando APROVADA")
    void rf18T1_transicaoDiretaSolicitadaParaEmUso_deveSerBloqueada() {
        // arrange
        Usuario responsavel = new Usuario(UUID.randomUUID(), "resp@example.com", "hash", Perfil.RESPONSAVEL);
        UUID reservaSolicitadaComRecursoRestritoId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.aprovar(responsavel, reservaSolicitadaComRecursoRestritoId));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-19-T1: responsavel aprova reserva SOLICITADA de recurso restrito e ela transiciona para APROVADA")
    void rf19T1_responsavelAprovaReservaRestrita_deveTransicionarParaAprovada() {
        // arrange
        Usuario responsavel = new Usuario(UUID.randomUUID(), "resp@example.com", "hash", Perfil.RESPONSAVEL);
        UUID reservaId = UUID.randomUUID();

        // act
        Reserva reservaAprovada = reservaService.aprovar(responsavel, reservaId);

        // assert
        assertEquals(EstadoReserva.APROVADA, reservaAprovada.getEstado());
    }

    @Test
    @DisplayName("RF-19-T2: administrador tambem pode aprovar reserva restrita")
    void rf19T2_administradorAprovaReservaRestrita_devePermitir() {
        // arrange
        Usuario administrador = new Usuario(UUID.randomUUID(), "adm@example.com", "hash", Perfil.ADMINISTRADOR);
        UUID reservaId = UUID.randomUUID();

        // act
        Reserva reservaAprovada = reservaService.aprovar(administrador, reservaId);

        // assert
        assertEquals(EstadoReserva.APROVADA, reservaAprovada.getEstado());
    }

    @Test
    @DisplayName("RF-19-T3: solicitante tentando aprovar/rejeitar reserva restrita (inclusive a propria) deve receber HTTP 403")
    void rf19T3_solicitanteTentaAprovar_deveRetornar403() {
        // arrange
        Usuario solicitante = new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE);
        UUID reservaId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.aprovar(solicitante, reservaId));

        // assert
        assertEquals(403, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-19-T4 [gap Q16, assumindo HTTP 409 por transicao invalida]: aprovar reserva ja fora de SOLICITADA deve ser rejeitado")
    void rf19T4_aprovarReservaForaDeSolicitada_deveSerRejeitado() {
        // arrange
        Usuario responsavel = new Usuario(UUID.randomUUID(), "resp@example.com", "hash", Perfil.RESPONSAVEL);
        UUID reservaJaAprovadaId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.aprovar(responsavel, reservaJaAprovadaId));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-20-T1: responsavel rejeita reserva restrita com justificativa nao vazia e transiciona para REJEITADA")
    void rf20T1_rejeicaoComJustificativa_deveTransicionarParaRejeitada() {
        // arrange
        Usuario responsavel = new Usuario(UUID.randomUUID(), "resp@example.com", "hash", Perfil.RESPONSAVEL);
        UUID reservaId = UUID.randomUUID();

        // act
        Reserva reservaRejeitada = reservaService.rejeitar(responsavel, reservaId, "Recurso necessario para outro evento institucional");

        // assert
        assertEquals(EstadoReserva.REJEITADA, reservaRejeitada.getEstado());
    }

    @Test
    @DisplayName("RF-20-T2: rejeicao sem justificativa (vazia/nula) deve ser rejeitada com HTTP 400")
    void rf20T2_rejeicaoSemJustificativa_deveSerRejeitada() {
        // arrange
        Usuario responsavel = new Usuario(UUID.randomUUID(), "resp@example.com", "hash", Perfil.RESPONSAVEL);
        UUID reservaId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> reservaService.rejeitar(responsavel, reservaId, ""));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }
}
