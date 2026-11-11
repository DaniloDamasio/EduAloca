package com.edualoca;

import com.edualoca.api.seguranca.TokenService;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import com.edualoca.suporte.RelogioAjustavel;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /relatorios/utilizacao?inicio=&fim= (RF-34, RF-35, RF-36), testado em caixa-preta: a massa e criada pela
 * propria API (POST /reservas, POST /reservas/{id}/cancelamento) e o relatorio e lido so pela resposta HTTP.
 * Apenas o catalogo (salas e professores) e semeado direto, porque ainda nao ha rota de cadastro.
 *
 * <h2>Regras que os valores esperados assumem</h2>
 * <ul>
 *   <li>Periodo semiaberto [inicio, fim), como o resto do sistema (encostar nao e sobrepor).</li>
 *   <li>Contam como uso as reservas APROVADA, EM_USO e CONCLUIDA. Cancelada (e rejeitada/pendente) nao conta.</li>
 *   <li>Reserva que cruza a borda do periodo conta so a parte que cai dentro dele.</li>
 *   <li>"recursos" traz salas e materiais; professores aparecem so em "professores" (carga horaria alocada).</li>
 *   <li>Conflito evitado = tentativa de criar reserva recusada por sobreposicao com reserva ativa, contada pelo
 *       instante da tentativa (criterio de RF-35: rejeicoes registradas em auditoria no periodo).</li>
 * </ul>
 *
 * <h2>Massa (relogio do servidor fixo, fuso America/Sao_Paulo)</h2>
 * <pre>
 * Catalogo: Sala 101, Lab 2, Prof. Ana, Prof. Bruno.
 *
 * Criadas com o relogio em 27/02/2027 10:00:
 *   R1  Sala 101 + Ana    10/03 08:00 - 10/03 10:00   APROVADA
 *   R2  Sala 101          31/03 22:00 - 01/04 02:00   APROVADA  (cruza a borda final de marco)
 *   R3  Lab 2 + Ana       28/02 23:00 - 01/03 01:30   APROVADA  (cruza a borda inicial de marco)
 *   R4  Lab 2 + Bruno     15/03 14:00 - 15/03 17:00   CANCELADA depois de criada
 *   R5  Sala 101          05/04 08:00 - 05/04 12:00   APROVADA  (fora de marco)
 *   C1  Sala 101          10/03 09:00 - 10/03 09:30   409, sobrepoe R1; tentativa em 27/02 (antes de marco)
 *
 * Relogio avancado para 05/03/2027 10:00:
 *   C2  Lab 2             01/03 00:00 - 01/03 01:00   409, sobrepoe R3
 *   C3  Lab 2 + Ana       10/03 09:00 - 10/03 11:00   409, Lab 2 livre mas Ana ocupada por R1
 * </pre>
 */
