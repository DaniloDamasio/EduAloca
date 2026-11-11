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
| BDD-R2-03, R2-04, R4-05 | Coberto na rodada de API | Testes só de API; viraram cenários Cucumber na seção "Ciclos BDD de API" abaixo |

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

---

## Ciclos BDD de API (Cucumber-JVM)

> **Branch:** `bdd-cucumber-aprovacao-bloqueio`. **Feature:** [`aprovacao-bloqueio.feature`](../../src/test/resources/features/aprovacao-bloqueio.feature)
> (texto igual ao do documento BDD). **Steps:** [`AprovacaoBloqueioSteps`](../../src/test/java/com/edualoca/bdd/AprovacaoBloqueioSteps.java).
> **Runner:** [`AprovacaoBloqueioFeatureTest`](../../src/test/java/com/edualoca/bdd/AprovacaoBloqueioFeatureTest.java) (Surefire).
> **Comando:** `./mvnw -B test` em cada passo; `./mvnw -B verify` (com o gate JaCoCo) no fim.
>
> Cada ação dos cenários chama a API por MockMvc com o token Bearer do perfil que a executa (SOLICITANTE,
> RESPONSAVEL, ADMINISTRADOR). O relógio é fixo (`Clock` `@Primary` no contexto de teste). Um ciclo cobre uma
> Regra do documento: o commit red acrescenta os cenários da Regra à feature, e o vermelho tem de ser falha
> de asserção, não passo indefinido. Antes desta rodada não havia camada web; ela nasceu nos commits green.

| Regra / cenários | Commit red | Commit green | Refactor / fix | Trecho da saída |
|---|---|---|---|---|
| **Regra 1**: R1-01, R1-04, R1-05, R1-06, R1-07 (3 exemplos) | `0bbd0e8` (configura Cucumber + feature + steps; `TokenService` só esqueleto) | `4727b59`: `POST /reservas`, `GET /reservas/{id}`, `/aprovacao`, `/rejeicao`, token Bearer, `TratadorDeErros` | — | **Red:** `Então a reserva é criada no estado SOLICITADA … expected: <201> but was: <404>` · `Tests run: 156, Failures: 7`<br>**Green:** `Tests run: 7, Failures: 0 -- in AprovacaoBloqueioFeatureTest` · `Tests run: 156, Failures: 0` |
| **Regra 2**: R2-01, R2-02, R2-03, R2-04 | **sem red** | `3e1ecaa` (só a feature) | — | Verde na 1ª execução: o 403 já vinha de `ReservaService` e o tratador do ciclo 1 já o traduzia. **Prova de que os cenários falham:** com o `throw 403` de `aprovar` removido, R2-01/R2-03/R2-04 ficam vermelhos (`expected: <403> but was: <200>`); mutante revertido · `Tests run: 160, Failures: 0` |
| **Regra 3**: R3-01, R3-04, R3-06 | `f4ac2ff` | `67449e2`: `POST /manutencoes` (só ADMINISTRADOR), `GET /salas/disponiveis` | `d27a953` (fix) | **Red:** `uma manutenção da "Lab 3" … expected: <201> but was: <500>`. O **500** em vez de 404 revelou um defeito: o handler genérico engolia `NoResourceFoundException`. O fix mantém 404/405/415 do framework, e o red passou a mostrar `but was: <404>` · `Failures: 3`<br>**Green:** `Tests run: 14, Failures: 0` · `Tests run: 163, Failures: 0` |
| **Regra 4**: R4-01 (4 linhas + 3 `@bloqueado`), R4-03, R4-04, R4-05 | `4bf7bf4` | `31da649`: `POST /reservas/{id}/cancelamento`, `DELETE /reservas/{id}` → `ReservaService.excluir` | `6124305` | **Red:** `expected: <200>/<400>/<403> but was: <404>` · `Failures: 4, Skipped: 3`. R4-05 já passava **pelo motivo errado** (405 do Spring, rota sem `DELETE`); o green faz o 403 vir da regra RF-30<br>**Green:** `Tests run: 24, Failures: 0, Skipped: 3` · `Tests run: 173, Failures: 0, Skipped: 3` |
| **Refactor final** | — | — | `6124305`: o passo "o cadastro é recusado" passa a exigir que nenhuma manutenção nova seja persistida; `ApiSegurancaTest` cobre 401, 403 em `/manutencoes`, 400 de entrada malformada e 404/405 sem detalhe interno | `./mvnw -B verify`: `Tests run: 188, Failures: 0, Errors: 0, Skipped: 3` · `All coverage checks have been met.` · JaCoCo **95,1% linhas / 81,4% ramos** (gate 80% / 70%) |

Contagem da suíte: 149 testes → **188** (24 casos Cucumber, sendo 21 executados e 3 `@bloqueado` *skipped*, e
mais 15 casos em `ApiSegurancaTest`).

### Decisões e limites desta rodada

- **Só a coluna "API" do documento virou feature.** Cenários só de unidade continuam em `BddAprovacaoBloqueioTest`
  e nas suítes JUnit. Cenários cujo "Então" é uma pergunta (R1-09, R2-05, R3-07, R4-06) não viram passo: não
  há resultado para verificar.
