# RTM — Matriz de Rastreabilidade de Requisitos

> **Status (2026-11-12):** os 32 requisitos críticos de [REQUISITOS.md](REQUISITOS/REQUISITOS.md) têm linha nesta
> matriz. A seção "Correções do relatório de qualidade" registra os 12 que faltavam; a evidência de CI dessa seção é
> a do PR das correções.

| Requisito | Risco | Teste | Evidência / Execução |
|---|---|---|---|
| RF-31 — Notificar o Solicitante a cada mudança de estado da própria reserva (REQUISITOS.md:99) | Notificação não disparada ou disparada para destinatário errado após transição de estado. | `NotificacoesTest.rf31T1_mudancaDeEstadoDaReserva_deveNotificarSolicitante` (lógica de domínio); `IntegracaoNotificacaoExternaTest.int01_respostaValida_deveRetornarSucessoEEmitirRequestEsperado` (transporte HTTP real via WireMock). | `./mvnw -B verify` — suíte verde, executada no CI (`.github/workflows/ci.yml`) a cada PR. |
| RF-32 — Notificar o Responsável competente ao surgir solicitação restrita pendente (REQUISITOS.md:100) | Responsável não identificado ou exceção não tratada quando recurso restrito não tem responsável associado. | `NotificacoesTest.rf32T1_novaSolicitacaoRestrita_deveNotificarResponsavelCompetente`; `NotificacoesTest.rf32T2_recursoRestritoSemResponsavelAssociado_deveRetornarFalha`. | Idem acima. |
| RF-33 — Falha de notificação externa não deve reverter a transição de estado (REQUISITOS.md:101) | Indisponibilidade, timeout ou erro do provedor externo propaga exceção e desfaz a transição de negócio. | `NotificacoesTest.rf33T1/T2` (lógica isolada do transporte); `IntegracaoNotificacaoExternaTest.int02_resposta404_naoDeveReenviar`, `int03_resposta500_deveReenviarUmaVezAntesDeFalhar`, `int04_timeout_deveInterromperSemLancarExcecao`, `int05_corpoInvalido_naoDeveLancarExcecao`. | Idem acima. |
| RNF-16 — Integrações externas isoladas por interface, substituíveis por WireMock em teste (REQUISITOS.md:148) | Adapter acoplado a detalhes de transporte, impossibilitando teste determinístico e offline. | `NotificacaoService.NotificadorExterno` (porta) + `NotificacaoHttpAdapter` (adaptador real) cobertos por `IntegracaoNotificacaoExternaTest` (WireMock em porta dinâmica, sem rede externa). | `./mvnw -B verify`; suíte roda offline. |
| RNF-16 (segurança da chamada externa) | Token/credencial do provedor vazado em log ou propagado ao cliente do EduAloca. | `IntegracaoNotificacaoExternaTest.seguranca_tokenVazadoNoCorpoDeErro_naoDeveAtravessarParaLogNemRetorno`. | Idem acima. |

## Aprovação, bloqueio por manutenção e máquina de estados da Reserva

Fonte dos cenários: [testes/bdd-aprovacao-bloqueio.md](testes/bdd-aprovacao-bloqueio.md).
Feature: `src/test/resources/features/aprovacao-bloqueio.feature` (os nomes abaixo são os títulos dos cenários na feature, iguais aos do documento).
Steps: `src/test/java/com/edualoca/bdd/AprovacaoBloqueioSteps.java`; runner:
`src/test/java/com/edualoca/bdd/AprovacaoBloqueioFeatureTest.java`. Evidência dos ciclos:
[testes/evidencia-tdd.md](testes/evidencia-tdd.md).

**Execução de todas as linhas:** `./mvnw -B verify`, que roda JUnit e Cucumber no Surefire, com
gate JaCoCo ≥80% linha / ≥70% ramo, a cada PR em `.github/workflows/ci.yml`.

