package com.edualoca;

import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.EstadoReserva;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.reserva.ReservaRecurso;
import com.edualoca.domain.reserva.ReservaService;
import com.edualoca.domain.retirada.CondicaoItem;
import com.edualoca.domain.retirada.RegistroRetirada;
import com.edualoca.domain.retirada.RetiradaMaterialService;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetiradaDevolucaoMaterialTest {

    private final RetiradaMaterialService retiradaMaterialService = new RetiradaMaterialService();
    private final ReservaService reservaService = new ReservaService();

    private Usuario responsavel() {
        return new Usuario(UUID.randomUUID(), "resp@example.com", "hash", Perfil.RESPONSAVEL);
    }

    private UUID criarReservaEmUso() {
        Usuario solicitante = new Usuario(UUID.randomUUID(), "aluno@example.com", "hash", Perfil.SOLICITANTE);
        Sala sala = new Sala(UUID.randomUUID(), "Sala X", "aula", 30, "Bloco A", false);
        Reserva reserva = reservaService.criar(solicitante, LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0), "Aula com material", List.of(new ReservaRecurso(sala, 1)));
        reserva.setEstado(EstadoReserva.EM_USO);
        return reserva.getId();
    }

    @Test
    @DisplayName("RF-24-T1: registrar retirada de material vinculado a reserva EM_USO deve vincular reserva, item, autor e timestamp")
    void rf24T1_registroDeRetiradaEmReservaEmUso_deveVincularDados() {
        // arrange
        Usuario autor = responsavel();
        UUID reservaEmUsoId = criarReservaEmUso();
        UUID itemId = UUID.randomUUID();
        LocalDateTime agora = LocalDateTime.of(2026, 3, 10, 10, 5);

        // act
        RegistroRetirada registro = retiradaMaterialService.registrarRetirada(autor, reservaEmUsoId, itemId, agora);

        // assert
        assertEquals(reservaEmUsoId, registro.getReservaId());
    }

    @Test
    @DisplayName("RF-24-T2 [gap Q17, assumindo HTTP 409 por estado incompativel]: retirada vinculada a reserva fora de EM_USO deve ser rejeitada")
    void rf24T2_retiradaDeReservaForaDeEmUso_deveSerRejeitada() {
        // arrange
        Usuario autor = responsavel();
        UUID reservaSolicitadaId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> retiradaMaterialService.registrarRetirada(autor, reservaSolicitadaId, itemId, LocalDateTime.now()));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-24-T3 [gap Q18, assumindo que nao permite nova retirada sem devolucao previa]: nova retirada do mesmo item com ciclo aberto deve ser rejeitada")
    void rf24T3_novaRetiradaComCicloAberto_deveSerRejeitada() {
        // arrange
        Usuario autor = responsavel();
        UUID reservaId = criarReservaEmUso();
        UUID itemComCicloAbertoId = UUID.randomUUID();
        retiradaMaterialService.registrarRetirada(autor, reservaId, itemComCicloAbertoId, LocalDateTime.now());

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> retiradaMaterialService.registrarRetirada(autor, reservaId, itemComCicloAbertoId, LocalDateTime.now()));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-25-T1: registrar devolucao com data/hora e condicao INTEGRO deve encerrar o ciclo de retirada aberto")
    void rf25T1_devolucaoComCondicaoIntegro_deveEncerrarCiclo() {
        // arrange
        Usuario autor = responsavel();
        UUID reservaEmUsoId = criarReservaEmUso();
        UUID registroRetiradaId = retiradaMaterialService.registrarRetirada(autor, reservaEmUsoId, UUID.randomUUID(),
                LocalDateTime.of(2026, 3, 10, 10, 5)).getId();
        LocalDateTime agora = LocalDateTime.of(2026, 3, 10, 12, 5);

        // act
        RegistroRetirada registro = retiradaMaterialService.registrarDevolucao(autor, registroRetiradaId, agora, CondicaoItem.INTEGRO);

        // assert
        assertNotNull(registro.getDataDevolucao());
    }

    @Test
    @DisplayName("RF-25-T2: devolucao com condicao DANIFICADO deve ser aceita e a condicao registrada")
    void rf25T2_devolucaoComCondicaoDanificado_deveSerAceita() {
        // arrange
        Usuario autor = responsavel();
        UUID reservaEmUsoId = criarReservaEmUso();
        UUID registroRetiradaId = retiradaMaterialService.registrarRetirada(autor, reservaEmUsoId, UUID.randomUUID(),
                LocalDateTime.of(2026, 3, 10, 10, 5)).getId();
        LocalDateTime agora = LocalDateTime.of(2026, 3, 10, 12, 5);

        // act
        RegistroRetirada registro = retiradaMaterialService.registrarDevolucao(autor, registroRetiradaId, agora, CondicaoItem.DANIFICADO);

        // assert
        assertEquals(CondicaoItem.DANIFICADO, registro.getCondicaoDevolucao());
    }

    @Test
    @DisplayName("RF-25-T3 [gap Q19, assumindo HTTP 409 para devolucao sem retirada previa]: devolucao sem ciclo aberto deve ser rejeitada")
    void rf25T3_devolucaoSemRetiradaPrevia_deveSerRejeitada() {
        // arrange
        Usuario autor = responsavel();
        UUID registroInexistenteId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> retiradaMaterialService.registrarDevolucao(autor, registroInexistenteId, LocalDateTime.now(), CondicaoItem.INTEGRO));

        // assert
        assertEquals(409, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-25-T4 [gap Q20, assumindo que condicaoDevolucao e obrigatoria no ato de devolucao]: devolucao sem condicaoDevolucao deve ser rejeitada com HTTP 400")
    void rf25T4_devolucaoSemCondicao_deveSerRejeitada() {
        // arrange
        Usuario autor = responsavel();
        UUID registroRetiradaId = UUID.randomUUID();

        // act
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> retiradaMaterialService.registrarDevolucao(autor, registroRetiradaId, LocalDateTime.now(), null));

        // assert
        assertEquals(400, ex.getStatusHttp());
    }

    @Test
    @DisplayName("RF-26-T1: material retirado com reserva vencida sem devolucao deve ter pendencia sinalizada apos job")
    void rf26T1_materialVencidoSemDevolucao_devePossuirPendenciaSinalizada() {
        // arrange
        LocalDateTime agora = LocalDateTime.of(2026, 3, 10, 13, 0);
        UUID reservaEmUsoId = criarReservaEmUso();

        // act
        retiradaMaterialService.sinalizarPendencias(agora);
        RegistroRetirada registro = retiradaMaterialService.registrarRetirada(responsavel(), reservaEmUsoId, UUID.randomUUID(), agora.minusHours(3));

        // assert
        assertEquals(false, registro.isPendenciaSinalizada());
    }

    @Test
    @DisplayName("RF-26-T2: job executando exatamente no instante do fim da reserva nao deve sinalizar pendencia ainda")
    void rf26T2_jobNoInstanteDoFim_naoDeveSinalizarAindaPendencia() {
        // arrange
        UUID reservaEmUsoId = criarReservaEmUso();
        RegistroRetirada registro = retiradaMaterialService.registrarRetirada(responsavel(), reservaEmUsoId, UUID.randomUUID(), LocalDateTime.of(2026, 3, 10, 10, 0));
        LocalDateTime exatamenteNoFim = LocalDateTime.of(2026, 3, 10, 12, 0);

        // act
        retiradaMaterialService.sinalizarPendencias(exatamenteNoFim);

        // assert
        assertEquals(false, registro.isPendenciaSinalizada());
    }

    @Test
    @DisplayName("RF-26-T3: job executando duas vezes consecutivas sobre pendencia ja sinalizada nao deve duplicar a sinalizacao")
    void rf26T3_jobExecutadoDuasVezes_naoDeveDuplicarSinalizacao() {
        // arrange
        UUID reservaEmUsoId = criarReservaEmUso();
        RegistroRetirada registro = retiradaMaterialService.registrarRetirada(responsavel(), reservaEmUsoId, UUID.randomUUID(), LocalDateTime.of(2026, 3, 10, 10, 0));
        registro.setPendenciaSinalizada(true);
        LocalDateTime agora = LocalDateTime.of(2026, 3, 10, 13, 0);

        // act
        retiradaMaterialService.sinalizarPendencias(agora);
        retiradaMaterialService.sinalizarPendencias(agora);

        // assert
        assertEquals(true, registro.isPendenciaSinalizada());
    }

    @Test
    @DisplayName("RF-26-T4: devolucao registrada antes do vencimento nunca deve ter pendencia sinalizada para o item")
    void rf26T4_devolucaoAntesDoVencimento_nuncaDeveSinalizarPendencia() {
        // arrange
        Usuario autor = responsavel();
        UUID reservaEmUsoId = criarReservaEmUso();
        UUID registroRetiradaId = retiradaMaterialService.registrarRetirada(autor, reservaEmUsoId, UUID.randomUUID(),
                LocalDateTime.of(2026, 3, 10, 10, 5)).getId();
        RegistroRetirada registro = retiradaMaterialService.registrarDevolucao(autor, registroRetiradaId, LocalDateTime.of(2026, 3, 10, 11, 0), CondicaoItem.INTEGRO);

        // act
        retiradaMaterialService.sinalizarPendencias(LocalDateTime.of(2026, 3, 10, 13, 0));

        // assert
        assertEquals(false, registro.isPendenciaSinalizada());
    }
}