- **Preparação ("Dado") fora da API quando não existe endpoint:** salas e usuários com token são criados pelos
  serviços de domínio. O estado `EM_USO` (R4-05) é atribuído direto no repositório em memória, porque o gatilho
  `APROVADA → EM_USO` está em aberto (P-07). As reservas de origem são criadas pela API.
- **Auditoria verificada no registro append-only** (`Banco.auditoria`), porque ainda não existe endpoint de
  consulta de auditoria.
- **`cucumber-spring`** entrou além de `cucumber-java` e `cucumber-junit-platform-engine`: sem ele os steps não
  recebem o contexto Spring (MockMvc, `TokenService`, `Clock`).

---

## Histórico auditável das reservas (RF-27, RF-29, RF-30)

> **Branch:** `historico-auditavel-reservas`. **Comando:** `./mvnw -B test` (unidade e API) e
> `./mvnw -B verify` (com os ITs do Testcontainers e o gate JaCoCo).
>
> Mesmas regras do ciclo: o vermelho tem de ser falha de asserção. Quando o teste precisava de uma API
> inexistente, um commit `chore` anterior trouxe só o esqueleto (métodos sem efeito). Os commits de teste e de
> código são separados.

| Rodada | Esqueleto | Commit red | Commit green | Trecho da saída |
|---|---|---|---|---|
| **Unidade**: registro por transição (criar, aprovar, rejeitar, cancelar) com usuário, instante do `Clock`, estado anterior/novo e motivo; registro inválido recusado; lista imutável; falha ao gravar desfaz a mudança em memória; reserva iniciada não é apagada e mantém o histórico; consulta só para Responsável/Administrador | `ebd7135` | `1236d98` | `e24323a` | **Red:** `esperava exatamente um registro de historico ==> expected: <1> but was: <0>`; `solicitanteDonoRecebe403 Expected RegraNegocioException to be thrown, but nothing was thrown` · `Tests run: 29, Failures: 29, Errors: 0`<br>**Green:** `Tests run: 233, Failures: 0, Errors: 0, Skipped: 3` |
| **Integração (PostgreSQL via Testcontainers)**: mesma transação; rollback depois de o histórico já ter sido escrito não deixa auditoria órfã (transição e criação); banco recusa UPDATE/DELETE | `f345f17` (repositório vazio, `TransacaoJdbc` sem transação, tabela mínima) | `c421ae5` | `ce0dfb9` (fix de teste antes: `791b71e`) | **Red:** `expected: <2> but was: <0>` · `Tests run: 4, Failures: 4, Errors: 0`<br>**Passo intermediário (só o repositório, sem transação):** `expected: <[SOLICITADA]> but was: <[SOLICITADA, APROVADA]>` e `expected: <SOLICITADA> but was: <APROVADA>`, o que prova que o IT detecta auditoria órfã<br>**Green:** `Tests run: 4, Failures: 0 -- in HistoricoReservaTransacaoIT` |
| **API**: `GET /reservas/{id}/historico` 200 para Responsável/Administrador, 403 para o Solicitante dono, 401 sem token, 405 para PUT/PATCH/DELETE/POST | — (rota inexistente já dá 404) | `3ff6122` | `bf674f5` | **Red:** `Status expected:<403> but was:<404>` (idem 200/401/405) · `Tests run: 8, Failures: 8, Errors: 0`<br>**Green:** `Tests run: 241, Failures: 0, Errors: 0, Skipped: 3` |
| **Final** | — | — | — | `./mvnw -B verify`: `Tests run: 241` (unidade) + `Tests run: 14` (IT), `Failures: 0` · `All coverage checks have been met.` · JaCoCo **96,2% linhas / 85,0% ramos** |

Contagem da suíte: 204 testes de unidade e 10 ITs → **241 + 14** (29 de unidade, 8 de API e 4 ITs novos).

### Decisões e limites desta rodada

- **Como se prova a mesma transação:** no IT, um decorador do repositório grava o histórico de verdade e, ainda
  dentro da transação, lê pela mesma conexão o estado `APROVADA` e a nova linha de histórico. Só depois lança a
  falha. Após o rollback, o banco tem `SOLICITADA` e só o registro da criação.
- **Cache em memória:** `ReservaRepositorioJdbc` só publica a reserva em `Banco.reservas` depois do commit. Antes
  disso, uma criação desfeita deixava no cache uma reserva que não existia no banco (pego por
  `rollbackNaCriacaoNaoDeixaNada`).
- **Criação conta como mudança de estado** (`null → SOLICITADA/APROVADA`). Alterar só o horário não muda o
  estado e não gera registro (continua gerando o `AuditoriaEvento` `ATUALIZACAO` de RF-28).
- **Exclusão (RF-30):** o 403 para reserva iniciada já existia. O que deixou o teste vermelho foi exigir que o
  histórico continue intacto. `EM_USO`, `CONCLUIDA` e `NAO_COMPARECEU` são atribuídos direto, porque os gatilhos
  continuam em aberto (P-07/P-08).
- **Dois mecanismos de auditoria convivem:** `AuditoriaEvento` (genérico, em memória, usado por RF-28 e pelos
  cenários BDD) e `RegistroHistoricoReserva` (histórico de estado, persistido). Unificar os dois é decisão para
  outra rodada.
- **Docker via Colima:** localmente os ITs precisaram de `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock`
  e `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.