| Requisito | Risco | Cenário | Teste ou feature (caminho :: nome) | Commit |
|---|---|---|---|---|
| RF-12 — Reserva nasce `SOLICITADA` com recurso restrito | Reserva restrita nasce `APROVADA` e pula a avaliação | BDD-R1-01 | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Reserva com recurso restrito nasce SOLICITADA* | red `0bbd0e8` · green `4727b59` |
| RF-19 — Só Responsável/Administrador aprovam | Aprovação válida recusada ou não auditada | BDD-R1-04, BDD-R1-05 | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Responsável aprova reserva restrita*; *Administrador aprova reserva restrita* | red `0bbd0e8` · green `4727b59` |
| RF-19 — Solicitante recebe 403 ao aprovar/rejeitar | Solicitante aprova a própria reserva ou a de terceiro (segregação de funções) | BDD-R2-01, BDD-R2-02, BDD-R2-03 | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Solicitante tenta aprovar a própria reserva*; *Solicitante tenta rejeitar a própria reserva*; *Solicitante tenta aprovar reserva de outro solicitante* | `3e1ecaa` (verde na 1ª execução; mutante comprovou a falha) |
| RF-20 — Rejeição com justificativa visível ao Solicitante | Justificativa perdida ou invisível para quem pediu | BDD-R1-06 | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Responsável rejeita com justificativa*; `src/test/java/com/edualoca/BddAprovacaoBloqueioTest.java` :: `rejeicaoComJustificativa_tornaJustificativaVisivelAoSolicitante` | API: red `0bbd0e8` · green `4727b59`. Unidade: red `ba55d99` · green `39d88a7` · refactor `7d0c2f0` |
| RF-20 / RNF-04 — Rejeição sem justificativa recusada com 400 | Rejeição com justificativa nula, vazia ou em branco aceita | BDD-R1-07 (3 exemplos) | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Rejeição sem justificativa é recusada* | red `0bbd0e8` · green `4727b59` |
| RF-38 / RNF-06 — Erro compreensível, sem stacktrace | Resposta expõe classe interna ou stacktrace | BDD-R2-04 | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Recusa por perfil traz mensagem compreensível*; `src/test/java/com/edualoca/ApiSegurancaTest.java` :: `errosDoFramework_mantemStatusSemDetalheInterno` | `3e1ecaa`; correção 500→404/405 `d27a953`; `6124305` |
| RF-22 — Reserva sobre manutenção recusada com 409 | Recurso reservado durante manutenção | BDD-R3-01; BDD-R3-05 (unidade) | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Reserva totalmente dentro da manutenção é recusada*; `src/test/java/com/edualoca/BddAprovacaoBloqueioTest.java` :: `reservaDeRecursoDeQualquerTipoEmManutencao_eRecusadaCom409` | API: red `f4ac2ff` · green `67449e2`. Unidade: red `20a96b8` · green `7c32c37` · refactor `026db1c` |
| RF-11 — Recurso em manutenção fora da busca | Busca oferece sala indisponível | BDD-R3-04 | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Recurso em manutenção não aparece na busca de disponibilidade* | red `f4ac2ff` · green `67449e2` |
| RF-21 / RF-23 — Manutenção sobre reserva `APROVADA` exige confirmação | Manutenção cadastrada derruba reserva já aprovada | BDD-R3-06 | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Cadastrar manutenção sobre reserva APROVADA exige confirmação explícita* | red `f4ac2ff` · green `67449e2` · refactor `6124305` |
| RF-27 — Toda transição gera exatamente um evento de auditoria com ator e instante | Transição sem auditoria, duplicada ou com autor/instante errado | BDD-R4-01 (4 linhas executáveis; 3 `@bloqueado`) | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Transição permitida muda o estado e gera auditoria*; `src/test/java/com/edualoca/BddAprovacaoBloqueioTest.java` :: `transicaoPermitida_mudaEstadoEGeraUmEventoDeAuditoriaComAtorEInstante` | API: red `4bf7bf4` · green `31da649`. Unidade: red `1b42968` · green `3da386e` · refactor `906b114` |
| RF-27 — Toda mudança de estado gera registro de histórico (reserva, usuário, instante do `Clock`, estado anterior/novo, motivo), gravado na mesma transação da mudança | Estado muda sem histórico, ou rollback deixa histórico órfão | — (rodada histórico auditável) | `src/test/java/com/edualoca/HistoricoAuditavelReservaTest.java` :: *Toda mudanca de estado gera registro*; `src/test/java/com/edualoca/persistencia/HistoricoReservaTransacaoIT.java` :: `aprovacaoGravaEstadoEHistorico`, `rollbackNaTransicaoNaoDeixaAuditoriaOrfa`, `rollbackNaCriacaoNaoDeixaNada` | Unidade: red `1236d98` · green `e24323a`. IT: red `c421ae5` · fix de teste `791b71e` · green `ce0dfb9` |
| RF-29 — Registro de histórico não pode ser alterado nem apagado | API ou banco aceitam alterar/apagar o histórico | — (rodada histórico auditável) | `HistoricoAuditavelReservaTest` :: *Registro de historico e imutavel*; `HistoricoReservaTransacaoIT` :: `bancoRecusaAlterarOuApagarHistorico`; `src/test/java/com/edualoca/HistoricoReservaApiTest.java` :: `historicoNaoPodeSerAlteradoNemApagado` | Unidade: red `1236d98` · green `e24323a`. IT: red `c421ae5` · green `ce0dfb9`. API: red `3ff6122` · green `bf674f5` |
| RF-27 / RF-02 — Histórico consultado só por Responsável e Administrador | Solicitante lê o histórico (inclusive da própria reserva) | — (rodada histórico auditável) | `HistoricoAuditavelReservaTest` :: *Consulta do historico so para Responsavel e Administrador*; `HistoricoReservaApiTest` :: `perfisAutorizadosConsultam`, `solicitanteRecebe403`, `semToken401` | Unidade: red `1236d98` · green `e24323a`. API: red `3ff6122` · green `bf674f5` |
| RF-14 — Cancelamento exige motivo | Cancelamento sem motivo aceito | BDD-R4-03 | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Cancelamento sem motivo é recusado* | red `4bf7bf4` · green `31da649` |
| RF-03 — Solicitante só cancela a própria reserva | Solicitante cancela reserva de terceiro | BDD-R4-04 | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Solicitante não cancela reserva de terceiro* | red `4bf7bf4` · green `31da649` |
| RF-30 — Reserva iniciada não pode ser apagada | `DELETE` remove reserva `EM_USO` | BDD-R4-05 | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Reserva iniciada não pode ser apagada* | red `4bf7bf4` (já passava pelo 405 do framework) · green `31da649` (403 pela regra de domínio). Reforço com reservas reais em todos os estados fora de `SOLICITADA` e histórico preservado: `HistoricoAuditavelReservaTest` :: `reservaIniciadaNaoEApagada` (red `1236d98` · green `e24323a`) |
| RF-01 / RF-02 — Rota exige token; perfil fora do escopo recebe 403 | Acesso anônimo ou Solicitante cadastrando manutenção | — (fora do documento BDD) | `src/test/java/com/edualoca/ApiSegurancaTest.java` :: `semTokenValido_recusaCom401`, `solicitanteNaoCadastraManutencao` | `6124305` |
| RNF-04 — Entrada inválida recusada com 400 | JSON malformado ou recurso inexistente processado | — (fora do documento BDD) | `src/test/java/com/edualoca/ApiSegurancaTest.java` :: `entradaInvalida_recusaCom400`, `buscaSemParametro_recusaCom400` | `6124305` |
| RF-01 / RF-02 / RF-12 / RF-19 — Cada operação responde o status da [matriz de autorização](seguranca/matriz-autorizacao.md) para cada perfil, com token obtido pelo login real | Perfil fora do escopo opera (ex.: Responsável cria reserva, Responsável de outro bloco aprova); login emite perfil errado | — (matriz §3, 57 células) | `src/test/java/com/edualoca/MatrizAutorizacaoApiTest.java` :: `celulaDaMatriz` | red `a8854c9` · green [DEF-003](defeitos/DEF-003-login-emite-sempre-solicitante.md) `9c71028`, [DEF-004](defeitos/DEF-004-alteracao-de-reserva-sem-rota.md) `dff55ce`, [DEF-006](defeitos/DEF-006-exclusao-de-solicitada-responde-405.md) `79692b6`, [DEF-009](defeitos/DEF-009-responsavel-e-administrador-criam-reserva.md) `366e69f`, [DEF-010](defeitos/DEF-010-responsavel-nao-competente-decide-reserva.md) `936860b` |
| RF-03 / RF-13 / RF-14 — Solicitante não altera nem cancela reserva alheia pelo id; 403 igual para id inexistente | Solicitante altera/cancela reserva de outro; resposta revela quais ids existem | — (matriz §2, P-1) | `src/test/java/com/edualoca/MatrizAutorizacaoApiTest.java` :: `Posse.alterarReservaAlheia`, `Posse.cancelarReservaAlheia`, `Posse.idAlheioEIdInexistenteSaoIndistinguiveis` | red `a8854c9` · green [DEF-004](defeitos/DEF-004-alteracao-de-reserva-sem-rota.md) `dff55ce`, [DEF-005](defeitos/DEF-005-cancelamento-revela-id-inexistente.md) `0ad6871` |
| RNF-04 / RF-38 — Término antes do início, capacidade negativa e campo ausente recusados com 400 e mensagem que nomeia o campo | Entrada fora do domínio aceita; mensagem genérica sem dizer o que corrigir | — | `src/test/java/com/edualoca/MatrizAutorizacaoApiTest.java` :: `entradaInvalida` (19 casos) | red `a8854c9` · green [DEF-007](defeitos/DEF-007-capacidade-minima-negativa-aceita.md) `3703276`, [DEF-008](defeitos/DEF-008-parametro-ausente-sem-nome-do-campo.md) `80f810d` |
| RNF-06 / RF-38 — Toda resposta de erro em ProblemDetail, sem stack trace, SQL ou nome de classe interna | Erro expõe detalhe interno ou sai fora do padrão | — | `src/test/java/com/edualoca/MatrizAutorizacaoApiTest.java` :: `celulaDeErroEmProblemDetail` (35 células), `entradaInvalida`, `Posse` | red `a8854c9` · green [DEF-002](defeitos/DEF-002-erros-fora-do-padrao-problemdetail.md) `7bad178` |

