# Evidência de TDD — Aprovação, bloqueio por manutenção e máquina de estados

> **Fonte dos cenários:** [bdd-aprovacao-bloqueio.md](bdd-aprovacao-bloqueio.md).
> **Branch:** `tdd-aprovacao-bloqueio`. **Teste:** [`BddAprovacaoBloqueioTest`](../../src/test/java/com/edualoca/BddAprovacaoBloqueioTest.java).
> **Comando em todos os passos:** `./mvnw -B test` (suíte inteira).
>
> **Regras do ciclo:** um único teste JUnit 5 por cenário (o Esquema do Cenário vira um único `@ParameterizedTest`);
> o vermelho tem de ser **falha de asserção**, e não erro de compilação ou de configuração. Quando o teste precisava de
> uma API inexistente, o commit vermelho traz só o esqueleto mínimo para compilar (getter que retorna `null`,
> método sem efeito ou construtor que ignora o argumento). Nenhum teste foi alterado para passar. O `Clock` é
> injetado; não há `Instant.now()`/`LocalDateTime.now()` escondido em código de produção nem `Thread.sleep`.

## Ciclos executados

| Cenário | Commit red | Commit green | Commit refactor | Trecho da saída do teste |
|---|---|---|---|---|
| **BDD-R1-06**: rejeição com justificativa deixa a justificativa visível ao solicitante (RF-20) | `ba55d99` | `39d88a7` | `7d0c2f0`: encapsula a rejeição em `Reserva.rejeitar(justificativa)` (estado e justificativa mudam juntos, sem setter público) e extrai fixtures do contexto BDD | **Red:** `BddAprovacaoBloqueioTest.rejeicaoComJustificativa_tornaJustificativaVisivelAoSolicitante:37 expected: <Laboratorio reservado para banca> but was: <null>` · `Tests run: 142, Failures: 1, Errors: 0`<br>**Green:** `Tests run: 142, Failures: 0, Errors: 0` · `BUILD SUCCESS`<br>**Refactor:** `Tests run: 142, Failures: 0, Errors: 0` |
| **BDD-R3-05**: reserva de qualquer tipo de recurso (SALA, MATERIAL, PROFESSOR) em manutenção é recusada com HTTP 409 (RF-21/RF-22) | `20a96b8` | `7c32c37` | `026db1c`: centraliza `Intervalos.sobrepoe` e `EstadoReserva.isAtiva`, que tinham 3 cópias cada em `ReservaService`, `CadastroRecursoService` e `PesquisaRecursoService`. Mesma semântica `[início, fim)` | **Red:** `reservaDeRecursoDeQualquerTipoEmManutencao_eRecusadaCom409(String, String)[2]` e `[3]`: `AssertionFailedError: Expected com.edualoca.domain.comum.RegraNegocioException to be thrown, but nothing was thrown.` (linha SALA `[1]` já passava) · `Tests run: 145, Failures: 2, Errors: 0`<br>**Green:** `Tests run: 4, Failures: 0 -- in com.edualoca.BddAprovacaoBloqueioTest` · `Tests run: 145, Failures: 0, Errors: 0`<br>**Refactor:** `Tests run: 145, Failures: 0, Errors: 0` |
| **BDD-R4-01**: transição permitida muda o estado e gera **exatamente um** evento de auditoria com ator e instante (RF-27). Linhas: SOLICITADA→APROVADA, SOLICITADA→REJEITADA, SOLICITADA→CANCELADA, APROVADA→CANCELADA | `1b42968` | `3da386e` | `906b114`: injeta `Clock` nas regras de manutenção (`CadastroRecursoService`) e em `AuditoriaService`, remove todo `now()` escondido da produção e extrai o despacho de ação e o relógio fixo no teste | **Red (4 linhas):** `transicaoPermitida_...ComAtorEInstante(...)[1]: expected: <2026-03-09T11:30> but was: <2026-11-10T15:33:18.551912>` (idem `[2]`, `[3]`, `[4]`) · estado, contagem e ator já batiam; só o instante falhava · `Failures: 4, Errors: 0`<br>**Green:** `Tests run: 8, Failures: 0 -- in com.edualoca.BddAprovacaoBloqueioTest` · `Tests run: 149, Failures: 0, Errors: 0`<br>**Refactor:** `Tests run: 149, Failures: 0, Errors: 0` · `BUILD SUCCESS` |