@SpringBootTest
class RelatorioUtilizacaoApiTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Instant CRIACAO_DA_MASSA = LocalDateTime.of(2027, 2, 27, 10, 0).atZone(FUSO).toInstant();
    private static final Instant TENTATIVAS_EM_MARCO = LocalDateTime.of(2027, 3, 5, 10, 0).atZone(FUSO).toInstant();

    @TestBean
    private Clock clock;

    static Clock clock() {
        return new RelogioAjustavel(CRIACAO_DA_MASSA, FUSO);
    }

    @Autowired
    private WebApplicationContext contexto;
    @Autowired
    private TokenService tokenService;

    private MockMvc api;
    private final Usuario solicitante = usuario(Perfil.SOLICITANTE);
    private String tokenSolicitante;

    private final Sala sala101 = new Sala(UUID.randomUUID(), "Sala 101", "aula", 40, "Bloco A", false);
    private final Sala lab2 = new Sala(UUID.randomUUID(), "Lab 2", "laboratorio", 20, "Bloco B", false);
    private final Professor ana = new Professor(UUID.randomUUID(), "Prof. Ana", "PROF-ANA", List.of(new Competencia("Quimica")));
    private final Professor bruno = new Professor(UUID.randomUUID(), "Prof. Bruno", "PROF-BRUNO", List.of(new Competencia("Fisica")));

    @BeforeEach
    void semearMassa() throws Exception {
        api = MockMvcBuilders.webAppContextSetup(contexto).build();
        tokenSolicitante = tokenService.emitir(solicitante);
        Banco.salas.put(sala101.getId(), sala101);
        Banco.salas.put(lab2.getId(), lab2);
        Banco.professores.put(ana.getId(), ana);
        Banco.professores.put(bruno.getId(), bruno);

        RelogioAjustavel relogio = (RelogioAjustavel) clock;
        relogio.definir(CRIACAO_DA_MASSA);
        reservar("2027-03-10T08:00", "2027-03-10T10:00", sala(sala101), professor(ana));          // R1
        reservar("2027-03-31T22:00", "2027-04-01T02:00", sala(sala101));                          // R2
        reservar("2027-02-28T23:00", "2027-03-01T01:30", sala(lab2), professor(ana));             // R3
        String r4 = reservar("2027-03-15T14:00", "2027-03-15T17:00", sala(lab2), professor(bruno));
        reservar("2027-04-05T08:00", "2027-04-05T12:00", sala(sala101));                          // R5
        tentarReservarComConflito("2027-03-10T09:00", "2027-03-10T09:30", sala(sala101));        // C1

        relogio.definir(TENTATIVAS_EM_MARCO);
        tentarReservarComConflito("2027-03-01T00:00", "2027-03-01T01:00", sala(lab2));           // C2
        tentarReservarComConflito("2027-03-10T09:00", "2027-03-10T11:00", sala(lab2), professor(ana)); // C3
        cancelar(r4);
    }

    @ParameterizedTest(name = "{0} -> 200")
    @EnumSource(value = Perfil.class, names = {"RESPONSAVEL", "ADMINISTRADOR"})
    @DisplayName("Marco/2027: horas por recurso, carga por professor e conflitos evitados batem com a conta a mao")
    void relatorioDoMes(Perfil perfil) throws Exception {
        // Periodo [01/03 00:00, 01/04 00:00)
        //
        // Sala 101: R1 10/03 08:00-10:00                    = 2,0 h
        //           R2 31/03 22:00-01/04 02:00 -> corta em 01/04 00:00 = 2,0 h (as 2 h de abril ficam de fora)
        //           R5 em abril                             = 0
        //           total                                   = 4,0 h
        // Lab 2:    R3 28/02 23:00-01/03 01:30 -> comeca em 01/03 00:00 = 1,5 h (a 1 h de fevereiro fica de fora)
        //           R4 cancelada                            = 0
        //           total                                   = 1,5 h
        // Ana:      R1 2,0 h + R3 1,5 h                     = 3,5 h
        // Bruno:    so R4, cancelada                        -> nao aparece
        // Conflitos: C1 foi tentado em 27/02 (fora); C2 e C3 em 05/03 -> 2
        // Ordem: maior numero de horas primeiro.
        relatorio(perfil, "2027-03-01T00:00", "2027-04-01T00:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos", hasSize(2)))
                .andExpect(jsonPath("$.recursos[0].tipo").value("SALA"))
                .andExpect(jsonPath("$.recursos[0].id").value(sala101.getId().toString()))
                .andExpect(jsonPath("$.recursos[0].nome").value("Sala 101"))
                .andExpect(jsonPath("$.recursos[0].horasReservadas").value(4.0))
                .andExpect(jsonPath("$.recursos[1].id").value(lab2.getId().toString()))
                .andExpect(jsonPath("$.recursos[1].horasReservadas").value(1.5))
                .andExpect(jsonPath("$.professores", hasSize(1)))
                .andExpect(jsonPath("$.professores[0].id").value(ana.getId().toString()))
                .andExpect(jsonPath("$.professores[0].nome").value("Prof. Ana"))
                .andExpect(jsonPath("$.professores[0].cargaHorariaAlocada").value(3.5))
                .andExpect(jsonPath("$.conflitosEvitados").value(2));
    }

    @Test
    @DisplayName("Periodo sem nenhuma reserva nem tentativa devolve listas vazias e zero conflitos, sem erro")
    void periodoVazio() throws Exception {
        // Periodo [01/06 00:00, 01/07 00:00): nenhuma reserva da massa toca junho
        // e todas as tentativas foram em 27/02 ou 05/03 -> recursos [], professores [], conflitos 0.
        relatorio(Perfil.ADMINISTRADOR, "2027-06-01T00:00", "2027-07-01T00:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos", hasSize(0)))
                .andExpect(jsonPath("$.professores", hasSize(0)))
                .andExpect(jsonPath("$.conflitosEvitados").value(0));
    }

    @Test
    @DisplayName("Reserva que cruza as duas bordas do periodo conta so a parte de dentro")
    void reservaQueCruzaABorda() throws Exception {
        // Periodo [01/03 00:00, 01/03 01:00) fica todo dentro de R3 (28/02 23:00 - 01/03 01:30):
        // corta 1 h no inicio e 30 min no fim -> Lab 2 = 1,0 h e Ana = 1,0 h (e nao 2,5 h).
        // Nenhuma outra reserva toca essa hora; as tentativas de 05/03 estao fora -> 0 conflitos.
        relatorio(Perfil.RESPONSAVEL, "2027-03-01T00:00", "2027-03-01T01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos", hasSize(1)))
                .andExpect(jsonPath("$.recursos[0].id").value(lab2.getId().toString()))
                .andExpect(jsonPath("$.recursos[0].horasReservadas").value(1.0))
                .andExpect(jsonPath("$.professores[*].cargaHorariaAlocada", contains(1.0)))
                .andExpect(jsonPath("$.conflitosEvitados").value(0));

        // Periodo [31/03 23:00, 01/04 01:00) fica todo dentro de R2 (31/03 22:00 - 01/04 02:00):
        // corta 1 h em cada ponta -> Sala 101 = 2,0 h (e nao 4,0 h); R2 nao tem professor.
        relatorio(Perfil.RESPONSAVEL, "2027-03-31T23:00", "2027-04-01T01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos[*].horasReservadas", contains(2.0)))
                .andExpect(jsonPath("$.recursos[0].id").value(sala101.getId().toString()))
                .andExpect(jsonPath("$.professores", hasSize(0)));
    }

    @Test
    @DisplayName("Reserva cancelada nao conta como uso nem como carga horaria do professor")
    void reservaCanceladaNaoConta() throws Exception {
        // Periodo [15/03 00:00, 16/03 00:00): a unica reserva do dia e R4 (Lab 2 + Bruno, 3 h), cancelada.
        // Se a cancelada contasse, Lab 2 e Bruno teriam 3,0 h. Esperado: nada.
        relatorio(Perfil.ADMINISTRADOR, "2027-03-15T00:00", "2027-03-16T00:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos", hasSize(0)))
                .andExpect(jsonPath("$.professores", hasSize(0)))
                .andExpect(jsonPath("$.conflitosEvitados").value(0));
    }

    @Test
    @DisplayName("Sem token o relatorio e recusado com 401")
    void semToken401() throws Exception {
        api.perform(get("/relatorios/utilizacao").param("inicio", "2027-03-01T00:00").param("fim", "2027-04-01T00:00"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").isString());
    }

    @Test
    @DisplayName("Token desconhecido e recusado com 401")
    void tokenDesconhecido401() throws Exception {
        api.perform(get("/relatorios/utilizacao").header("Authorization", "Bearer token-que-nunca-foi-emitido")
                        .param("inicio", "2027-03-01T00:00").param("fim", "2027-04-01T00:00"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Solicitante recebe 403 sem nenhum dado do relatorio")
    void solicitante403() throws Exception {
        api.perform(get("/relatorios/utilizacao").header("Authorization", "Bearer " + tokenSolicitante)
                        .param("inicio", "2027-03-01T00:00").param("fim", "2027-04-01T00:00"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").isString())
                .andExpect(content().string(not(containsString("Sala 101"))))
                .andExpect(content().string(not(containsString("conflitosEvitados"))));
    }

    @Test
    @DisplayName("Solicitante recebe 403 mesmo com parametros invalidos: o perfil e checado antes da entrada")
    void solicitanteComParametroInvalido403() throws Exception {
        api.perform(get("/relatorios/utilizacao").header("Authorization", "Bearer " + tokenSolicitante)
                        .param("inicio", "ontem"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "inicio={0} fim={1} -> 400")
    @CsvSource(nullValues = "NULO", value = {
            "NULO,             2027-04-01T00:00",   // falta inicio
            "2027-03-01T00:00, NULO",               // falta fim
            "01/03/2027,       2027-04-01T00:00",   // formato que nao e ISO
            "2027-03-01T00:00, 2027-03-01T00:00",   // fim igual ao inicio
            "2027-04-01T00:00, 2027-03-01T00:00",   // fim antes do inicio
            "2027-01-01T00:00, 2028-01-03T00:00"    // 367 dias: 2027 tem 365 + 2, passa do limite de 366
    })
    @DisplayName("Periodo ausente, mal formatado, invertido ou longo demais e recusado com 400 sem detalhe interno")
    void periodoInvalido400(String inicio, String fim) throws Exception {
        var requisicao = get("/relatorios/utilizacao").header("Authorization", "Bearer " + tokenService.emitir(usuario(Perfil.ADMINISTRADOR)));
        if (inicio != null) {
            requisicao.param("inicio", inicio);
        }
        if (fim != null) {
            requisicao.param("fim", fim);
        }
        api.perform(requisicao)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").isString())
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    @DisplayName("Periodo de exatamente 366 dias e o maior aceito")
    void periodoDeUmAnoNoLimite() throws Exception {
        // 01/01/2027 a 02/01/2028 = 365 dias de 2027 + 1 = 366 dias -> aceito.
        // Contem toda a massa: Sala 101 = R1 2 h + R2 4 h + R5 4 h = 10,0 h; Lab 2 = R3 2,5 h; Ana = 2 + 2,5 = 4,5 h;
        // conflitos C1, C2 e C3 = 3.
        relatorio(Perfil.ADMINISTRADOR, "2027-01-01T00:00", "2028-01-02T00:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos[*].horasReservadas", contains(10.0, 2.5)))
                .andExpect(jsonPath("$.professores[*].cargaHorariaAlocada", contains(4.5)))
                .andExpect(jsonPath("$.conflitosEvitados").value(3));
    }

    private ResultActions relatorio(Perfil perfil, String inicio, String fim) throws Exception {
        return api.perform(get("/relatorios/utilizacao")
                .header("Authorization", "Bearer " + tokenService.emitir(usuario(perfil)))
                .param("inicio", inicio).param("fim", fim));
    }

    private String reservar(String inicio, String fim, String... recursos) throws Exception {
        String corpo = api.perform(post("/reservas").header("Authorization", "Bearer " + tokenSolicitante)
                        .contentType(MediaType.APPLICATION_JSON).content(corpoReserva(inicio, fim, recursos)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("APROVADA"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.id");
    }

    private void tentarReservarComConflito(String inicio, String fim, String... recursos) throws Exception {
        api.perform(post("/reservas").header("Authorization", "Bearer " + tokenSolicitante)
                        .contentType(MediaType.APPLICATION_JSON).content(corpoReserva(inicio, fim, recursos)))
                .andExpect(status().isConflict());
    }

    private void cancelar(String reservaId) throws Exception {
        api.perform(post("/reservas/{id}/cancelamento", reservaId).header("Authorization", "Bearer " + tokenSolicitante)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Turma remanejada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    private static String corpoReserva(String inicio, String fim, String... recursos) {
        return "{\"inicio\":\"" + inicio + "\",\"fim\":\"" + fim + "\",\"finalidade\":\"Aula\",\"recursos\":["
                + String.join(",", recursos) + "]}";
    }

    private static String sala(Sala sala) {
        return "{\"tipo\":\"SALA\",\"id\":\"" + sala.getId() + "\"}";
    }

    private static String professor(Professor professor) {
        return "{\"tipo\":\"PROFESSOR\",\"id\":\"" + professor.getId() + "\"}";
    }

    private static Usuario usuario(Perfil perfil) {
        return new Usuario(UUID.randomUUID(), perfil.name().toLowerCase() + "@edualoca.test", "h", perfil);
    }
}