**Cenários do documento ainda sem linha executável:** BDD-R1-09 (P-02), BDD-R2-05 (P-01),
BDD-R3-07 (P-04), BDD-R4-06 (P-06) e as linhas de BDD-R4-01/R4-02 com iniciar uso, concluir e
não comparecimento (P-05, P-07, P-08). Todos aguardam decisão do PRD.

## Pendências de cobertura (RNF-11)

Fechadas na seção "Correções do relatório de qualidade (2026-11-12)", abaixo. Cada nova entrega de feature continua
devendo adicionar aqui o requisito, o risco que o teste mitiga, os testes correspondentes e a forma de
execução/evidência.

## Auditoria por mutação manual (2026-11-11, commit-base `ee540c5`)

Detalhes, comandos e resultados: [testes/mutacao-manual.md](testes/mutacao-manual.md). Foram 33 quebras de regra no
código de produção, uma por vez, cada uma desfeita com `git restore`. Os testes ligados detectaram 30 delas. As 3
lacunas (D3, A2, U8) ganharam os testes abaixo, verdes no CI (run [43125582350](https://github.com/DaniloDamasio/EduAloca/actions/runs/43125582350), commit `d69901d`, PR #12).

| Requisito | Risco | Teste (caminho :: nome) | Evidência / Execução |
|---|---|---|---|
| RF-12 — Material restrito também faz a reserva nascer `SOLICITADA` | Material restrito tratado como livre: reserva nasce `APROVADA` sem avaliação (mutante A2 sobrevivia à suíte inteira) | `src/test/java/com/edualoca/AprovacaoRecursosRestritosTest.java` :: `rf12T2_materialRestrito_reservaNasceSolicitada` | Local: verde no código original; vermelho com A2 (3/3). CI: verde no run 43125582350 (`d69901d`) |
| RF-17 — Exatamente uma reserva no modo em memória (padrão da aplicação) | Sem o lock do serviço, duas criações passam juntas pela checagem e as duas gravam (mutante D3 sobrevivia a unitários e ITs) | `src/test/java/com/edualoca/ConcorrenciaReservaEmMemoriaTest.java` :: `criacoesSimultaneasNaChecagem_soUmaEAceita` | Local: verde no código original; vermelho com D3 (3/3, "aceitas expected 1 but was 2"). CI: verde no run 43125582350 (`d69901d`) |
| RF-28 — Toda escrita de cadastro (sala, professor, material, manutenção, inativação) gera exatamente um evento de auditoria | Escrita de cadastro sem rastro (mutante U8 sobrevivia à suíte inteira) | `src/test/java/com/edualoca/AuditoriaCadastroTest.java` :: `escritaDeCadastroGeraAuditoria` (7 casos) | Local: verde no código original; vermelho com U8 (3/3). CI: verde no run 43125582350 (`d69901d`) |

Correções de rastreabilidade apontadas pela mutação. São testes que já estavam verdes no CI `ee540c5` e que eram os
únicos a detectar a quebra, mas não estavam citados nas linhas acima:

- RF-15 / RF-16 (modo em memória): `src/test/java/com/edualoca/RegrasCriticasTest.java` :: `rc02T3`–`rc02T9`;
  `RejeicaoSobreposicaoAuditoriaTest` :: `alteracaoRecusadaPorSobreposicao_gravaRejeicao` (mutantes S2, S3, S4, S6).
- RF-19: `src/test/java/com/edualoca/AprovacaoRecursosRestritosTest.java` :: `rf19T3_solicitanteTentaAprovar_deveRetornar403`
  (único a detectar A3) e `rf19T4_aprovarReservaForaDeSolicitada_deveSerRejeitado` (único a detectar A5).
- RF-23: `RegrasCriticasTest` :: `rc04T5_manutencaoSobrepoeReservaEmUso_exigeConfirmacaoOuRecusa` (único a detectar M4).

No commit `ee540c5`, `AuditoriaTest` :: `rf27T1`, `rf28T1` e `rf28T2` passavam com qualquer código, porque
`AuditoriaService.consultarPorEntidade` inventava um evento quando não havia nenhum. Isso foi corrigido em
[DEF-011](defeitos/DEF-011-auditoria-inventa-evento-inexistente.md) (red `1826216`, green `fc5fe22`): os três testes
foram reescritos com ações reais, e cada um derruba um mutante de auditoria. Os três estão verdes no CI (run
43125582350, `d69901d`). Continua sem servir de evidência, embora esteja verde,
`AprovacaoRecursosRestritosTest` :: `rf18T1` (recebe 409 por "reserva inexistente" e não exercita a transição para
`EM_USO`).

**Requisitos críticos com evidência: 19 de 32 (59,4%) no commit `ee540c5`; 20 de 32 (62,5%) no commit `d69901d`**
(RF-28 passou a ter teste real verde no CI, e as partes de RF-12 e RF-17 que estavam descobertas foram fechadas).
Sem evidência: RF-04, RF-05, RF-06, RF-18, RF-24, RF-25, RNF-05, RNF-07, RNF-10, RNF-11, RNF-12, RNF-15 (RNF-10 e RNF-12 têm execução no CI, mas
não têm linha nesta matriz). A tabela completa está em [testes/mutacao-manual.md §4](testes/mutacao-manual.md).

## Correções do relatório de qualidade (2026-11-12)

Origem: [relatorio-qualidade.md](relatorio-qualidade.md) (12 críticos sem evidência em `a670c68`). Auditoria por
mutação: [testes/mutacao-manual.md §6](testes/mutacao-manual.md). Execução: `./mvnw -B verify` (Surefire/Failsafe), a
cada PR no CI. **CI destas linhas:** run [43238556274](https://github.com/DaniloDamasio/EduAloca/actions/runs/43238556274) (`eb4b3af`, PR #13): 429 unitários (3 pulados) e 14 ITs, 0 falhas; linhas 96,96%, ramos 87,10%.

| Requisito | Risco | Teste (caminho :: nome) | Commit / Evidência |
|---|---|---|---|
| RF-04 — CRUD de sala: criação, edição e inativação refletidas na consulta; campos obrigatórios validados | Sala editada sem refletir na busca; campo obrigatório vazio aceito; escrita sem auditoria | `src/test/java/com/edualoca/CrudRecursoTest.java` :: `SalaCrud.*`; `src/test/java/com/edualoca/CadastroConsultaTest.java` :: `rf04T1`–`rf04T6` | [DEF-015](defeitos/DEF-015-crud-de-recursos-incompleto.md) red `fd4d18c` · green `2c9b98c`; lacunas C1/C3 `7046cf0`; mutantes C1–C3, C8–C14, C24 mortos |
| RF-05 — CRUD de professor (nome, identificador, competências) | Professor duplicado, sem nome ou sem identificador; edição/inativação sem efeito na busca | `CrudRecursoTest` :: `ProfessorCrud.*`; `CadastroConsultaTest` :: `rf05T1`–`rf05T3` | DEF-015 `fd4d18c` · `2c9b98c`; C4, C5, C15–C19 mortos |
| RF-06 — CRUD de material (nome, tipo, quantidade, restrição) | Material sem tipo ou com quantidade ≤ 0; edição/inativação sem efeito na busca | `CrudRecursoTest` :: `MaterialCrud.*`; `CadastroConsultaTest` :: `rf06T1`–`rf06T3` | DEF-015 `fd4d18c` · `2c9b98c`; C6, C7, C20–C23 mortos |
| RF-11 — Busca exclui recurso em manutenção, para **qualquer** tipo | Professor ou material em manutenção oferecido na busca | `src/test/java/com/edualoca/BuscaRecursoEmManutencaoTest.java` (3 casos) | [DEF-012](defeitos/DEF-012-busca-oferece-professor-e-material-em-manutencao.md) red `e16a14f` · green `69d04af` |
| RF-18 — Só `APROVADA` vai para `EM_USO`; restrita `SOLICITADA` nunca pula a aprovação (P-07: início manual por Responsável competente ou Administrador) | Uso iniciado sem aprovação, por perfil indevido ou sem histórico/auditoria | `src/test/java/com/edualoca/InicioUsoReservaTest.java` (6 casos) | red `820e634` · green `a9fd840`; I1–I5 mortos. `rf18T1`, que não exercitava a regra, virou `rf19T5` (`e99efed`) |
| RF-24 — Retirada vincula reserva + item + autor + instante, só em `EM_USO`, ciclo por item | Item não registrado; material de fora da reserva retirado; retirada fora de `EM_USO` | `src/test/java/com/edualoca/RetiradaVinculoItemTest.java`; `RetiradaDevolucaoMaterialTest` :: `rf24T1`–`rf24T3`; `InicioUsoReservaTest` :: `fluxoReal_permiteRetiradaSoDepoisDoInicioDoUso` | [DEF-014](defeitos/DEF-014-retirada-nao-vincula-item.md) red `109aace` · green `e5c5859`; R1–R4, R8, R9, R11 mortos |
| RF-25 — Devolução encerra o ciclo aberto correspondente, com condição | Devolução repetida reescreve data/condição; devolução sem condição aceita | `RetiradaVinculoItemTest` :: `devolucaoDeCicloEncerrado_eRecusadaSemSobrescrever`; `RetiradaDevolucaoMaterialTest` :: `rf25T1`–`rf25T4` | DEF-014 `109aace` · `e5c5859`; R5–R7, R10 mortos |
| RNF-05 — Senha armazenada com hash com sal, nunca em claro | Vazamento do armazenamento expõe todas as senhas | `src/test/java/com/edualoca/ArmazenamentoSenhaTest.java` (4 casos) | [DEF-013](defeitos/DEF-013-senha-armazenada-em-texto-plano.md) red `3835d8b` · green `2926f0e`; P1–P3 mortos |
| RNF-07 — SonarCloud da `main` sem issue Critical/Blocker | Teste sem asserção passa por qualquer código; regex super-linear | Correção em `AutenticacaoAutorizacaoTest` (`rf02T1`, `rf03T1`) e nos dois ITs de concorrência | `11ba3b3`. SonarCloud do PR #13: quality gate OK, 0 issues novos, 0 hotspots. A baixa dos 2 BLOCKER só aparece na análise da `main` depois do merge |
| RNF-10 — JaCoCo ≥ 80% linhas e ≥ 70% ramos | Cobertura cai sem ninguém notar | Gate `jacoco:check` (regra `BUNDLE`) no `pom.xml`, na fase `verify` | Run 43126611038 (`a670c68`): 96,53% linhas e 87,04% ramos ([relatório](relatorio-qualidade.md), A-03); CI destas correções: 96,96% linhas e 87,10% ramos (run 43238556274) |
| RNF-12 — CI com build, testes e análise em todo PR, bloqueando merge | Merge sem CI verde | `.github/workflows/ci.yml`; proteção da `main` (check obrigatório "Build e Testes", `strict`, `enforce_admins`) | PRs #4–#12 com CI verde; #1–#3 anteriores ao workflow ([relatório](relatorio-qualidade.md), A-09) |
| RNF-15 — Camadas separadas (apresentação, domínio, persistência, integração) | Domínio acoplado a framework web/JDBC ou à API | `src/test/java/com/edualoca/ArquiteturaCamadasTest.java` (4 regras) | red `d0f8ed4` · green `cbe3f53` (adaptador HTTP de notificação movido para `com.edualoca.integracao`); Q1 morto |
| RNF-11 — 100% dos críticos com teste e evidência | RTM incompleta | Esta seção, mais as anteriores | Ver contagem abaixo |

**Contagem:** com as linhas acima, os 32 requisitos críticos têm linha na RTM com risco, teste localizável e
mutação. Pelo critério de [mutacao-manual.md §4](testes/mutacao-manual.md) (teste verde no CI), **31 de 32 estão confirmados**
no run 43238556274. O 32º, RNF-07, depende da análise do SonarCloud da `main` depois do merge do PR #13 mostrar
0 issues Blocker/Critical. O PR não introduz nenhum issue, e os dois BLOCKER estão corrigidos no código.

## Relatório de utilização (`GET /relatorios/utilizacao`)

Testes de API em caixa-preta: a massa (4 recursos, 5 reservas, 1 cancelamento, 3 tentativas recusadas) é criada
pela própria API com relógio do servidor fixo (`@TestBean Clock`), e os valores esperados estão calculados à mão no
comentário de cada teste. Mutantes "cancelada conta como uso" e "sem recorte na borda" derrubam 4 e 3 testes,
respectivamente. Documentação: [README.md › API — Relatório de utilização](../README.md#api--relatório-de-utilização-rf-34-rf-35-rf-36).
Execução: `./mvnw -B verify` (Surefire), a cada PR no CI.

| Requisito | Risco | Teste (caminho :: nome) | Commit |
|---|---|---|---|
| RF-34 — Horas reservadas por recurso e carga horária por professor no período | Total errado por contar reserva fora do período, reserva inteira quando só parte cai no período, ou professor misturado aos recursos | `src/test/java/com/edualoca/RelatorioUtilizacaoApiTest.java` :: `relatorioDoMes`, `periodoDeUmAnoNoLimite` | red `09577bd` · green `faba0a3` |
| RF-34 — Reserva que cruza a borda conta só a parte de dentro | Horas de fora do período somadas ao relatório | `src/test/java/com/edualoca/RelatorioUtilizacaoApiTest.java` :: `reservaQueCruzaABorda` (corta início e fim), `relatorioDoMes` (R2 e R3) | red `09577bd` · green `faba0a3` |
| RF-34 — Reserva cancelada não conta como uso | Cancelamento continua inflando uso da sala e carga do professor | `src/test/java/com/edualoca/RelatorioUtilizacaoApiTest.java` :: `reservaCanceladaNaoConta`, `relatorioDoMes` (Prof. Bruno ausente, Lab 2 = 1,5 h) | red `09577bd` · green `faba0a3` |
| RF-34 — Período vazio | Erro ou lixo quando não há dados | `src/test/java/com/edualoca/RelatorioUtilizacaoApiTest.java` :: `periodoVazio` | red `09577bd` · green `faba0a3` |
| RF-34 / RNF-04 — Período inválido recusado com 400 | Período ausente, mal formatado, invertido ou ilimitado processado (custo/erro 500) | `src/test/java/com/edualoca/RelatorioUtilizacaoApiTest.java` :: `periodoInvalido400` (6 casos) | red `09577bd` · green `faba0a3` |
| RF-35 — Conflitos evitados = tentativas recusadas por sobreposição no período | Contagem sempre 0 (nada gravava `REJEICAO_SOBREPOSICAO`) ou contando tentativas de fora do período | `src/test/java/com/edualoca/RelatorioUtilizacaoApiTest.java` :: `relatorioDoMes` (C1 fora, C2 e C3 dentro), `periodoDeUmAnoNoLimite` | red `09577bd` · green `faba0a3` |
| RF-28 / RF-35 — Recusa por sobreposição grava `REJEICAO_SOBREPOSICAO` (uma por tentativa, autor e instante do `Clock`) | Recusa sem rastro; recusa pela constraint do banco ou na alteração não contada; recusa por manutenção contada como conflito | `src/test/java/com/edualoca/RejeicaoSobreposicaoAuditoriaTest.java` :: `criacaoRecusadaPorSobreposicao_gravaRejeicao`, `recusaDaConstraintNoBanco_gravaRejeicao`, `alteracaoRecusadaPorSobreposicao_gravaRejeicao`, `recusaPorManutencao_naoGravaRejeicao`, `semSobreposicao_naoGravaRejeicao` | red `09577bd` · green `faba0a3` |
| RF-36 / RF-01 — Só Responsável e Administrador; 401 sem token; 403 para Solicitante | Solicitante ou anônimo lê dados de uso | `src/test/java/com/edualoca/RelatorioUtilizacaoApiTest.java` :: `relatorioDoMes` (RESPONSAVEL e ADMINISTRADOR → 200), `semToken401`, `tokenDesconhecido401`, `solicitante403`, `solicitanteComParametroInvalido403` | red `09577bd` · green `faba0a3` |

## Concorrência de reservas (PostgreSQL real via Testcontainers)

| Requisito | Risco | Teste | Evidência / Execução |
|---|---|---|---|
| RF-17 / RNF-08 — Sob concorrência, exatamente uma reserva aceita para o mesmo recurso/intervalo | Duas reservas aceitas para a mesma sala ou o mesmo professor no mesmo horário. | `ConcorrenciaReservaIT` (20 requisições simultâneas, 1 instância); `ConcorrenciaReservaMultiInstanciaIT` (20 requisições divididas entre 2 JVMs). Exigem 1×201, 19×409 e 1 linha no banco, conferida por SQL. | 5 execuções seguidas verdes; [DEF-001](defeitos/DEF-001-dupla-reserva-entre-instancias.md) (falha antes da correção e reteste); [ADR-009](adr/ADR-009-exclusao-gist-tstzrange.md). |
| RF-15 / RF-16 — Recusar sobreposição com reserva ativa do mesmo recurso (sala ou professor) | Sobreposição gravada por caminho que não passe pela checagem do serviço. | `ReservaRepositorioJdbcIT` (constraint `ex_reserva_recurso_sem_sobreposicao`: sobreposição → 409, professor, intervalos encostados permitidos, cancelada libera, reativação recusada). | `./mvnw -B verify` (Failsafe), executado no CI. |

## Desempenho sob carga (JMeter)

Plano: [performance/rnf.md](performance/rnf.md) (requisitos propostos, modelo de carga e regras de
execução) e `perf/reservas.jmx`. Execução: `jmeter -n -t perf/reservas.jmx -l
perf/resultados/rodada-01.jtl -e -o perf/relatorio-01`, **somente local e manual** (não roda no CI).
Os números valem só para a máquina local descrita no resultado e não dizem nada sobre produção.

| Requisito | Risco | Plano | Resultado | Situação |
|---|---|---|---|---|
| RNF-01 (oficial: p95 ≤ 2 s) / RNF-01-P (proposta: p95 ≤ 300 ms, erro < 1 %) | Busca de disponibilidade lenta com 50 usuários. | [rnf.md §2–3](performance/rnf.md): `GET /salas/disponiveis`, 50 usuários, 5 min estáveis | [resultado-01.md](performance/resultado-01.md): p95 2 ms, 0 erros em 7.938 | Atende na rodada 01, com ressalvas* |
| RNF-02 (oficial: p95 ≤ 3 s) / RNF-02-P (proposta: p95 ≤ 500 ms com 201 + 409) | Criação lenta sob disputa pelo mesmo recurso. | [rnf.md §2–3](performance/rnf.md): `POST /reservas`, sala única, ~10 tentativas por janela | [resultado-01.md](performance/resultado-01.md): p95 8 ms (201: 11 ms; 409: 6 ms), 0 erros reais em 1.987 | Atende na rodada 01, com ressalvas* |
| RNF-03 / RNF-03-P: ≥ 100 criações/min, 0 × 5xx | Condição de corrida que gera 500 no pico. | [rnf.md §2](performance/rnf.md): asserções "somente 201 ou 409" e "nunca 5xx" | [resultado-01.md](performance/resultado-01.md): 397,4/min, 0 × 5xx | Atende na rodada 01, com ressalvas* |
| RF-17 sob carga: no máximo uma reserva aceita por recurso e intervalo | Dupla reserva sob carga sustentada, e não só no teste de 20 requisições. | [rnf.md §3](performance/rnf.md): disputa por janela, asserção "Sem dupla reserva" | [resultado-01.md §3.1](performance/resultado-01.md): 217 janelas, cada uma com 1 × 201; no banco, 217 linhas, máx. 1 por (recurso, intervalo) e 0 pares sobrepostos | Atende na rodada 01 |

\* Ressalvas (detalhes em [resultado-01.md §4](performance/resultado-01.md)): (1) jar gerado a
partir de `0cf9518` **mais alterações não commitadas**, então a evidência não é reproduzível pelo
git até a rodada 02; (2) as metas RNF-0x-P ainda são **proposta** não validada pelo grupo; (3) uma
única rodada, com gerador, aplicação e banco na mesma máquina.