Contagem da suíte: 141 testes antes → **149** depois (1 + 3 + 4 casos novos), todos verdes.

## Cenários sem ciclo TDD (e por quê)

Um cenário só entra em ciclo se o teste puder ficar **vermelho por asserção** contra o código atual. Escrever um
teste que já nasce verde e chamá-lo de "red" seria pular o vermelho, por isso estes cenários não ganharam commit.

| Cenário(s) | Situação | Motivo |
|---|---|---|
| BDD-R1-01, R1-02, R1-03, R1-04, R1-05, R1-07 | Já atendido | Comportamento já existe em `ReservaService.criar/aprovar/rejeitar` (estado inicial, auditoria `CRIACAO`/`APROVACAO`, 400 para justificativa nula/vazia/em branco) |
| BDD-R2-01, R2-02 | Já atendido | `aprovar`/`rejeitar` recusam perfil SOLICITANTE com 403 antes de qualquer mudança ou auditoria |
| BDD-R3-01, R3-02, R3-03, R3-06 | Já atendido | Bloqueio por manutenção de sala na criação e na alteração (409) e confirmação exigida no cadastro sobre reserva APROVADA (RF-23) |
| BDD-R3-04 | Já atendido para SALA | `PesquisaRecursoService.buscarSalas` já exclui sala em manutenção. Excluir **professor/material** em manutenção da busca não está no cenário; fica como lacuna (ver abaixo) |
| BDD-R3-08 F3–F7 | Já atendido | A semântica `[início, fim)` atual já recusa F3/F4/F7 e aceita F5/F6 |
| BDD-R4-02 (linhas aprovar/rejeitar/cancelar), R4-03, R4-04 | Já atendido | Transições fora de SOLICITADA e cancelamento de estado terminal, sem motivo ou de terceiro já são recusados |
| BDD-R1-08, R4-01 (linhas P-05/P-07/P-08), R4-02 (linhas iniciar uso/concluir) | ⏸ Bloqueado | **P-07/P-08**: gatilho e ator de `iniciar uso`, `concluir` e `não comparecimento` indefinidos |
| BDD-R1-09, R2-05, R3-07, R3-08 F1/F2, R4-06 | ⏸ Bloqueado | **P-02, P-01, P-04, P-03, P-06** |
| BDD-R2-03, R2-04, R4-05 | Fora de escopo | Testes só de API; ainda não existe camada web (`@RestController`) |

## Observações para a próxima rodada

- **Lacuna de RF-11:** depois de BDD-R3-05, a manutenção já bloqueia a *reserva* de Material/Professor, mas a
  *busca* (`PesquisaRecursoService.buscarProfessores/buscarMateriais`) ainda não os exclui. Ela merece um cenário BDD
  próprio e um ciclo red/green.
- **Teste legado dependente do relógio real:** `RelatoriosTest.rf35_contagemDeRejeicoesReais_deveRespeitarAcaoEPeriodo`
  usa `LocalDateTime.now() ± 1 min` e chama `AuditoriaService.registrar(...)` sem instante. Essa sobrecarga ficou
  `@Deprecated` e é usada só por esse teste. Migrar o teste para relógio fixo é uma mudança de teste e pede decisão
  separada.
- **Testes que passam pelo motivo errado:** `MaquinaEstadosReservaTest.estT2/T3/T7/T8/T10–T13` e
  `AprovacaoRecursosRestritosTest.rf18T1` chamam `aprovar` com um `UUID.randomUUID()` que não existe no `Banco`;
  o 409 vem de "reserva inexistente". (bdd-aprovacao-bloqueio.md também cita `rf19T4`, mas ele monta uma reserva
  real e passa pelo motivo certo.) Continuam inalterados. O `BDD-R4-01` deste ciclo monta a reserva no estado de
  origem real.
