# RTM — Matriz de Rastreabilidade de Requisitos

> **Status: cobertura inicial.** Este arquivo começa com os requisitos efetivamente
> cobertos pela entrega do adapter HTTP de notificação externa (WireMock). Ele **não**
> representa ainda 100% dos requisitos críticos listados em
> [REQUISITOS.md](REQUISITOS/REQUISITOS.md) — RNF-11 exige essa cobertura completa, o que
> é um trabalho incremental, não esgotado nesta entrega. Novas linhas devem ser
> adicionadas à medida que cada requisito crítico ganha teste e evidência formal.

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
| RF-14 — Cancelamento exige motivo | Cancelamento sem motivo aceito | BDD-R4-03 | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Cancelamento sem motivo é recusado* | red `4bf7bf4` · green `31da649` |
| RF-03 — Solicitante só cancela a própria reserva | Solicitante cancela reserva de terceiro | BDD-R4-04 | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Solicitante não cancela reserva de terceiro* | red `4bf7bf4` · green `31da649` |
| RF-30 — Reserva iniciada não pode ser apagada | `DELETE` remove reserva `EM_USO` | BDD-R4-05 | `src/test/resources/features/aprovacao-bloqueio.feature` :: *Reserva iniciada não pode ser apagada* | red `4bf7bf4` (já passava pelo 405 do framework) · green `31da649` (403 pela regra de domínio) |
| RF-01 / RF-02 — Rota exige token; perfil fora do escopo recebe 403 | Acesso anônimo ou Solicitante cadastrando manutenção | — (fora do documento BDD) | `src/test/java/com/edualoca/ApiSegurancaTest.java` :: `semTokenValido_recusaCom401`, `solicitanteNaoCadastraManutencao` | `6124305` |
| RNF-04 — Entrada inválida recusada com 400 | JSON malformado ou recurso inexistente processado | — (fora do documento BDD) | `src/test/java/com/edualoca/ApiSegurancaTest.java` :: `entradaInvalida_recusaCom400`, `buscaSemParametro_recusaCom400` | `6124305` |

**Cenários do documento ainda sem linha executável:** BDD-R1-09 (P-02), BDD-R2-05 (P-01),
BDD-R3-07 (P-04), BDD-R4-06 (P-06) e as linhas de BDD-R4-01/R4-02 com iniciar uso, concluir e
não comparecimento (P-05, P-07, P-08). Todos aguardam decisão do PRD.

## Pendências de cobertura (RNF-11)

Os demais RFs/RNFs críticos listados em [REQUISITOS.md](REQUISITOS/REQUISITOS.md) ainda
não têm linha nesta matriz. Cada nova entrega de feature deve adicionar aqui o
requisito, o risco que o teste mitiga, o(s) teste(s) correspondente(s) e a forma de
execução/evidência, até atingir 100% dos requisitos marcados "Crítico".

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
